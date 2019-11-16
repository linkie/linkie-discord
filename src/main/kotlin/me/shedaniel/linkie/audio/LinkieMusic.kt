package me.shedaniel.linkie.audio

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.core.`object`.entity.*
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.voice.AudioProvider
import discord4j.voice.VoiceConnection
import me.shedaniel.linkie.*
import java.awt.Color
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue


object LinkieMusic {

    val playerManager = DefaultAudioPlayerManager()
    private val musicManagers: MutableMap<Snowflake, GuildMusicManager> = mutableMapOf()

    fun setupCommands() {
        api.eventDispatcher.on(VoiceStateUpdateEvent::class.java).subscribe {
            if (it.current.userId == api.selfId.orElse(null)) {
                musicManagers.remove(it.current.guildId)
            }
        }
        commandApi.registerCommand(object : CommandBase {
            override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
                if (args.size != 1)
                    throw InvalidUsageException("+$cmd <link>")
                val guildId = event.guildId.orElse(null)?.asLong()
                if (guildId == 591645350016712709 || guildId == 432055962233470986) {
                    val member = event.member.get()
                    val voiceState = member.voiceState.block()
                            ?: throw IllegalStateException("Failed to get the voice state of the user!")
                    val voiceChannel = voiceState.channel.block()
                            ?: throw IllegalStateException("Failed to locate the voice channel used by the user!")
                    val musicManager = getGuildAudioPlayer(member.guild.block()!!, voiceChannel)
                    playerManager.loadItem(args[0], OurAudioLoadResultHandler(channel, member, musicManager, args[0])).get()
                }
            }
        }, "play", "p")
        commandApi.registerCommand(object : CommandBase {
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
                    val musicManager = getGuildAudioPlayer(member.guild.block()!!, voiceChannel)
                    musicManager.scheduler.clear()
                    musicManager.player.stopTrack()
                    musicManager.voiceConnection!!.disconnect()
                    musicManager.voiceConnection = null
                    musicManagers.remove(guildId)
                    channel.createEmbed {
                        it.setTitle("Disconnected!")
                        it.setColor(Color.red)
                        it.setTimestamp(Instant.now())
                    }.subscribe()
                }
            }
        }, "dc", "disconnect", "fuckoff", "stop", "shutdown")
        commandApi.registerCommand(object : CommandBase {
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
                    val musicManager = getGuildAudioPlayer(member.guild.block()!!, voiceChannel)
                    val track = musicManager.scheduler.nextTrack()
                    if (track == null) {
                        musicManager.scheduler.clear()
                        musicManager.player.stopTrack()
                        musicManager.voiceConnection!!.disconnect()
                        musicManager.voiceConnection = null
                        musicManagers.remove(guildId)
                        channel.createEmbed {
                            it.setTitle("Disconnected!")
                            it.setDescription("There isn't any tracks left!")
                            it.setColor(Color.red)
                            it.setTimestamp(Instant.now())
                        }.subscribe()
                    } else {
                        channel.createEmbed {
                            it.apply {
                                setTitle("Now Playing!")
                                setColor(Color.green)
                                addInlineField("Channel", track.info.author)
                                addInlineField("Title", track.info.title)
                                setTimestampToNow()
                            }
                        }.subscribe()
                    }
                }
            }
        }, "skip")
    }

    fun setupMusic() {
        playerManager.configuration.setFrameBufferFactory({ a, b, c -> NonAllocatingAudioFrameBuffer(a, b, c) })
        AudioSourceManagers.registerRemoteSources(playerManager)
        AudioSourceManagers.registerLocalSource(playerManager)
    }

    internal fun play(musicManager: GuildMusicManager, track: AudioTrack) {
//        connectToFirstVoiceChannel(voiceConnection.)

        musicManager.scheduler.queue(track)
    }

