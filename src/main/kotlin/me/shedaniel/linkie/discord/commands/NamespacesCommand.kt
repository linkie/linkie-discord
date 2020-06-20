package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.discriminatedName
import me.shedaniel.linkie.discord.setTimestampToNow

object NamespacesCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        channel.createEmbed {
            it.apply {
                setTitle("List of Namespaces")
                setFooter("Requested by ${user.discriminatedName}", user.avatarUrl)
                setDescription(Namespaces.namespaces.keys.joinToString(", "))
                setTimestampToNow()
            }
        }.subscribe()
    }
}