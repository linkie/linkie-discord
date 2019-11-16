package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.*
import java.util.*
import java.util.concurrent.ScheduledExecutorService

object HelpCommand : CommandBase {
    override fun execute(service: ScheduledExecutorService, event: MessageCreateEvent, author: Member, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isNotEmpty())
            throw InvalidUsageException("+$cmd")
        val prefix = commandApi.getPrefix(!channel.type.name.startsWith("GUILD_") || event.guildId.isPresent && event.guildId.get().asLong() != 432055962233470986L)
        channel.createEmbed {
            it.setTitle("Linkie Help Command")
            it.setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
            commandApi.commands.filter { it.key.getName() != null && it.key.getDescription() != null && it.value.isNotEmpty() }
                    .toSortedMap(Comparator.comparing<CommandBase, String> { it.getName()!! })
                    .forEach { cmd, values ->
                        if (values.isEmpty()) return@forEach
                        val name = cmd.getName() ?: return@forEach
                        val desc = cmd.getDescription() ?: return@forEach
                        it.addInlineField(name, "Value: ${values.joinToString { "$prefix$it" }}\n$desc")
                    }
            it.setTimestampToNow()
        }.subscribe()
    }

    override fun getDescription(): String? = "Displays this message."
    override fun getName(): String? = "Help Command"
}