package no.shipit.eventbot.model

import java.time.Instant

/**
 * Kildeuavhengig event. Nye datakilder mapper til denne, slik at
 * oppsummeringen slipper å vite hvor dataene kom fra.
 */
data class Event(
    val title: String,
    val start: Instant,
    val end: Instant? = null,
    val venue: String? = null,
    val lineup: List<String> = emptyList(),
    val url: String? = null,
    val highlighted: Boolean = false,
    /** Navnet på kilden, f.eks. "Oslo Omvendt". */
    val source: String,
    /** Kildens hjemmeside, brukt til krediteringen nederst i meldingen. */
    val sourceUrl: String? = null,
)
