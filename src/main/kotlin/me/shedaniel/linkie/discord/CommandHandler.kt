package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.config.ConfigManager

object CommandHandler : CommandAcceptor {
    private val commandMap: MutableMap<String, CommandBase> = mutableMapOf()
    internal val commands: MutableMap<CommandBase, MutableSet<String>> = mutableMapOf()

    fun registerCommand(command: CommandBase, vararg l: String): CommandHandler {
        for (ll in l)
            commandMap[ll.toLowerCase()] = command
        commands.getOrPut(command, ::mutableSetOf).addAll(l)
        command.postRegister()
        return this
    }

    override fun getPrefix(event: MessageCreateEvent): String? =
        event.guildId.orElse(null)?.let { ConfigManager[it.asLong()].prefix }

    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        if (cmd in commandMap)
            commandMap[cmd]!!.execute(event, prefix, user, cmd, args, channel)
    }
}