package me.shedaniel.linkie

import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.toVersion
import java.net.URL

private val mcpContainers = mutableListOf<MappingsContainer>()
private val mcpConfig = mutableListOf<Version>()
internal val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()

fun getMCPMappingsContainer(version: String): MappingsContainer? = mcpContainers.firstOrNull { it.version == version }

@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
fun tryLoadMCPMappingContainer(version: String, defaultContainer: MappingsContainer?): Triple<String, Boolean, () -> MappingsContainer> {
    val mightBeCached = getMCPMappingsContainer(version)
    if (mightBeCached != null)
        return Triple(mightBeCached.version, true, { mightBeCached!! })
    try {
        if (mcpConfigSnapshots[version.toVersion()]?.isNotEmpty() == true) {
            return Triple(version.toLowerCase(), false, {
                version.toVersion().loadLatestSnapshot(mcpContainers)
                getMCPMappingsContainer(version)!!
            })
        }
    } catch (ignored: NumberFormatException) {
    }
    if (defaultContainer != null) {
        return Triple(defaultContainer.version, true, { defaultContainer!! })
    }
    throw NullPointerException("Please report this issue!")
}

fun getLatestMCPVersion(): Version? = mcpConfigSnapshots.filterValues { it.isNotEmpty() }.keys.max()

fun updateMCP() {
    try {
        val c = mutableListOf<MappingsContainer>()
        mcpConfig.clear()
        mcpConfigSnapshots.clear()
        URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/maven-metadata.xml").readText().lines().forEach {
            val s = it.trimIndent()
            if (!s.contains('-') && s.startsWith("<version>") && s.endsWith("</version>")) {
                val version = s.substring(9, s.length - 10)
                mcpConfig.add(version.toVersion())
                mcpConfigSnapshots[version.toVersion()] = mutableListOf()
            }
        }
        mcpConfig.sort()
        json.parseJson(URL("http://export.mcpbot.bspk.rs/versions.json").readText()).jsonObject.forEach { mcVersion, mcpVersionsObj ->
            val list = mcpConfigSnapshots.getOrPut(mcVersion.toVersion(), { mutableListOf() })
            mcpVersionsObj.jsonObject["snapshot"]?.jsonArray?.forEach {
                list.add(it.primitive.content)
            }
        }
        getLatestMCPVersion()?.loadLatestSnapshot(c)
        mcpContainers.clear()
        mcpContainers.addAll(c)
        println("Updated KMCP")
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

private fun Version?.loadLatestSnapshot(containers: MutableList<MappingsContainer>) =
        this?.also { mcVersion ->
            val latestSnapshot = mcpConfigSnapshots[mcVersion]?.max() ?: return@also
            MappingsContainer(mcVersion.toString(), name = "MCP").apply {
                loadTsrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/$mcVersion/mcp_config-$mcVersion.zip"))
                loadMCPFromURLZip(URL("http://export.mcpbot.bspk.rs/mcp_snapshot/$latestSnapshot-$mcVersion/mcp_snapshot-$latestSnapshot-$mcVersion.zip"))
            }.also { containers.add(it) }
        }