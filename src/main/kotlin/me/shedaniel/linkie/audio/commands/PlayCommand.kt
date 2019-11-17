package me.shedaniel.linkie.audio.commands

import discord4j.core.`object`.entity.*
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.CommandBase
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.audio.GuildMusicManager
import me.shedaniel.linkie.audio.LinkieMusic
import me.shedaniel.linkie.audio.OurAudioLoadResultHandler
import me.shedaniel.linkie.json
import org.apache.http.client.utils.URIBuilder
import java.net.URL

object PlayCommand : CommandBase {
    private fun joinBySearch(searchTerm: String, guild: Guild, voiceChannel: VoiceChannel, channel: MessageChannel, member: Member, musicManager: GuildMusicManager) {
        val builder = URIBuilder("https://www.googleapis.com/youtube/v3/search")
        builder.addParameter("part", "snippet")
        builder.addParameter("type", "video")
        builder.addParameter("maxResults", "25")
        builder.addParameter("key", System.getenv("GOOGLEAPI"))
        builder.addParameter("q", searchTerm)
        val apiText = URL(builder.toString()).readText()
        val obj = json.parseJson(apiText).jsonObject
        val items = obj.getArray("items")
        if (items.isEmpty()) {
            throw NullPointerException("Track not found!")
        } else {
            val item = items.first()
            val videoId = item.jsonObject.getObject("id").getPrimitive("videoId").content
            LinkieMusic.playerManager.loadItem("https://youtu.be/$videoId", OurAudioLoadResultHandler(guild, voiceChannel, channel, member, musicManager) {
                throw NullPointerException("Track not found!")
            }).get()
        }
    }

    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isEmpty())
            throw InvalidUsageException("+$cmd <link>")
        val guildId = event.guildId.orElse(null)?.asLong()
        if (guildId == 591645350016712709 || guildId == 432055962233470986) {
            val member = event.member.get()
            val voiceState = member.voiceState.block()
                    ?: throw IllegalStateException("Failed to get the voice state of the user!")
            val voiceChannel = voiceState.channel.block()
                    ?: throw IllegalStateException("Failed to locate the voice channel used by the user!")
            val musicManager = LinkieMusic.getGuildAudioPlayer(member.guild.block()!!)
            val url = args.joinToString(" ")
            if (url.contains(" ")) {
                joinBySearch(url, event.guild.block()!!, voiceChannel, channel, member, musicManager)
            } else
                try {
                    LinkieMusic.playerManager.loadItem(url, OurAudioLoadResultHandler(event.guild.block()!!, voiceChannel, channel, member, musicManager) {
                        joinBySearch(url, event.guild.block()!!, voiceChannel, channel, member, musicManager)
                    }).get()
                } catch (t: Throwable) {
                    joinBySearch(url, event.guild.block()!!, voiceChannel, channel, member, musicManager)
                }
        }
    }
}