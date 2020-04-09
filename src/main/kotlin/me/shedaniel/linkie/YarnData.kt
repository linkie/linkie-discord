package me.shedaniel.linkie

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.list
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

private val yarnContainers = CopyOnWriteArrayList<MappingsContainer>()
val yarnBuilds = mutableMapOf<String, YarnBuild>()

fun getYarnMappingsContainer(version: String): MappingsContainer? = yarnContainers.firstOrNull { it.version == version }

fun tryLoadYarnMappingContainer(version: String, defaultContainer: MappingsContainer?): Triple<String, Boolean, () -> MappingsContainer> {
    return tryLoadYarnMappingContainerDoNotThrow(version, defaultContainer) ?: throw NullPointerException("Please report this issue!")
}

fun tryLoadYarnMappingContainerDoNotThrow(version: String, defaultContainer: MappingsContainer?): Triple<String, Boolean, () -> MappingsContainer>? =
        if (defaultContainer == null) tryLoadYarnMappingContainerDoNotThrowSupplier(version, null, null)
        else tryLoadYarnMappingContainerDoNotThrowSupplier(version, defaultContainer.version) { defaultContainer }

@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
fun tryLoadYarnMappingContainerDoNotThrowSupplier(version: String, defaultContainerVersion: String?, defaultContainer: (() -> MappingsContainer)?): Triple<String, Boolean, () -> MappingsContainer>? {
    val mightBeCached = getYarnMappingsContainer(version)
    if (mightBeCached != null)
        return Triple(mightBeCached.version, true, { mightBeCached!! })
    if (yarnBuilds.containsKey(version)) {
        return Triple(version.toLowerCase(), false) {
            version.loadOfficialYarn(yarnContainers, false)
            getYarnMappingsContainer(version)!!
        }
    }
    if (defaultContainer != null && defaultContainerVersion != null) {
        return Triple(defaultContainerVersion, true, defaultContainer)
    }
    return null
}

var latestYarn = ""

fun updateYarn() {
    try {
        println("Updating yarn")
        yarnContainers.clear()
        yarnBuilds.clear()
        val buildMap = LinkedHashMap<String, MutableList<YarnBuild>>()
        json.parse(YarnBuild.serializer().list, URL("https://meta.fabricmc.net/v2/versions/yarn").readText()).forEach { buildMap.getOrPut(it.gameVersion, { mutableListOf() }).add(it) }
        buildMap.forEach { (version, builds) -> builds.maxBy { it.build }?.apply { yarnBuilds[version] = this } }
        System.gc()
        yarnBuilds.keys.firstOrNull { it.contains('.') && !it.contains('-') }?.takeIf { it != yarnBuilds.keys.firstOrNull() }?.loadOfficialYarn(yarnContainers)
        yarnBuilds.keys.firstOrNull()?.loadOfficialYarn(yarnContainers)
        yarnBuilds.keys.firstOrNull()?.apply { latestYarn = this }
//        "1.14.3".loadOfficialYarn(yarnContainers)
        GlobalScope.launch {
            yarnContainers.add(MappingsContainer("1.2.5").apply {
                println("Loading yarn for $version")
                classes.clear()
                loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/1.2.5%20Merge.tiny"))
                loadNamedFromGithubRepo("Blayyke/yarn", "1.2.5", showError = false)
                mappingSource = MappingsContainer.MappingSource.ENGIMA
            })
        }
        GlobalScope.launch {
            yarnContainers.add(MappingsContainer("b1.7.3", name = "POMF").apply {
                println("Loading yarn for $version")
                classes.clear()
                loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/Beta%201.7.3%20Merge.tiny"))
                loadNamedFromGithubRepo("minecraft-cursed-legacy/Minecraft-Cursed-POMF", "master", showError = false)
                mappingSource = MappingsContainer.MappingSource.ENGIMA
            })
        }
        GlobalScope.launch {
            yarnContainers.add(MappingsContainer("1.8.9").apply {
                println("Loading yarn for $version")
                classes.clear()
                loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/hYdos/33c70aeca0f54eb031874bf78d8bd50d/raw/6723e56ecdddc9e1101be1a1cf7aa60e3367f72e/1.8.9_intermediary.tiny"))
                loadNamedFromGithubRepo("Legacy-Fabric/yarn-1.8.9", "1.8.9", showError = false)
                mappingSource = MappingsContainer.MappingSource.ENGIMA
            })
        }
        GlobalScope.launch {
            yarnContainers.add(MappingsContainer("S1.8.9", name = "Spigot").apply {
                println("Loading spigot mappings for $version")
                classes.clear()
                loadClassFromSpigot(URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-1.15.2-cl.csrg?at=refs%2Fheads%2Fmaster").openStream())
                loadMembersFromSpigot(URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-1.15.2-members.csrg?at=refs%2Fheads%2Fmaster").openStream())
                mappingSource = MappingsContainer.MappingSource.SPIGOT
            })
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

private fun String.loadOfficialYarn(c: MutableList<MappingsContainer>, async: Boolean = true) {
    val version = this
    if (async)
        GlobalScope.launch {
            version.loadNonAsyncOfficialYarn(c)
        }
    else version.loadNonAsyncOfficialYarn(c)
}

private fun String.loadNonAsyncOfficialYarn(c: MutableList<MappingsContainer>) {
    val version = this
    if (c.none { it.version == version }) {
        c.add(MappingsContainer(version).apply {
            println("Loading yarn for ${this.version}")
            classes.clear()
            loadIntermediaryFromMaven(this.version)
            val yarnMaven = yarnBuilds[this.version]!!.maven
            loadNamedFromMaven(yarnMaven.substring(yarnMaven.lastIndexOf(':') + 1), showError = false)
        })
        if (c.size > 8)
            c.firstOrNull { yarnBuilds.containsKey(it.version) && it.version != yarnBuilds.keys.firstOrNull { it.contains('.') && !it.contains('-') } && it.version != yarnBuilds.keys.firstOrNull() }?.let { c.remove(it) }
        System.gc()
    }
}


fun String.mapIntermediaryDescToNamed(mappingsContainer: MappingsContainer): String {
    if (startsWith('(') && contains(')')) {
        val split = split(')')
        val parametersOG = split[0].substring(1).toCharArray()
        val returnsOG = split[1].toCharArray()
        val parametersUnmapped = mutableListOf<String>()
        val returnsUnmapped = mutableListOf<String>()

        var lastT: String? = null
        for (char in parametersOG) {
            when {
                lastT != null && char == ';' -> {
                    parametersUnmapped.add(lastT)
                    lastT = null
                }
                lastT != null -> {
                    lastT += char
                }
                char == 'L' -> {
                    lastT = ""
                }
                else -> parametersUnmapped.add(char.toString())
            }
        }
        for (char in returnsOG) {
            when {
                lastT != null && char == ';' -> {
                    returnsUnmapped.add(lastT)
                    lastT = null
                }
                lastT != null -> {
                    lastT += char
                }
                char == 'L' -> {
                    lastT = ""
                }
                else -> returnsUnmapped.add(char.toString())
            }
        }
        return "(" + parametersUnmapped.joinToString("") {
            if (it.length != 1) {
                "L${mappingsContainer.getClass(it)?.mappedName ?: it};"
            } else
                it
        } + ")" + returnsUnmapped.joinToString("") {
            if (it.length != 1) {
                "L${mappingsContainer.getClass(it)?.mappedName ?: it};"
            } else
                it
        }
    }
    return this
}
