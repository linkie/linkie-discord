package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.CommandBase
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discriminatedName
import me.shedaniel.linkie.setTimestampToNow

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