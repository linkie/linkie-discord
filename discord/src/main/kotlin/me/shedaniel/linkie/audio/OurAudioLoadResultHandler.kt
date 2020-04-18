package me.shedaniel.linkie.audio

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.VoiceChannel
import me.shedaniel.linkie.addInlineField
import me.shedaniel.linkie.discriminatedName
import me.shedaniel.linkie.setTimestampToNow
import java.awt.Color
import java.time.Instant

class OurAudioLoadResultHandler(private val guild: Guild, private val voiceChannel: VoiceChannel, val channel: MessageChannel, private val member: Member, private val musicManager: GuildMusicManager, private inline val noMatch: () -> Unit) : AudioLoadResultHandler {
    override fun trackLoaded(track: AudioTrack) {
        track.userData = member.id.asLong()
        if (!LinkieMusic.play(guild, voiceChannel, musicManager, track)) {
            channel.createEmbed {
                it.apply {
                    setTitle("Added to queue!")
                    setColor(Color.green)
                    addInlineField("Channel", track.info.author)
                    addInlineField("Title", track.info.title)
                    setFooter("Requested by " + member.discriminatedName, member.avatarUrl)
                    setTimestampToNow()
                }
            }.subscribe()
        } else {
            channel.createEmbed {
                it.apply {
                    setTitle("Now Playing!")
                    setColor(Color.green)
                    addInlineField("Channel", track.info.author)
                    addInlineField("Title", track.info.title)
                    setFooter("Requested by " + member.discriminatedName, member.avatarUrl)
                    setTimestampToNow()
                }
            }.subscribe()
        }
    }

    override fun noMatches() {
        noMatch.invoke()
    }

    override fun playlistLoaded(playlist: AudioPlaylist) {
        val firstTrack: AudioTrack = playlist.selectedTrack ?: playlist.tracks[0]
        if (LinkieMusic.play(guild, voiceChannel, musicManager, firstTrack)) {
            channel.createEmbed {
                it.apply {
                    setTitle("Now Playing!")
                    setColor(Color.green)
                    addInlineField("Channel", firstTrack.info.author)
                    addInlineField("Title", firstTrack.info.title)
                    setFooter("Requested by " + member.discriminatedName, member.avatarUrl)
                    setTimestampToNow()
                }
            }.subscribe()
        } else {
            channel.createEmbed {
                it.apply {
                    setTitle("Added to queue!")
                    setColor(Color.green)
                    addInlineField("Channel", firstTrack.info.author)
                    addInlineField("Title", firstTrack.info.title)
                    addInlineField("Playlist", playlist.name)
                    setFooter("Requested by " + member.discriminatedName, member.avatarUrl)
                    setTimestampToNow()
                }
            }.subscribe()
        }
    }

    override fun loadFailed(exception: FriendlyException) {
        channel.createEmbed {
            it.apply {
                setTitle("Linkie Error")
                setColor(Color.red)
                setFooter("Requested by " + member.discriminatedName, member.avatarUrl)
                setTimestamp(Instant.now())
                addField("Error occurred while processing the track:", exception.localizedMessage.replace(System.getenv("GOOGLEAPI"), "*"), false)
            }
        }.subscribe()
    }
}