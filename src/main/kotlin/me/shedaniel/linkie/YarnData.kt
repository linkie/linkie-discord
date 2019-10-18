package me.shedaniel.linkie

import com.google.gson.reflect.TypeToken
import me.shedaniel.cursemetaapi.CurseMetaAPI
import me.shedaniel.linkie.spring.LoadMeta
import java.io.InputStreamReader
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

private val executor = Executors.newScheduledThreadPool(16)
val mappingsContainers = mutableListOf<MappingsContainer>()

fun startLoop() {
    executor.scheduleAtFixedRate(::updateYarn, 0, 5, TimeUnit.MINUTES)
}

fun getMappingsContainer(version: String): MappingsContainer? = mappingsContainers.firstOrNull { it.version == version }

fun updateYarn() {
    mappingsContainers.clear()
    mappingsContainers.add(MappingsContainer("1.14.4").apply {
        classes.clear()
        loadIntermediaryFromMaven(version)
        val list = LinkieBot.GSON.fromJson<List<LoadMeta.YarnBuild>>(
                InputStreamReader(CurseMetaAPI.InternetUtils.getSiteStream(URL("https://meta.fabricmc.net/v2/versions/yarn"))),
                object : TypeToken<List<LoadMeta.YarnBuild>>() {}.type
        )
        val yarnMaven = list.first { it.gameVersion == version }.maven
        loadNamedFromMaven(yarnMaven.substring(yarnMaven.lastIndexOf(':') + 1))
    })
    mappingsContainers.add(MappingsContainer("1.2.5").apply {
        classes.clear()
        loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/1.2.5%20Merge.tiny"))
        loadNamedFromGithubRepo("minecraft-cursed-legacy/yarn", "1.2.5")
    })
    println("Updated KYarn")
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
        println("$parametersUnmapped $returnsUnmapped")
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

fun String.onlyClass(c: Char = '/'): String {
    val indexOf = lastIndexOf(c)
    return if (indexOf < 0) this else substring(indexOf + 1)
}