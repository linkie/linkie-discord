package me.shedaniel.linkie

import me.shedaniel.linkie.utils.toVersion
import net.fabricmc.mappings.MappingsProvider
import org.apache.commons.lang3.StringUtils
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import java.util.zip.ZipInputStream

fun MappingsContainer.loadIntermediaryFromMaven(
        mcVersion: String,
        repo: String = "https://maven.fabricmc.net",
        group: String = "net.fabricmc.intermediary"
) =
        loadIntermediaryFromTinyJar(URL("$repo/${group.replace('.', '/')}/$mcVersion/intermediary-$mcVersion.jar"))

fun MappingsContainer.loadIntermediaryFromTinyJar(url: URL) {
    val stream = ZipInputStream(url.openStream())
    while (true) {
        val entry = stream.nextEntry ?: break
        if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "mappings.tiny") {
            loadIntermediaryFromTinyInputStream(stream)
            break
        }
    }
}

fun MappingsContainer.loadIntermediaryFromTinyFile(url: URL) {
    loadIntermediaryFromTinyInputStream(url.openStream())
}

fun MappingsContainer.loadIntermediaryFromTinyInputStream(stream: InputStream) {
    val mappings = MappingsProvider.readTinyMappings(stream, false)
    val isSplit = !mappings.namespaces.contains("official")
    mappings.classEntries.forEach { entry ->
        val intermediary = entry["intermediary"]
        getOrCreateClass(intermediary).apply {
            if (isSplit) {
                obfName.client = entry["client"]
                obfName.server = entry["server"]
            } else obfName.merged = entry["official"]
        }
    }
    mappings.methodEntries.forEach { entry ->
        val intermediaryTriple = entry["intermediary"]
        getOrCreateClass(intermediaryTriple.owner).apply {
            getOrCreateMethod(intermediaryTriple.name, intermediaryTriple.desc).apply {
                if (isSplit) {
                    val clientTriple = entry["client"]
                    val serverTriple = entry["server"]
                    obfName.client = clientTriple?.name
                    obfName.server = serverTriple?.name
                    obfDesc.client = clientTriple?.desc
                    obfDesc.server = serverTriple?.desc
                } else {
                    val officialTriple = entry["official"]
                    obfName.merged = officialTriple?.name
                    obfDesc.merged = officialTriple?.desc
                }
            }
        }
    }
    mappings.fieldEntries.forEach { entry ->
        val intermediaryTriple = entry["intermediary"]
        getOrCreateClass(intermediaryTriple.owner).apply {
            getOrCreateField(intermediaryTriple.name, intermediaryTriple.desc).apply {
                if (isSplit) {
                    val clientTriple = entry["client"]
                    val serverTriple = entry["server"]
                    obfName.client = clientTriple?.name
                    obfName.server = serverTriple?.name
                    obfDesc.client = clientTriple?.desc
                    obfDesc.server = serverTriple?.desc
                } else {
                    val officialTriple = entry["official"]
                    obfName.merged = officialTriple?.name
                    obfDesc.merged = officialTriple?.desc
                }
            }
        }
    }
}

fun MappingsContainer.loadNamedFromMaven(
        yarnVersion: String,
        repo: String = "https://maven.fabricmc.net",
        group: String = "net.fabricmc.yarn",
        showError: Boolean = true
) =
        if (yarnVersion.split('+').first().toVersion().isAtLeast(1, 14, 4))
            loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/yarn-$yarnVersion-v2.jar"), showError)
        else loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/yarn-$yarnVersion.jar"), showError)

fun MappingsContainer.loadNamedFromTinyJar(url: URL, showError: Boolean = true) {
    val stream = ZipInputStream(url.openStream())
    while (true) {
        val entry = stream.nextEntry ?: break
        if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "mappings.tiny") {
            loadNamedFromTinyInputStream(stream, showError)
            break
        }
    }
}

fun MappingsContainer.loadNamedFromTinyFile(url: URL, showError: Boolean = true) {
    loadNamedFromTinyInputStream(url.openStream(), showError)
}

