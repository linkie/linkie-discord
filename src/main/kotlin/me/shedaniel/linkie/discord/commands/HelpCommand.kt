package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.*

object HelpCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateEmpty(prefix, cmd)
        channel.createEmbedMessage {
            setTitle("Linkie Help Command")
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setDescription("View the list of commands at https://github.com/shedaniel/linkie-discord/wiki/Commands")
        }.subscribe()
    }

    override fun getDescription(): String? = "Displays this message."
    override fun getName(): String? = "Help Command"
}