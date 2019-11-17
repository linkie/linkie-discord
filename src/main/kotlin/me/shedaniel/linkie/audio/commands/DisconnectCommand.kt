package me.shedaniel.linkie.audio.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.gateway.json.GatewayPayload
import discord4j.gateway.json.VoiceStateUpdate
import me.shedaniel.linkie.CommandBase
import me.shedaniel.linkie.CommandCategory
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.api
import me.shedaniel.linkie.audio.LinkieMusic
import java.awt.Color

object DisconnectCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isNotEmpty())
            throw InvalidUsageException("+$cmd <link>")
        val guildId = event.guildId.orElse(null)
        if (guildId?.asLong() == 591645350016712709 || guildId?.asLong() == 432055962233470986) {
            val member = event.member.get()
            val voiceState = member.voiceState.block()
                    ?: throw IllegalStateException("Failed to get the voice state of the user!")
            val voiceChannel = voiceState.channel.block()
                    ?: throw IllegalStateException("Failed to locate the voice channel used by the user!")
            val musicManager = LinkieMusic.getGuildAudioPlayer(member.guild.block()!!)
            musicManager.scheduler.clear()
            musicManager.player.stopTrack()
            val voiceStateUpdate = VoiceStateUpdate(guildId.asLong(), null, false, false)
            api.serviceMediator.gatewayClient.sender().next(GatewayPayload.voiceStateUpdate(voiceStateUpdate))
            LinkieMusic.musicManagers.remove(guildId)
            channel.createEmbed {
                it.setTitle("Disconnected!")
                it.setColor(Color.red)
            }.subscribe()
        }
    }

    override fun getCategory(): CommandCategory = CommandCategory.MUSIC
    override fun getName(): String = "Disconnect Music Command"
    override fun getDescription(): String = "Disconnect the music bot."
}