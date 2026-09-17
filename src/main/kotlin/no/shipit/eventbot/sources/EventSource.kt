package no.shipit.eventbot.sources

import no.shipit.eventbot.model.Event
import no.shipit.eventbot.model.Week

/**
 * Sømmen for flere datakilder. Legg til en ny kilde ved å implementere
 * dette og registrere den i [no.shipit.eventbot.Main].
 */
interface EventSource {
    val name: String

    /** Skal returnere events som starter i [week]. Kaster ved feil; kalleren logger og går videre. */
    fun fetch(week: Week): List<Event>
}
