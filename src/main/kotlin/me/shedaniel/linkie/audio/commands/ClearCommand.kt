package me.shedaniel.linkie.audio.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.*
import me.shedaniel.linkie.audio.LinkieMusic
import java.awt.Color

object ClearCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isNotEmpty())
            throw InvalidUsageException("!$cmd <link>")
        val guildId = event.guildId.orElse(null)
        if (guildId?.asLong() == 591645350016712709 || guildId?.asLong() == 432055962233470986) {
            val member = event.member.get()
            val voiceState = member.voiceState.block()
                    ?: throw IllegalStateException("Failed to get the voice state of the user!")
            val voiceChannel = voiceState.channel.block()
                    ?: throw IllegalStateException("Failed to locate the voice channel used by the user!")
            val musicManager = LinkieMusic.getGuildAudioPlayer(member.guild.block()!!)
            musicManager.scheduler.clear()
            channel.createEmbed {
                it.apply {
                    setTitle("Cleared Queue!")
                    setColor(Color.green)
                    setFooter("Requested by ${member.discriminatedName}", member.avatarUrl)
                    setTimestampToNow()
                }
            }.subscribe()
        }
    }

    override fun getCategory(): CommandCategory = CommandCategory.MUSIC
    override fun getName(): String = "Clear Queue Command"
    override fun getDescription(): String = "Clears the music queue."
}