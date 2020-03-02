package me.shedaniel.linkie

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.toVersion
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

private val mcpContainers = CopyOnWriteArrayList<MappingsContainer>()
private val mcpConfig = mutableListOf<Version>()
internal val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()

fun getMCPMappingsContainer(version: String): MappingsContainer? = mcpContainers.firstOrNull { it.version == version }

fun tryLoadMCPMappingContainer(version: String, defaultContainer: MappingsContainer?): Triple<String, Boolean, () -> MappingsContainer> {
    return tryLoadMCPMappingContainerDoNotThrow(version, defaultContainer) ?: throw NullPointerException("Please report this issue!")
}

fun tryLoadMCPMappingContainerDoNotThrow(version: String, defaultContainer: MappingsContainer?): Triple<String, Boolean, () -> MappingsContainer>? =
        if (defaultContainer == null) tryLoadMCPMappingContainerDoNotThrowSupplier(version, null, null)
        else tryLoadMCPMappingContainerDoNotThrowSupplier(version, defaultContainer.version) { defaultContainer }

@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
fun tryLoadMCPMappingContainerDoNotThrowSupplier(version: String, defaultContainerVersion: String?, defaultContainer: (() -> MappingsContainer)?): Triple<String, Boolean, () -> MappingsContainer>? {
    val mightBeCached = getMCPMappingsContainer(version)
    if (mightBeCached != null)
        return Triple(mightBeCached.version, true, { mightBeCached!! })
    try {
        if (mcpConfigSnapshots[version.toVersion()]?.isNotEmpty() == true) {
            return Triple(version.toLowerCase(), false, {
                version.toVersion().loadLatestSnapshot(mcpContainers, false)
                getMCPMappingsContainer(version)!!
            })
        }
    } catch (ignored: NumberFormatException) {
    }
    if (defaultContainer != null && defaultContainerVersion != null) {
        return Triple(defaultContainerVersion, true, defaultContainer)
    }
    return null
}

fun getLatestMCPVersion(): Version? = mcpConfigSnapshots.filterValues { it.isNotEmpty() }.keys.max()

fun updateMCP() {
    try {
        println("Updating MCP")
        mcpContainers.clear()
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
        System.gc()
        json.parseJson(URL("http://export.mcpbot.bspk.rs/versions.json").readText()).jsonObject.forEach { mcVersion, mcpVersionsObj ->
            val list = mcpConfigSnapshots.getOrPut(mcVersion.toVersion(), { mutableListOf() })
            mcpVersionsObj.jsonObject["snapshot"]?.jsonArray?.forEach {
                list.add(it.primitive.content)
            }
        }
        getLatestMCPVersion()?.loadLatestSnapshot(mcpContainers)
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

private fun Version?.loadLatestSnapshot(containers: MutableList<MappingsContainer>, async: Boolean = true) {
    this?.also { mcVersion ->
        if (async)
            GlobalScope.launch {
                mcVersion.loadNonAsyncLatestSnapshot(containers)
            }
        else mcVersion.loadNonAsyncLatestSnapshot(containers)
    }
}

private fun Version?.loadNonAsyncLatestSnapshot(containers: MutableList<MappingsContainer>) {
    this?.also { mcVersion ->
        val latestSnapshot = mcpConfigSnapshots[mcVersion]?.max() ?: return@also
        MappingsContainer(mcVersion.toString(), name = "MCP").apply {
            println("Loading mcp for $version")
            loadTsrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/$mcVersion/mcp_config-$mcVersion.zip"))
            loadMCPFromURLZip(URL("http://export.mcpbot.bspk.rs/mcp_snapshot/$latestSnapshot-$mcVersion/mcp_snapshot-$latestSnapshot-$mcVersion.zip"))
            mappingSource = MappingsContainer.MappingSource.MCP_TSRG
        }.also {
            containers.add(it)
            if (containers.size > 6)
                containers.firstOrNull { mcpConfigSnapshots.keys.any { version -> version.toString() == it.version } && getLatestMCPVersion()?.toString() != it.version }?.let { containers.remove(it) }
            System.gc()
        }
    }
}