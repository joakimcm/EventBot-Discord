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

        val delay = untilNextRun(config)
        log.info("Neste utsending om {} (kl. {} hver {})", human(delay), config.postTime, config.postDay)

        scheduler.scheduleAtFixedRate(
            { runCatching { publish(discord) }.onFailure { log.error("Utsending feilet", it) } },
            delay.toMinutes(),
            Duration.ofDays(7).toMinutes(),
            TimeUnit.MINUTES,
        )
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

private fun untilNextRun(config: Config): Duration {
    val now = ZonedDateTime.now(OSLO)
    var next = now.with(TemporalAdjusters.nextOrSame(config.postDay))
        .with(config.postTime)
    if (!next.isAfter(now)) next = next.plusWeeks(1)
    return Duration.between(now, next)
}

private fun human(d: Duration) = "${d.toDays()}d ${d.toHoursPart()}t ${d.toMinutesPart()}m"
