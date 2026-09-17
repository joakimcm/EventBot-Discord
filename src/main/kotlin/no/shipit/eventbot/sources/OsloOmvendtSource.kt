package no.shipit.eventbot.sources

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.shipit.eventbot.model.Event
import no.shipit.eventbot.model.Week
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Åpent API, ingen nøkkel:
 *   GET https://www.osloomvendt.no/api/events?year=<N>&week=<N>
 * Dokumentert på https://www.osloomvendt.no/faq
 */
class OsloOmvendtSource(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val mapper: ObjectMapper = ObjectMapper(),
) : EventSource {

    override val name = "Oslo Omvendt"

    override fun fetch(week: Week): List<Event> {
        val uri = URI.create("$BASE/api/events?year=${week.year}&week=${week.week}")
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) {
            "$name svarte ${response.statusCode()} på $uri"
        }

        return parse(response.body())
    }

    /** Skilt fra [fetch] så mappingen kan testes mot lagret JSON. */
    fun parse(body: String): List<Event> =
        mapper.readTree(body)
            .path("events")
            .map(::toEvent)
            .sortedBy { it.start }

    private fun toEvent(node: JsonNode) = Event(
        title = node.path("name").asText().trim(),
        start = Instant.parse(node.path("startDate").asText()),
        end = node.path("endDate").textValue()?.let(Instant::parse),
        venue = node.path("location").path("name").textValue()?.trim(),
        lineup = node.path("contributors").mapNotNull { it.path("person").path("name").textValue() },
        url = node.path("urls").firstOrNull()?.path("url")?.textValue(),
        highlighted = node.path("highlight").asBoolean(false),
        source = name,
    )

    private companion object {
        const val BASE = "https://www.osloomvendt.no"
        const val USER_AGENT = "ship-it-eventbot/0.1 (Discord weekly digest)"
    }
}
