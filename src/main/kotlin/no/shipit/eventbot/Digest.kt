package no.shipit.eventbot

import no.shipit.eventbot.model.Event
import no.shipit.eventbot.model.OSLO
import no.shipit.eventbot.model.Week
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NO = Locale.of("no", "NO")
private val DAY = DateTimeFormatter.ofPattern("EEEE d. MMMM", NO)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", NO)

/** Discord: 4096 tegn per embed-beskrivelse, 6000 til sammen per melding. */
const val EMBED_DESCRIPTION_LIMIT = 4096
const val EMBED_TOTAL_LIMIT = 6000

/** Luft mellom dagene. Embeds beholder linjeskift som de står. */
private const val DAY_GAP = "\n\n\n"

/**
 * Discord legger tittel og beskrivelse tett inntil hverandre, og trimmer bort
 * blanke linjer i starten av beskrivelsen. ​ er ZERO WIDTH SPACE: usynlig,
 * men gjør at linja ikke teller som tom og derfor overlever trimmingen.
 */
private const val TITLE_GAP = "​\n\n"

/**
 * Uka som én melding. [chunks] blir én embed hver, alle på samme melding, slik
 * at hele oppsummeringen henger sammen i klienten.
 *
 * Grunnen til embeds og ikke ren tekst: en vanlig melding tar maks 2000 tegn,
 * og en travel uke er over 3000. Embeds gir også automatisk bort
 * link-previewene, siden Discord bare lager dem fra URL-er i meldingsteksten.
 */
data class WeeklyDigest(
    val title: String,
    val chunks: List<String>,
    /** Kildene dataene faktisk kom fra, til krediteringen nederst. */
    val sources: List<String>,
)

fun digest(week: Week, events: List<Event>): WeeklyDigest {
    //   er EM SPACE. Discord slår sammen vanlige mellomrom i titler,
    // så ekstra luft må komme fra et tegn som ikke kollapser.
    val title = "🗓️ Uke ${week.week} - Hva skjer i Oslo (${events.size} events)"


    val sources = events.map { it.source }.distinct().sorted()

    if (events.isEmpty()) {
        return WeeklyDigest(title, listOf("Ingen events funnet denne uka."), sources)
    }

    // Dager uten events hoppes over — groupBy gir bare dagene som faktisk har noe.
    val byDay: Map<LocalDate, List<Event>> = events
        .sortedBy { it.start }
        .groupBy { it.start.atZone(OSLO).toLocalDate() }

    val blocks = byDay.map { (day, dayEvents) ->
        val name = day.format(DAY).replaceFirstChar { it.uppercase() }
        "**$name**\n" + dayEvents.joinToString("\n", transform = ::line)
    }

    val chunks = chunk(blocks).toMutableList()
    chunks[0] = TITLE_GAP + chunks[0]

    return WeeklyDigest(title, chunks, sources)
}

private fun line(event: Event): String = buildString {
    val time = event.start.atZone(OSLO).format(TIME)
    append(if (event.highlighted) "✨ " else "• ")
    append("`$time` ")
    append(if (event.url != null) "[${event.title}](${event.url})" else event.title)
    event.venue?.let { append(" @ $it") }
    if (event.lineup.isNotEmpty()) append(" — ${event.lineup.joinToString(", ")}")
}

/**
 * Fyller så få embeds som mulig uten å dele en dag i to, og stopper før
 * [EMBED_TOTAL_LIMIT] — Discord avviser hele meldingen hvis summen sprenger.
 */
private fun chunk(blocks: List<String>): List<String> {
    val overflow = "_… flere events enn det er plass til. Se osloomvendt.no._"
    val chunks = mutableListOf<String>()
    var current = StringBuilder()
    var total = 0

    for (block in blocks) {
        val needed = DAY_GAP.length + block.length
        if (total + needed > EMBED_TOTAL_LIMIT - overflow.length - DAY_GAP.length) {
            current.append(DAY_GAP).append(overflow)
            break
        }
        if (current.length + needed > EMBED_DESCRIPTION_LIMIT) {
            chunks += current.toString()
            current = StringBuilder(block)
        } else {
            if (current.isNotEmpty()) current.append(DAY_GAP)
            current.append(block)
        }
        total += needed
    }

    chunks += current.toString()
    return chunks
}
