package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import fr.minuskube.pastee.JPastee
import fr.minuskube.pastee.data.Paste
import fr.minuskube.pastee.data.Section
import me.shedaniel.linkie.*
import java.util.*

object AWCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        channel.createEmbed {
            it.apply {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                setTitle("Everything Access Widener™")
                setDescription("https://github.com/shedaniel/LinkieBot/releases/tag/accesswidener")
            }
        }.subscribe()
    }

    override fun getName(): String? = "Everything Access Widener"
    override fun getDescription(): String? = "Destroys your fabric environment by making everything public!"
}