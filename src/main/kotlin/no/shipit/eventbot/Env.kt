package no.shipit.eventbot

import java.io.File

/**
 * Oppslag av innstillinger: miljøvariabler først, så en lokal `.env`, så det er
 * lett å kjøre fra IntelliJ. `.env` er git-ignorert.
 *
 * Alt som leser innstillinger skal gå gjennom denne — bruker du
 * `System.getenv` direkte, virker ikke `.env`, og da stemmer ikke lenger README.
 */
object Env {

    private val dotenv: Map<String, String> by lazy { readDotenv(File(".env")) }

    operator fun get(key: String): String? =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: dotenv[key]

    fun require(key: String): String = get(key)
        ?: error("Mangler $key. Sett den i .env eller som miljøvariabel (se README).")

    fun flag(key: String, default: Boolean = false): Boolean =
        get(key)?.trim()?.lowercase()?.let { it == "true" || it == "1" || it == "ja" } ?: default

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
