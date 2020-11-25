package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.sendEmbedMessage
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.discord.validateEmpty

object HelpCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateEmpty(prefix, cmd)
        channel.sendEmbedMessage(event.message) {
            setTitle("Linkie Help Command")
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            description = "View the list of commands at https://github.com/shedaniel/linkie-discord/wiki/Commands"
        }.subscribe()
    }

    override fun getDescription(): String? = "Displays this message."
    override fun getName(): String? = "Help Command"
}