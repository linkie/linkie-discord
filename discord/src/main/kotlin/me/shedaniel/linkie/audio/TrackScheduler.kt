package me.shedaniel.linkie.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class TrackScheduler(internal val player: AudioPlayer) : AudioEventAdapter() {
    private val queue: BlockingQueue<AudioTrack> = LinkedBlockingQueue()
    internal var loop: Boolean = false

    init {
        player.addListener(this)
    }

    fun queue(track: AudioTrack): Boolean {
        return if (player.playingTrack != null) {
            queue.offer(track)
            false
        } else {
            player.playTrack(track)
            true
        }
    }

    fun nextTrack(lastTrack: AudioTrack? = null): AudioTrack? {
        if (loop) {
            if (lastTrack != null) {
                return lastTrack.makeClone().apply {
                    player.startTrack(this, false)
                }
            }
            return queue.poll().apply {
                player.startTrack(this, false)
            }
        } else {
            return queue.poll().apply {
                player.startTrack(this, false)
            }
        }
    }

    fun clear() {
        queue.clear()
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            nextTrack(track)
        }
    }
}
