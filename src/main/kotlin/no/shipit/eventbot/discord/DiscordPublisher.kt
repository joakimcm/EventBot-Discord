package no.shipit.eventbot.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import no.shipit.eventbot.WeeklyDigest
import org.slf4j.LoggerFactory

/** Oslo Omvendts merkefarge, hentet fra stylesheetet deres. */
private const val ACCENT = 0x9B6CFF

private val log = LoggerFactory.getLogger(DiscordPublisher::class.java)

/** Tynn wrapper rundt JDA: kobler opp én gang, poster samme melding til hver kanal. */
class DiscordPublisher(
    token: String,
    private val channelIds: List<String>,
) : AutoCloseable {

    private val jda: JDA = JDABuilder.createLight(token).build().awaitReady()

    /**
     * Poster til alle kanalene. En kanal som feiler — boten er ikke invitert,
     * mangler rettigheter, ID-en er feil — logges og hoppes over, slik at de
     * andre kanalene får posten sin. Kaster bare hvis ingen av dem gikk.
     */
    fun post(digest: WeeklyDigest) {
        val embeds = buildEmbeds(digest)

        val sent = channelIds.count { id ->
            runCatching {
                val channel = jda.getTextChannelById(id)
                    ?: error("fant ikke kanalen — er boten invitert, og ser den kanalen?")
                channel.sendMessageEmbeds(embeds).complete()
            }.onFailure { log.error("Kanal {}: {}", id, it.message) }.isSuccess
        }

        check(sent > 0) { "Ingen av de ${channelIds.size} kanalene tok imot meldingen." }
        log.info("Postet til {} av {} kanal(er)", sent, channelIds.size)
    }

    // Alle embeds på samme melding, så hele uka henger sammen i klienten.
    // Tittel bare på den første, kreditering bare på den siste.
    private fun buildEmbeds(digest: WeeklyDigest) = digest.chunks.mapIndexed { index, text ->
        EmbedBuilder()
            .setDescription(text)
            .setColor(ACCENT)
            .apply {
                if (index == 0) setTitle(digest.title)
                if (index == digest.chunks.lastIndex && digest.sources.isNotEmpty()) {
                    setFooter("Kilde: ${digest.sources.joinToString(", ")}")
                }
            }
            .build()
    }

    override fun close() = jda.shutdown()
}
