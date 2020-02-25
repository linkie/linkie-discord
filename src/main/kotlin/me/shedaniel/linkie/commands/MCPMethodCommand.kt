package me.shedaniel.linkie.commands

import me.shedaniel.linkie.getLatestMCPVersion

object MCPMethodCommand : AYarnMethodCommand({ getLatestMCPVersion()?.toString() ?: "" }, false) {
    override fun getName(): String? = "MCP Method Command"
    override fun getDescription(): String? = "Query mcp methods."
}