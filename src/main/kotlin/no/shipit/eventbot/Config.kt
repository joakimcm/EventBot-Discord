package no.shipit.eventbot

import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Leser fra miljøvariabler, med en lokal `.env` som fallback så det er
 * lett å kjøre fra IntelliJ. `.env` er git-ignorert.
 */
data class Config(
    val discordToken: String,
    val channelId: String,
    /** Ukedagene det postes på, f.eks. `POST_DAYS=MONDAY,THURSDAY`. */
    val postDays: Set<DayOfWeek>,
    val postTime: LocalTime,
) {
    companion object {
        fun load(): Config {
            val dotenv = readDotenv(File(".env"))
            fun get(key: String): String? =
                System.getenv(key)?.takeIf { it.isNotBlank() } ?: dotenv[key]

            fun require(key: String): String = get(key)
                ?: error("Mangler $key. Sett den i .env eller som miljøvariabel (se README).")

            // POST_DAY beholdes som fallback for eldre oppsett med én dag.
            val days = get("POST_DAYS") ?: get("POST_DAY") ?: "MONDAY,THURSDAY"

            return Config(
                discordToken = require("DISCORD_TOKEN"),
                channelId = require("DISCORD_CHANNEL_ID"),
                postDays = parseDays(days),
                postTime = LocalTime.parse(get("POST_TIME") ?: "12:00"),
            )
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

        private fun readDotenv(file: File): Map<String, String> {
            if (!file.exists()) return emptyMap()
            return file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                .associate { line ->
                    val (key, value) = line.split('=', limit = 2)
                    key.trim() to value.trim().trim('"', '\'')
                }
        }
    }
}
