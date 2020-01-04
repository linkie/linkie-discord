package me.shedaniel.linkie

import kotlinx.serialization.list
import java.net.URL

private val yarnContainers = mutableListOf<MappingsContainer>()
val yarnBuilds = mutableMapOf<String, YarnBuild>()

fun getYarnMappingsContainer(version: String): MappingsContainer? = yarnContainers.firstOrNull { it.version == version }

@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
fun tryLoadYarnMappingContainer(version: String, defaultContainer: MappingsContainer?): Triple<String, Boolean, () -> MappingsContainer> {
    return tryLoadYarnMappingContainerDoNotThrow(version, defaultContainer) ?: throw NullPointerException("Please report this issue!")
}

@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
fun tryLoadYarnMappingContainerDoNotThrow(version: String, defaultContainer: MappingsContainer?): Triple<String, Boolean, () -> MappingsContainer>? {
    val mightBeCached = getYarnMappingsContainer(version)
    if (mightBeCached != null)
        return Triple(mightBeCached.version, true, { mightBeCached!! })
    if (yarnBuilds.containsKey(version)) {
        return Triple(version.toLowerCase(), false, {
            version.loadOfficialYarn(yarnContainers)
            getYarnMappingsContainer(version)!!
        })
    }
    if (defaultContainer != null) {
        return Triple(defaultContainer.version, true, { defaultContainer!! })
    }
    return null
}

var latestYarn = ""

fun updateYarn() {
    try {
        val c = mutableListOf<MappingsContainer>()
        yarnBuilds.clear()
        val buildMap = LinkedHashMap<String, MutableList<YarnBuild>>()
        json.parse(YarnBuild.serializer().list, URL("https://meta.fabricmc.net/v2/versions/yarn").readText()).forEach { buildMap.getOrPut(it.gameVersion, { mutableListOf() }).add(it) }
        buildMap.forEach { version, builds -> builds.maxBy { it.build }?.apply { yarnBuilds[version] = this } }
        yarnBuilds.keys.firstOrNull { it.contains('.') && !it.contains('-') }?.loadOfficialYarn(c)
        yarnBuilds.keys.firstOrNull()?.loadOfficialYarn(c)
        yarnBuilds.keys.firstOrNull()?.apply { latestYarn = this }
        "1.14.3".loadOfficialYarn(c)
        c.add(MappingsContainer("1.2.5").apply {
            classes.clear()
            loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/1.2.5%20Merge.tiny"))
            loadNamedFromGithubRepo("Blayyke/yarn", "1.2.5", showError = false)
        })
        c.add(MappingsContainer("b1.7.3", name = "POMF").apply {
            classes.clear()
            loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/Beta%201.7.3%20Merge.tiny"))
            loadNamedFromGithubRepo("minecraft-cursed-legacy/Minecraft-Cursed-POMF", "master", showError = false)
        })
        yarnContainers.clear()
        yarnContainers.addAll(c)
        println("Updated KYarn")
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

private fun String.loadOfficialYarn(c: MutableList<MappingsContainer>) {
    if (c.none { it.version == this })
        c.add(MappingsContainer(this).apply {
            println("Loading yarn for $version")
            classes.clear()
            loadIntermediaryFromMaven(version)
            val yarnMaven = yarnBuilds[version]!!.maven
            loadNamedFromMaven(yarnMaven.substring(yarnMaven.lastIndexOf(':') + 1), showError = false)
        })
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