fun MappingsContainer.loadNamedFromTinyInputStream(stream: InputStream, showError: Boolean = true) {
    val mappings = MappingsProvider.readTinyMappings(stream, false)
    mappings.classEntries.forEach { entry ->
        val intermediary = entry["intermediary"]
        val clazz = getClass(intermediary)
        if (clazz == null) {
            if (showError)
                println("Class $intermediary does not have intermediary name! Skipping!")
        } else clazz.apply {
            if (mappedName == null)
                mappedName = entry["named"]
        }
    }
    mappings.methodEntries.forEach { entry ->
        val intermediaryTriple = entry["intermediary"]
        val clazz = getClass(intermediaryTriple.owner)
        if (clazz == null) {
            if (showError)
                println("Class ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
        } else clazz.apply {
            val method = getMethod(intermediaryTriple.name)
            if (method == null) {
                if (showError)
                    println("Method ${intermediaryTriple.name} in ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
            } else method.apply {
                val namedTriple = entry["named"]
                if (mappedName == null)
                    mappedName = namedTriple?.name
                if (mappedDesc == null)
                    mappedDesc = namedTriple?.desc
            }
        }
    }
    mappings.fieldEntries.forEach { entry ->
        val intermediaryTriple = entry["intermediary"]
        val clazz = getClass(intermediaryTriple.owner)
        if (clazz == null) {
            if (showError)
                println("Class ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
        } else clazz.apply {
            val field = getField(intermediaryTriple.name)
            if (field == null) {
                if (showError)
                    println("Field ${intermediaryTriple.name} in ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
            } else field.apply {
                val namedTriple = entry["named"]
                if (mappedName == null)
                    mappedName = namedTriple?.name
                if (mappedDesc == null)
                    mappedDesc = namedTriple?.desc
            }
        }
    }
}

fun MappingsContainer.loadNamedFromGithubRepo(repo: String, branch: String, showError: Boolean = true, ignoreError: Boolean = false) =
        loadNamedFromEngimaZip(URL("https://github.com/$repo/archive/$branch.zip"), showError, ignoreError)

fun MappingsContainer.loadNamedFromEngimaZip(url: URL, showError: Boolean = true, ignoreError: Boolean = false) =
        loadNamedFromEngimaStream(url.openStream(), showError, ignoreError)

fun MappingsContainer.loadNamedFromEngimaStream(stream: InputStream, showError: Boolean = true, ignoreError: Boolean = false) {
    val zipInputStream = ZipInputStream(stream)
    while (true) {
        val entry = zipInputStream.nextEntry ?: break
        if (!entry.isDirectory && entry.name.endsWith(".mapping")) {
            val isr = InputStreamReader(zipInputStream)
            val strings = ArrayList<String>()
            val reader = BufferedReader(isr)
            while (reader.ready())
                strings.add(reader.readLine())
            val lines = strings.map { EngimaLine(it, StringUtils.countMatches(it, '\t'), MappingsType.getByString(it.replace("\t", "").split(" ")[0])) }
            val levels = mutableListOf<Class?>()
            repeat(lines.filter { it.type != MappingsType.UNKNOWN }.map { it.indent }.max()!! + 1) { levels.add(null) }
            lines.forEach { line ->
                if (line.type == MappingsType.CLASS) {
                    var className = line.split[1]
                    for (i in 0 until line.indent)
                        className = "${levels[i]!!.intermediaryName}\$$className"
                    levels[line.indent] = if (ignoreError) getOrCreateClass(className).apply {
                        mappedName = if (line.split.size >= 3) line.split[2] else null
                    } else getClass(className)?.apply {
                        mappedName = if (line.split.size >= 3) line.split[2] else null
                    }
                    if (levels[line.indent] == null && showError)
                        println("Class $className does not have intermediary name! Skipping!")
                } else if (line.type == MappingsType.METHOD) {
                    if (levels[line.indent - 1] == null) {
                        if (showError)
                            println("Class of ${line.split[1]} does not have intermediary name! Skipping!")
                    } else {
                        levels[line.indent - 1]!!.apply {
                            val method = if (line.split[1] == "<init>") Method("<init>", line.split.last()).also { methods.add(it) } else if (ignoreError) getOrCreateMethod(line.split[1], line.split.last()) else getMethod(line.split[1])
                            if (method == null && showError)
                                println("Method ${line.split[1]} in ${levels[line.indent - 1]!!.intermediaryName} does not have intermediary name! Skipping!")
                            if (line.split.size == 4)
                                method?.apply {
                                    mappedName = line.split[2]
                                }
                        }
                    }
                } else if (line.type == MappingsType.FIELD) {
                    if (levels[line.indent - 1] == null) {
                        if (showError)
                            println("Class of ${line.split[1]} does not have intermediary name! Skipping!")
                    } else {
                        levels[line.indent - 1]!!.apply {
                            val field = if (ignoreError) getOrCreateField(line.split[1], line.split.last()) else getField(line.split[1])
                            if (field == null && showError)
                                println("Field ${line.split[1]} in ${levels[line.indent - 1]!!.intermediaryName} does not have intermediary name! Skipping!")
                            if (line.split.size == 4)
                                field?.apply {
                                    mappedName = line.split[2]
                                }
                        }
                    }
                }
            }
        }
    }
}

private data class EngimaLine(
        val text: String,
        val indent: Int,
        val type: MappingsType
) {
    val split: List<String> by lazy { text.trimStart('\t').split(" ") }
}

internal enum class MappingsType {
    CLASS,
    FIELD,
    METHOD,
    UNKNOWN;

    companion object {
        fun getByString(string: String): MappingsType =
                MappingsType.values().firstOrNull { it.name.equals(string, true) } ?: UNKNOWN
    }
}