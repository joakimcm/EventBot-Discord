package no.shipit.eventbot

import no.shipit.eventbot.discord.DiscordPublisher
import no.shipit.eventbot.model.Event
import no.shipit.eventbot.model.OSLO
import no.shipit.eventbot.model.Week
import no.shipit.eventbot.sources.EventSource
import no.shipit.eventbot.sources.OsloOmvendtSource
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("eventbot")

/** Legg nye datakilder til her. */
private val sources: List<EventSource> = listOf(
    OsloOmvendtSource(),
)

/**
 * Bruk:
 *   (ingen flagg)  kjør videre og post hver uke til fast tid
 *   --now          post én gang og avslutt
 *   --dry-run      skriv oppsummeringen til terminalen, ingen Discord
 */
fun main(args: Array<String>) {
    val dryRun = "--dry-run" in args
    val once = "--now" in args

    if (dryRun) {
        val digest = buildDigest()
        println(digest.title)
        println(digest.chunks.joinToString("\n\n───── ny embed ─────\n\n"))
        return
    }

    val config = Config.load()
    DiscordPublisher(config.discordToken, config.channelId).use { discord ->
        if (once) {
            publish(discord)
            return
        }

        val scheduler = Executors.newSingleThreadScheduledExecutor()
        Runtime.getRuntime().addShutdownHook(Thread { scheduler.shutdown() })

        log.info(
            "Poster kl. {} på {}",
            config.postTime,
            config.postDays.joinToString(", ") { it.name.lowercase() },
        )

        // Én utsending av gangen, som planlegger neste selv. Et fast intervall
        // ville drevet av gårde over sommertid og ved avbrudd.
        fun scheduleNext() {
            val delay = untilNextRun(config)
            log.info("Neste utsending om {}", human(delay))
            scheduler.schedule(
                {
                    runCatching { publish(discord) }.onFailure { log.error("Utsending feilet", it) }
                    scheduleNext()
                },
                delay.toSeconds(),
                TimeUnit.SECONDS,
            )
        }
        scheduleNext()

        // Hold hovedtråden i live; scheduleren er en daemon-fri executor.
        Thread.currentThread().join()
    }
}

private fun publish(discord: DiscordPublisher) {
    val digest = buildDigest()
    discord.post(digest)
    log.info("Postet uke {} som {} embed(s)", Week.current().week, digest.chunks.size)
}

/** Henter fra alle kilder; en kilde som feiler stopper ikke resten. */
private fun buildDigest(week: Week = Week.current()): WeeklyDigest {
    val events: List<Event> = sources.flatMap { source ->
        runCatching { source.fetch(week) }
            .onFailure { log.warn("Kilde '{}' feilet, hopper over", source.name, it) }
            .getOrDefault(emptyList())
    }
    log.info("Hentet {} events for uke {} fra {} kilde(r)", events.size, week.week, sources.size)
    return digest(week, events)
}

/** Nærmeste av de konfigurerte dagene som ligger fram i tid. */
private fun untilNextRun(config: Config): Duration {
    val now = ZonedDateTime.now(OSLO)
    val next = config.postDays
        .map { day ->
            val candidate = now.with(TemporalAdjusters.nextOrSame(day)).with(config.postTime)
            if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
        }
        .min()
    return Duration.between(now, next)
}

private fun human(d: Duration) = "${d.toDays()}d ${d.toHoursPart()}t ${d.toMinutesPart()}m"
