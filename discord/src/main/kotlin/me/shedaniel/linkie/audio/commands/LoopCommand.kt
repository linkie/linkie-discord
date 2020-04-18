package me.shedaniel.linkie.audio.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.gateway.json.GatewayPayload
import discord4j.gateway.json.VoiceStateUpdate
import me.shedaniel.linkie.*
import me.shedaniel.linkie.audio.LinkieMusic
import java.awt.Color

object LoopCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isNotEmpty())
            throw InvalidUsageException("!$cmd")
        val guildId = event.guildId.orElse(null)
        if (guildId?.asLong() == 591645350016712709 || guildId?.asLong() == 432055962233470986) {
            val member = event.member.get()
            val voiceState = member.voiceState.block()
                    ?: throw IllegalStateException("Failed to get the voice state of the user!")
            val voiceChannel = voiceState.channel.block()
                    ?: throw IllegalStateException("Failed to locate the voice channel used by the user!")
            val musicManager = LinkieMusic.getGuildAudioPlayer(member.guild.block()!!)
            LinkieMusic.attachToVoiceChannel(member.guild.block()!!, voiceChannel, musicManager.audioProvider)
            musicManager.scheduler.loop = musicManager.scheduler.loop.not()
            if (musicManager.scheduler.loop) {
                channel.createEmbed {
                    it.apply {
                        setTitle("Loop Enabled!")
                        setColor(Color.yellow)
                    }
                }.subscribe()
            } else {
                channel.createEmbed {
                    it.apply {
                        setTitle("Loop Disabled!")
                        setColor(Color.yellow)
                    }
                }.subscribe()
            }
        } else throw IllegalAccessException("Music commands are not available on this server.")
    }

    override fun getCategory(): CommandCategory = CommandCategory.MUSIC
    override fun getName(): String = "Loop Queue Command"
    override fun getDescription(): String = "Toggles looping in the queue."
}