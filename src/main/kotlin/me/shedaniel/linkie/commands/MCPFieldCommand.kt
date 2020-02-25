package me.shedaniel.linkie.commands

import me.shedaniel.linkie.getLatestMCPVersion

object MCPFieldCommand : AYarnFieldCommand({ getLatestMCPVersion()?.toString() ?: "" }, false) {
    override fun getName(): String? = "MCP Field Command"
    override fun getDescription(): String? = "Query mcp fields."
}