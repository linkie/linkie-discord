package me.shedaniel.linkie.namespaces

import discord4j.core.`object`.util.Snowflake
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.toVersion
import java.net.URL

object MCPNamespace : Namespace("mcp") {
    private val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()

    init {
        registerProvider({ it in getAllVersions() }) {
            MappingsContainer(it, name = "MCP").apply {
                val latestSnapshot = mcpConfigSnapshots[it.toVersion()]?.max()!!
                println("Loading mcp for $version")
                mappingSource = if (it.toVersion() >= Version(1, 13)) {
                    loadTsrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/$it/mcp_config-$it.zip"))
                    MappingsContainer.MappingSource.MCP_TSRG
                } else {
                    loadSrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/$it/mcp-$it-srg.zip"))
                    MappingsContainer.MappingSource.MCP_SRG
                }
                loadMCPFromURLZip(URL("http://export.mcpbot.bspk.rs/mcp_snapshot/$latestSnapshot-$it/mcp_snapshot-$latestSnapshot-$it.zip"))
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf(getDefaultVersion(null, null))
    override fun getAllVersions(): List<String> = mcpConfigSnapshots.keys.map { it.toString() }
    override fun getDefaultVersion(command: String?, snowflake: Snowflake?): String = mcpConfigSnapshots.keys.max()!!.toString()
    override fun supportsAT(): Boolean = true
    override fun reloadData() {
        mcpConfigSnapshots.clear()
        json.parseJson(URL("http://export.mcpbot.bspk.rs/versions.json").readText()).jsonObject.forEach { mcVersion, mcpVersionsObj ->
            val list = mcpConfigSnapshots.getOrPut(mcVersion.toVersion(), { mutableListOf() })
            mcpVersionsObj.jsonObject["snapshot"]?.jsonArray?.forEach {
                list.add(it.primitive.content)
            }
        }
        mcpConfigSnapshots.filterValues { it.isEmpty() }.keys.toMutableList().forEach { mcpConfigSnapshots.remove(it) }
    }
}