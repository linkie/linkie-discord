package me.shedaniel.linkie

import me.shedaniel.linkie.yarn.MappingsType
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
        group: String = "net.fabricmc.yarn"
) =
        loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/yarn-$yarnVersion.jar"))

fun MappingsContainer.loadNamedFromTinyJar(url: URL) {
    val stream = ZipInputStream(url.openStream())
    while (true) {
        val entry = stream.nextEntry ?: break
        if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "mappings.tiny") {
            loadNamedFromTinyInputStream(stream)
            break
        }
    }
}

fun MappingsContainer.loadNamedFromTinyFile(url: URL) {
    loadNamedFromTinyInputStream(url.openStream())
}

fun MappingsContainer.loadNamedFromTinyInputStream(stream: InputStream) {
    val mappings = MappingsProvider.readTinyMappings(stream, false)
    mappings.classEntries.forEach { entry ->
        val intermediary = entry["intermediary"]
        val clazz = getClass(intermediary)
        if (clazz == null) {
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
            println("Class ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
        } else clazz.apply {
            val method = getMethod(intermediaryTriple.name)
            if (method == null) {
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
            println("Class ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
        } else clazz.apply {
            val field = getField(intermediaryTriple.name)
            if (field == null) {
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

fun MappingsContainer.loadNamedFromGithubRepo(repo: String, branch: String) =
        loadNamedFromEngimaZip(URL("https://github.com/$repo/archive/$branch.zip"))

fun MappingsContainer.loadNamedFromEngimaZip(url: URL) =
        loadNamedFromEngimaStream(url.openStream())

fun MappingsContainer.loadNamedFromEngimaStream(stream: InputStream) {
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
                    levels[line.indent] = getClass(className)?.apply {
                        mappedName = if (line.split.size >= 3) line.split[2] else null
                    }
                    if (levels[line.indent] == null)
                        println("Class $className does not have intermediary name! Skipping!")
                } else if (line.type == MappingsType.METHOD) {
                    if (levels[line.indent - 1] == null)
                        println("Class of ${line.split[1]} does not have intermediary name! Skipping!")
                    else {
                        levels[line.indent - 1]!!.apply {
                            val method = if (line.split[1] == "<init>") Method("<init>", line.split.last()).also { methods.add(it) } else getMethod(line.split[1])
                            if (method == null)
                                println("Method ${line.split[1]} in ${levels[line.indent - 1]!!.intermediaryName} does not have intermediary name! Skipping!")
                            if (line.split.size == 4)
                                method?.apply {
                                    mappedName = line.split[2]
                                }
                        }
                    }
                } else if (line.type == MappingsType.FIELD) {
                    if (levels[line.indent - 1] == null)
                        println("Class of ${line.split[1]} does not have intermediary name! Skipping!")
                    else {
                        levels[line.indent - 1]!!.apply {
                            val field = getField(line.split[1])
                            if (field == null)
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
    val split: List<String> by lazy { text.replace("\t", "").split(" ") }
}