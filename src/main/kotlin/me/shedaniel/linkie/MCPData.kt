package me.shedaniel.linkie

import java.net.URL

private val mcpContainers = mutableListOf<MappingsContainer>()
private val mcpConfig = mutableListOf<Version>()
private val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()

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

class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    constructor(major: Int, minor: Int) : this(major, minor, 0)

    private val version = versionOf(major, minor, patch)

    private fun versionOf(major: Int, minor: Int, patch: Int): Int {
        require(major in 0..255 && minor in 0..255 && patch in 0..255) {
            "Version components are out of range: $major.$minor.$patch"
        }
        return major.shl(16) + minor.shl(8) + patch
    }

    override fun toString(): String = if (patch == 0) "$major.$minor" else "$major.$minor.$patch"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherVersion = (other as? Version) ?: return false
        return this.version == otherVersion.version
    }

    override fun hashCode(): Int = version

    override fun compareTo(other: Version): Int = version - other.version

    fun isAtLeast(major: Int, minor: Int): Boolean = // this.version >= versionOf(major, minor, 0)
            this.major > major || (this.major == major &&
                    this.minor >= minor)

    fun isAtLeast(major: Int, minor: Int, patch: Int): Boolean = // this.version >= versionOf(major, minor, patch)
            this.major > major || (this.major == major &&
                    (this.minor > minor || this.minor == minor &&
                            this.patch >= patch))
}

private fun String.toVersion(): Version {
    val byDot = split('.')

    return when (byDot.size) {
        0 -> Version(0, 0)
        1 -> Version(byDot[0].toInt(), 0)
        2 -> Version(byDot[0].toInt(), byDot[1].toInt())
        3 -> Version(byDot[0].toInt(), byDot[1].toInt(), byDot[2].toInt())
        else -> throw IllegalStateException()
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