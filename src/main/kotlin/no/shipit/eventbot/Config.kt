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
    val postDay: DayOfWeek,
    val postTime: LocalTime,
) {
    companion object {
        fun load(): Config {
            val dotenv = readDotenv(File(".env"))
            fun get(key: String): String? =
                System.getenv(key)?.takeIf { it.isNotBlank() } ?: dotenv[key]

            fun require(key: String): String = get(key)
                ?: error("Mangler $key. Sett den i .env eller som miljøvariabel (se .env.example).")

            return Config(
                discordToken = require("DISCORD_TOKEN"),
                channelId = require("DISCORD_CHANNEL_ID"),
                postDay = DayOfWeek.valueOf(get("POST_DAY") ?: "MONDAY"),
                postTime = LocalTime.parse(get("POST_TIME") ?: "09:00"),
            )
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
