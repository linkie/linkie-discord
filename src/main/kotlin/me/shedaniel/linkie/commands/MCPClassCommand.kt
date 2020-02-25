package me.shedaniel.linkie.commands

import me.shedaniel.linkie.getLatestMCPVersion

object MCPClassCommand : AYarnClassCommand({ getLatestMCPVersion()?.toString() ?: "" }, false) {
    override fun getName(): String? = "MCP Class Command"
    override fun getDescription(): String? = "Query mcp classes."
}