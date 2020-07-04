package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.*

object HelpCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateEmpty(cmd)
        val prefix = commandMap.prefix
        val commandCategories = CommandCategory.getValues(event.guildId.orElse(null)).filter { c ->
            CommandHandler.commands.filter { it.key.getCategory() == c && it.key.getName() != null && it.key.getDescription() != null && it.value.isNotEmpty() }.isNotEmpty()
        }
        commandCategories.forEachIndexed { index, category ->
            channel.createEmbedMessage {
                if (index == 0) setTitle("Linkie Help Command")
                else category.description?.let(this::setTitle)
                if (index == commandCategories.size - 1) {
                    setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                    setTimestampToNow()
                }
                CommandHandler.commands.filter { it.key.getCategory() == category && it.key.getName() != null && it.key.getDescription() != null && it.value.isNotEmpty() }
                        .toSortedMap(compareBy { it.getName() })
                        .forEach { (cmd, values) ->
                            if (values.isEmpty()) return@forEach
                            val name = cmd.getName() ?: return@forEach
                            val desc = cmd.getDescription() ?: return@forEach
                            addInlineField(name, "Value: ${values.joinToString { "$prefix$it" }}\n$desc")
                        }
            }.subscribe()
        }
    }

    override fun getDescription(): String? = "Displays this message."
    override fun getName(): String? = "Help Command"
}