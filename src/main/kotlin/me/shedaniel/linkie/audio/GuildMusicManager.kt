package me.shedaniel.linkie.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager

class GuildMusicManager(manager: AudioPlayerManager) {
    internal val player: AudioPlayer = manager.createPlayer()
    internal val scheduler: TrackScheduler = TrackScheduler(player)
    internal var audioProvider: LavaPlayerAudioProvider = LavaPlayerAudioProvider(player)
}