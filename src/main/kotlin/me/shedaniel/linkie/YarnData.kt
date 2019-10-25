package me.shedaniel.linkie

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class MappingsContainer(
        val version: String,
        val classes: MutableList<Class> = mutableListOf()
) {
    fun getClass(intermediaryName: String): Class? =
            classes.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateClass(intermediaryName: String): Class =
            getClass(intermediaryName) ?: Class(intermediaryName).also { classes.add(it) }

    fun prettyPrint() {
        classes.forEach {
            it.apply {
                println("$intermediaryName: $mappedName")
                methods.forEach {
                    it.apply {
                        println("  $intermediaryName $intermediaryDesc: $mappedName $mappedDesc")
                    }
                }
                fields.forEach {
                    it.apply {
                        println("  $intermediaryName $intermediaryDesc: $mappedName $mappedDesc")
                    }
                }
                println()
            }
        }
    }
}

data class Class(
        val intermediaryName: String,
        val obfName: Obf = Obf(),
        var mappedName: String? = null,
        val methods: MutableList<Method> = mutableListOf(),
        val fields: MutableList<Field> = mutableListOf()
) {
    fun getMethod(intermediaryName: String): Method? =
            methods.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateMethod(intermediaryName: String, intermediaryDesc: String): Method =
            getMethod(intermediaryName) ?: Method(intermediaryName, intermediaryDesc).also { methods.add(it) }

    fun getField(intermediaryName: String): Field? =
            fields.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateField(intermediaryName: String, intermediaryDesc: String): Field =
            getField(intermediaryName) ?: Field(intermediaryName, intermediaryDesc).also { fields.add(it) }
}

data class Method(
        val intermediaryName: String,
        val intermediaryDesc: String,
        val obfName: Obf = Obf(),
        val obfDesc: Obf = Obf(),
        var mappedName: String? = null,
        var mappedDesc: String? = null
)

data class Field(
        val intermediaryName: String,
        val intermediaryDesc: String,
        val obfName: Obf = Obf(),
        val obfDesc: Obf = Obf(),
        var mappedName: String? = null,
        var mappedDesc: String? = null
)

data class Obf(
        var client: String? = null,
        var server: String? = null,
        var merged: String? = null
) {
    fun list(): List<String> {
        val list = mutableListOf<String>()
        if (client != null) list.add(client!!)
        if (server != null) list.add(server!!)
        if (merged != null) list.add(merged!!)
        return list
    }

    fun isMerged(): Boolean = merged != null
    fun isEmpty(): Boolean = list().isEmpty()
}

@Serializable
data class YarnBuild(
        val gameVersion: String,
        val separator: String,
        val build: Int,
        val maven: String,
        val version: String,
        val stable: Boolean
)

private val executor = Executors.newScheduledThreadPool(16)
val mappingsContainers = mutableListOf<MappingsContainer>()
val yarnBuilds = mutableMapOf<String, YarnBuild>()
val json = Json(JsonConfiguration.Stable.copy(strictMode = false))

fun startLoop() {
    executor.scheduleAtFixedRate(::updateYarn, 0, 20, TimeUnit.MINUTES)
}

fun getMappingsContainer(version: String): MappingsContainer? = mappingsContainers.firstOrNull { it.version == version }

fun tryLoadMappingContainer(version: String): MappingsContainer {
    return (getMappingsContainer(version) ?: if (yarnBuilds.containsKey(version)) {
        version.loadOfficialYarn()
        getMappingsContainer(version)
    } else null) ?: getMappingsContainer("1.2.5") ?: throw NullPointerException("Please report this issue!")
}

fun updateYarn() {
    mappingsContainers.clear()
    yarnBuilds.clear()
    val buildMap = LinkedHashMap<String, MutableList<YarnBuild>>()
    json.parse(YarnBuild.serializer().list, URL("https://meta.fabricmc.net/v2/versions/yarn").readText()).forEach { buildMap.getOrPut(it.gameVersion, { mutableListOf() }).add(it) }
    buildMap.forEach { version, builds -> builds.maxBy { it.build }?.apply { yarnBuilds[version] = this } }
    yarnBuilds.keys.firstOrNull { it.contains('.') && !it.contains('-') }?.loadOfficialYarn()
    yarnBuilds.keys.firstOrNull()?.loadOfficialYarn()
    mappingsContainers.add(MappingsContainer("1.2.5").apply {
        classes.clear()
        loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/1.2.5%20Merge.tiny"))
        loadNamedFromGithubRepo("minecraft-cursed-legacy/yarn", "1.2.5", showError = false)
    })
    mappingsContainers.add(MappingsContainer("b1.7.3").apply {
        classes.clear()
        loadNamedFromGithubRepo("minecraft-cursed-legacy/Minecraft-Cursed-POMF", "master", ignoreError = true)
    })
    println("Updated KYarn")
}

internal fun String?.loadOfficialYarn() =
        this?.also {
            println("Loading yarn for $it")
            mappingsContainers.add(MappingsContainer(it).apply {
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