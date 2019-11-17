package me.shedaniel.linkie.audio

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.VoiceChannel
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.VoiceStateUpdateEvent
import me.shedaniel.linkie.api
import me.shedaniel.linkie.audio.commands.*
import me.shedaniel.linkie.commandApi


object LinkieMusic {

    val playerManager = DefaultAudioPlayerManager()
    internal val musicManagers: MutableMap<Snowflake, GuildMusicManager> = mutableMapOf()

    fun setupCommands() {
        api.eventDispatcher.on(VoiceStateUpdateEvent::class.java).subscribe {
            if (it.current.userId == api.selfId.orElse(null)) {
                val guild = it.current.guild.block()!!
                val inVoiceChannel = guild.voiceStates.any { state -> guild.client.selfId.map(state.userId::equals).orElse(false) }.block()!!
                if (!inVoiceChannel)
                    musicManagers.remove(it.current.guildId)
            }
        }
        commandApi.registerCommand(PlayCommand, "play", "p")
        commandApi.registerCommand(DisconnectCommand, "dc", "disconnect", "stop", "shutdown")
        commandApi.registerCommand(SkipCommand, "skip")
        commandApi.registerCommand(ClearCommand, "clear")
        commandApi.registerCommand(LoopCommand, "loop")
    }

    fun setupMusic() {
        playerManager.configuration.setFrameBufferFactory({ a, b, c -> NonAllocatingAudioFrameBuffer(a, b, c) })
        AudioSourceManagers.registerRemoteSources(playerManager)
        AudioSourceManagers.registerLocalSource(playerManager)
    }

    internal fun play(guild: Guild, voiceChannel: VoiceChannel, musicManager: GuildMusicManager, track: AudioTrack): Boolean {
        attachToVoiceChannel(guild, voiceChannel, musicManager.audioProvider)
        return musicManager.scheduler.queue(track)
    }

    @Synchronized
    internal fun getGuildAudioPlayer(guild: Guild): GuildMusicManager =
            musicManagers.getOrPut(guild.id, { GuildMusicManager(playerManager) })

    internal fun attachToVoiceChannel(guild: Guild, voiceChannel: VoiceChannel, provider: LavaPlayerAudioProvider) {
        val inVoiceChannel = guild.voiceStates.any { state -> guild.client.selfId.map(state.userId::equals).orElse(false) && state.channelId.map(voiceChannel.id::equals).orElse(false) }.block()!!

        if (!inVoiceChannel) {
            voiceChannel.join { spec -> spec.setProvider(provider) }.block()
        }
    }
}
