package no.shipit.eventbot.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import no.shipit.eventbot.WeeklyDigest

/** Oslo Omvendts merkefarge, hentet fra stylesheetet deres. */
private const val ACCENT = 0x9B6CFF

/** Tynn wrapper rundt JDA: kobler opp én gang, poster én melding til én kanal. */
class DiscordPublisher(token: String, private val channelId: String) : AutoCloseable {

    private val jda: JDA = JDABuilder.createLight(token).build().awaitReady()

    fun post(digest: WeeklyDigest) {
        val channel = jda.getTextChannelById(channelId)
            ?: error("Fant ikke kanal $channelId. Er boten invitert til serveren?")

        // Alle embeds på samme melding, så hele uka henger sammen i klienten.
        // Tittel bare på den første, kreditering bare på den siste.
        val last = digest.chunks.lastIndex
        val embeds = digest.chunks.mapIndexed { index, text ->
            EmbedBuilder()
                .setDescription(text)
                .setColor(ACCENT)
                .apply {
                    if (index == 0) setTitle(digest.title)
                    if (index == last && digest.sources.isNotEmpty()) {
                        setFooter("Kilde: ${digest.sources.joinToString(", ")}")
                    }
                }
                .build()
        }

        channel.sendMessageEmbeds(embeds).complete()
    }

    override fun close() = jda.shutdown()
}
