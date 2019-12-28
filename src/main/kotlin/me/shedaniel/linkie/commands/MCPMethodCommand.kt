package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.getLatestMCPVersion

object MCPMethodCommand : AYarnMethodCommand({ getLatestMCPVersion()?.toString() ?: "" }, false) {
    override fun getName(): String? = "MCP Method Command"
    override fun getDescription(): String? = "Query mcp methods."

    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (event.guildId.orElse(null)?.asLong() == 570630340075454474)
            throw IllegalAccessException("MCP commands are not available on this server.")
        super.execute(event, user, cmd, args, channel)
    }
}