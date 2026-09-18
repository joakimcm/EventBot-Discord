package no.shipit.eventbot.sources

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.shipit.eventbot.model.Event
import no.shipit.eventbot.model.OSLO
import no.shipit.eventbot.model.Week
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Oslo Omvendt tilbyr to endepunkter, og de er ikke enige om klokkeslett:
 *
 *  - `GET /api/events?year=&week=` (REST) forskyver events som er lagret med
 *    eksplisitt tidssone-offset to timer bakover. Verifisert 17.09.2026 mot
 *    arrangørenes egne sider: 6 av 27 events i uke 38 var feil.
 *  - `POST /api/mcp` (MCP) returnerer det samme som deres egne eventsider.
 *
 * Derfor brukes MCP som kilde. REST kalles kun for `highlight`-flagget, som
 * ikke finnes i MCP-svaret.
 */
class OsloOmvendtSource(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val mapper: ObjectMapper = ObjectMapper(),
) : EventSource {

    override val name = "Oslo Omvendt"

    override fun fetch(week: Week): List<Event> {
        val events = parse(post(MCP, mcpRequest(week)))

        // Mister vi highlight, er det bare stjernene som uteblir.
        val highlighted = runCatching { highlightedTitles(week) }.getOrDefault(emptySet())
        return events.map { if (it.title in highlighted) it.copy(highlighted = true) else it }
    }

    /** Skilt fra [fetch] så mappingen kan testes mot lagret JSON. */
    fun parse(body: String): List<Event> =
        mcpPayload(body)
            .path("events")
            .map(::toEvent)
            .sortedBy { it.start }

    private fun toEvent(node: JsonNode) = Event(
        title = node.path("name").asText().trim(),
        start = parseTimestamp(node.path("startDate").asText()),
        end = node.path("endDate").textValue()?.let(::parseTimestamp),
        venue = node.path("venue").path("name").textValue()?.trim(),
        lineup = node.path("artists").mapNotNull { it.path("name").textValue() },
        url = node.path("urls").firstOrNull()?.path("url")?.textValue(),
        source = name,
        sourceUrl = BASE,
    )

    /**
     * To formater i samme felt:
     *
     *  - `2026-09-18T21:00:00+02:00` — ekte offset, leses som det står.
     *  - `2026-09-15T21:00:00.000Z` — `Z`-en er dekorativ. Tallet er allerede
     *    lokal Oslo-tid, og det er slik Oslo Omvendt viser det selv.
     */
    private fun parseTimestamp(raw: String): Instant =
        if (raw.endsWith("Z")) {
            LocalDateTime.parse(raw.dropLast(1)).atZone(OSLO).toInstant()
        } else {
            OffsetDateTime.parse(raw).toInstant()
        }

    /** Pakker ut JSON-RPC-konvolutten: SSE-ramme → result.content[0].text → JSON. */
    private fun mcpPayload(body: String): JsonNode {
        val json = body.lineSequence()
            .firstOrNull { it.startsWith("data:") }
            ?.removePrefix("data:")
            ?.trim()
            ?: body
        val text = mapper.readTree(json).path("result").path("content").firstOrNull()
            ?.path("text")?.asText()
            ?: error("Uventet svar fra MCP-endepunktet")
        return mapper.readTree(text)
    }

    private fun mcpRequest(week: Week) = """
        {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_events",
        "arguments":{"year":${week.year},"week":${week.week}}}}
    """.trimIndent().replace("\n", "")

    private fun highlightedTitles(week: Week): Set<String> =
        mapper.readTree(get("$BASE/api/events?year=${week.year}&week=${week.week}"))
            .path("events")
            .filter { it.path("highlight").asBoolean(false) }
            .mapNotNull { it.path("name").textValue()?.trim() }
            .toSet()

    private fun post(uri: String, body: String): String = send(
        HttpRequest.newBuilder(URI.create(uri))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body)),
    )

    private fun get(uri: String): String = send(
        HttpRequest.newBuilder(URI.create(uri)).header("Accept", "application/json").GET(),
    )

    private fun send(builder: HttpRequest.Builder): String {
        val request = builder
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", USER_AGENT)
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) {
            "$name svarte ${response.statusCode()} på ${request.uri()}"
        }
        return response.body()
    }

    private companion object {
        const val BASE = "https://www.osloomvendt.no"
        const val MCP = "$BASE/api/mcp"
        const val USER_AGENT = "ship-it-eventbot/0.1 (Discord weekly digest)"
    }
}
