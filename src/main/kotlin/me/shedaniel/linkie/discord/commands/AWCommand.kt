package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.*

object AWCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateEmpty(cmd)
        channel.createEmbedMessage {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setTitle("Everything Access Widener")
            setDescription("https://github.com/shedaniel/linkie-everythingaccesswidener/releases/tag/accesswideners")
        }.subscribe()
    }

    override fun getName(): String? = "Everything Access Widener"
    override fun getDescription(): String? = "Destroys your fabric environment by making everything public!"
}