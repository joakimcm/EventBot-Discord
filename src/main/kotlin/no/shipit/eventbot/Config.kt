package no.shipit.eventbot

import java.time.DayOfWeek
import java.time.LocalTime

/** Innstillingene boten trenger. Oppslag skjer gjennom [Env]. */
data class Config(
    val discordToken: String,
    /** Kanalene det postes i, f.eks. `DISCORD_CHANNEL_IDS=123,456`. */
    val channelIds: List<String>,
    /** Ukedagene det postes på, f.eks. `POST_DAYS=MONDAY,THURSDAY`. */
    val postDays: Set<DayOfWeek>,
    val postTime: LocalTime,
) {
    companion object {
        fun load(): Config {
            fun get(key: String) = Env[key]

            // Entallsformene beholdes som fallback for eldre oppsett.
            val days = get("POST_DAYS") ?: get("POST_DAY") ?: "MONDAY,THURSDAY"
            val channels = get("DISCORD_CHANNEL_IDS")
                ?: get("DISCORD_CHANNEL_ID")
                ?: error("Mangler DISCORD_CHANNEL_IDS. Sett den i .env eller som miljøvariabel (se README).")

            return Config(
                discordToken = Env.require("DISCORD_TOKEN"),
                channelIds = parseList(channels),
                postDays = parseDays(days),
                postTime = LocalTime.parse(get("POST_TIME") ?: "12:00"),
            )
        }

        private fun parseList(raw: String): List<String> {
            val values = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            require(values.isNotEmpty()) { "DISCORD_CHANNEL_IDS kan ikke være tom." }
            return values.distinct()
        }

        private fun parseDays(raw: String): Set<DayOfWeek> {
            val days = raw.split(',')
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .map {
                    runCatching { DayOfWeek.valueOf(it) }.getOrElse { _ ->
                        error("Ukjent ukedag '$it' i POST_DAYS. Bruk engelske navn, f.eks. MONDAY,THURSDAY.")
                    }
                }
            require(days.isNotEmpty()) { "POST_DAYS kan ikke være tom." }
            return days.toSortedSet()
        }

    }
}