//    private fun connectToFirstVoiceChannel(audioManager: AudioManager) {
//        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
//            for (voiceChannel in audioManager.getGuild().getVoiceChannels()) {
//                audioManager.openAudioConnection(voiceChannel)
//                break
//            }
//        }
//    }

    @Synchronized
    private fun getGuildAudioPlayer(guild: Guild, voiceChannel: VoiceChannel): GuildMusicManager {

//        val voiceState = api.self.block()?.asMember(guild.id)?.block()?.voiceState?.block()
//        if (voiceState == null || voiceState.channelId.isPresent) {
//            musicManager.voiceConnection = voiceChannel.join { spec -> spec.setProvider(musicManager.getSendHandler()) }.block()
//        }

        return musicManagers.getOrPut(guild.id, {
            GuildMusicManager(playerManager).also {
                it.voiceConnection = voiceChannel.join { spec -> spec.setProvider(it.getSendHandler()) }.block()
            }
        })
    }
}

class OurAudioLoadResultHandler(val channel: MessageChannel, val member: Member, val musicManager: GuildMusicManager, val url: String) : AudioLoadResultHandler {
    override fun trackLoaded(track: AudioTrack) {
        channel.createEmbed {
            it.apply {
                setTitle("Added to queue!")
                setColor(Color.green)
                addInlineField("Channel", track.info.author)
                addInlineField("Title", track.info.title)
                setTimestampToNow()
            }
        }.subscribe()
        track.userData = member.id.asLong()
        LinkieMusic.play(musicManager, track)
    }

    override fun noMatches() {
        channel.createEmbed {
            it.apply {
                setTitle("Linkie Error")
                setColor(Color.red)
                setFooter("Requested by " + member.discriminatedName, member.avatarUrl)
                setTimestamp(Instant.now())
                addField("Error occurred while processing the URL:", "Invaild Track URL: " + url, false)
            }
        }.subscribe()
    }

    override fun playlistLoaded(playlist: AudioPlaylist) {
        val firstTrack: AudioTrack = playlist.selectedTrack ?: playlist.tracks[0]
        channel.createEmbed {
            it.apply {
                setTitle("Added to queue!")
                setColor(Color.green)
                addInlineField("Channel", firstTrack.info.author)
                addInlineField("Title", firstTrack.info.title)
                addInlineField("Playlist", playlist.name)
                setTimestampToNow()
            }
        }.subscribe()
        LinkieMusic.play(musicManager, firstTrack)
    }

    override fun loadFailed(exception: FriendlyException?) {
        channel.createEmbed {
            it.apply {
                setTitle("Linkie Error")
                setColor(Color.red)
                setFooter("Requested by " + member.discriminatedName, member.avatarUrl)
                setTimestamp(Instant.now())
                addField("Error occurred while processing the track:", exception?.localizedMessage
                        ?: "", false)
            }
        }.subscribe()
    }
}

class GuildMusicManager(internal val manager: AudioPlayerManager) {
    internal val player: AudioPlayer = manager.createPlayer()
    internal val scheduler: TrackScheduler = TrackScheduler(player)
    internal var voiceConnection: VoiceConnection? = null
    private var audioProvider: LavaPlayerAudioProvider = LavaPlayerAudioProvider(player)

    init {
        player.addListener(scheduler)
    }

    fun getSendHandler(): LavaPlayerAudioProvider = audioProvider
}

class TrackScheduler(internal val player: AudioPlayer) : AudioEventAdapter() {
    private val queue: BlockingQueue<AudioTrack> = LinkedBlockingQueue()

    fun queue(track: AudioTrack) {
        if (!player.startTrack(track, true)) {
            queue.offer(track)
        }
    }

    fun nextTrack(): AudioTrack? {
        return queue.poll().apply {
            player.startTrack(this, false)
        }
    }

    fun clear() {
        queue.clear()
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            nextTrack()
        }
    }
}

class LavaPlayerAudioProvider(private val player: AudioPlayer) : AudioProvider(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize())) {
    private val frame = MutableAudioFrame().also { it.setBuffer(buffer) }

    override fun provide(): Boolean {
        val didProvide = player.provide(frame)
        if (didProvide) {
            buffer.flip()
        }
        return didProvide
    }
}