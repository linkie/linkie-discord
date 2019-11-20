package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.*
import java.util.*

object HelpCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isNotEmpty())
            throw InvalidUsageException("!$cmd")
        val prefix = commandApi.getPrefix(event.guildId.orElse(null)?.asLong() == 432055962233470986L)
        val commandCategories = CommandCategory.getValues(event.guildId.orElse(null)).filter { c ->
            commandApi.commands.filter { it.key.getCategory() == c && it.key.getName() != null && it.key.getDescription() != null && it.value.isNotEmpty() }.isNotEmpty()
        }
        commandCategories.forEachIndexed { index, category ->
            channel.createEmbed {
                if (index == 0) it.setTitle("Linkie Help Command")
                else category.description?.let(it::setTitle)
                if (index == commandCategories.size - 1) {
                    it.setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                    it.setTimestampToNow()
                }
                commandApi.commands.filter { it.key.getCategory() == category && it.key.getName() != null && it.key.getDescription() != null && it.value.isNotEmpty() }
                        .toSortedMap(Comparator.comparing<CommandBase, String> { it.getName()!! })
                        .forEach { cmd, values ->
                            if (values.isEmpty()) return@forEach
                            val name = cmd.getName() ?: return@forEach
                            val desc = cmd.getDescription() ?: return@forEach
                            it.addInlineField(name, "Value: ${values.joinToString { "$prefix$it" }}\n$desc")
                        }
            }.subscribe()
        }
    }

    override fun getDescription(): String? = "Displays this message."
    override fun getName(): String? = "Help Command"
}