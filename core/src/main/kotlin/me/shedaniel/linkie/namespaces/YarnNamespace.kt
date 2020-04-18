package me.shedaniel.linkie.namespaces

import kotlinx.serialization.builtins.list
import me.shedaniel.linkie.*
import org.apache.commons.lang3.StringUtils
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.collections.LinkedHashMap
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.getOrPut
import kotlin.collections.last
import kotlin.collections.lastOrNull
import kotlin.collections.map
import kotlin.collections.max
import kotlin.collections.maxBy
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set

object YarnNamespace : Namespace("yarn") {
    private val yarnBuilds = mutableMapOf<String, YarnBuild>()
    private var yarnBuild1_8_9 = ""

    init {
        YarnV2BlackList.loadData()
        registerProvider({ it == "1.2.5" }) {
            MappingsContainer(it).apply {
                println("Loading yarn for $version")
                classes.clear()
                loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/1.2.5%20Merge.tiny"))
                loadNamedFromGithubRepo("Blayyke/yarn", "1.2.5", showError = false)
                mappingSource = MappingsContainer.MappingSource.ENGIMA
            }
        }
        registerProvider({ it == "1.8.9" }) {
            MappingsContainer(it).apply {
                println("Loading yarn for $version")
                classes.clear()
                loadIntermediaryFromMaven(version, repo = "https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven")
                loadNamedFromMaven(yarnVersion = yarnBuild1_8_9, repo = "https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven", showError = false)
            }
        }
        registerProvider({ it in yarnBuilds.keys }) {
            MappingsContainer(it).apply {
                println("Loading yarn for $version")
                classes.clear()
                loadIntermediaryFromMaven(version)
                val yarnMaven = yarnBuilds[version]!!.maven
                loadNamedFromMaven(yarnMaven.substring(yarnMaven.lastIndexOf(':') + 1), showError = false)
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> {
        val versions = mutableListOf<String>()
        val latestVersion = getDefaultVersion(null, null)
        yarnBuilds.keys.firstOrNull { it.contains('.') && !it.contains('-') }?.takeIf { it != latestVersion }?.also { versions.add(it) }
        latestVersion.also { versions.add(it) }
        return versions
    }

    override fun getAllVersions(): List<String> {
        val versions = mutableListOf(
                "1.2.5", "1.8.9"
        )
        versions.addAll(yarnBuilds.keys)
        return versions
    }

    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true

    override fun reloadData() {
        val buildMap = LinkedHashMap<String, MutableList<YarnBuild>>()
        json.parse(YarnBuild.serializer().list, URL("https://meta.fabricmc.net/v2/versions/yarn").readText()).forEach { buildMap.getOrPut(it.gameVersion, { mutableListOf() }).add(it) }
        buildMap.forEach { (version, builds) -> builds.maxBy { it.build }?.apply { yarnBuilds[version] = this } }
        val pom189 = URL("https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven/net/fabricmc/yarn/maven-metadata.xml").readText()
        yarnBuild1_8_9 = pom189.substring(pom189.indexOf("<latest>") + "<latest>".length, pom189.indexOf("</latest>"))
    }

    override fun getDefaultVersion(command: String?, channelId: Long?): String =
            when (channelId) {
                602959845842485258 -> "1.2.5"
                661088839464386571 -> "1.14.4"
                else -> yarnBuilds.keys.first { it.contains('.') && !it.contains('-') }
            }

    private fun MappingsContainer.loadIntermediaryFromMaven(
            mcVersion: String,
            repo: String = "https://maven.fabricmc.net",
            group: String = "net.fabricmc.intermediary"
    ) =
            loadIntermediaryFromTinyJar(URL("$repo/${group.replace('.', '/')}/$mcVersion/intermediary-$mcVersion.jar"))

    private fun MappingsContainer.loadIntermediaryFromTinyJar(url: URL) {
        val stream = ZipInputStream(url.openStream())
        while (true) {
            val entry = stream.nextEntry ?: break
            if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "mappings.tiny") {
                loadIntermediaryFromTinyInputStream(stream)
                break
            }
        }
    }

    internal fun MappingsContainer.loadIntermediaryFromTinyFile(url: URL) {
        loadIntermediaryFromTinyInputStream(url.openStream())
    }

    private fun MappingsContainer.loadIntermediaryFromTinyInputStream(stream: InputStream) {
        val mappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false)
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

    private fun MappingsContainer.loadNamedFromMaven(
            yarnVersion: String,
            repo: String = "https://maven.fabricmc.net",
            group: String = "net.fabricmc.yarn",
            showError: Boolean = true
    ) {
        mappingSource = if (YarnV2BlackList.blacklist.contains(yarnVersion)) {
            loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/yarn-$yarnVersion.jar"), showError)
            MappingsContainer.MappingSource.YARN_V1
        } else {
            try {
                loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/yarn-$yarnVersion-v2.jar"), showError)
                MappingsContainer.MappingSource.YARN_V2
            } catch (t: Throwable) {
                loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/yarn-$yarnVersion.jar"), showError)
                MappingsContainer.MappingSource.YARN_V1
            }
        }
    }

    private fun MappingsContainer.loadNamedFromTinyJar(url: URL, showError: Boolean = true) {
        val stream = ZipInputStream(url.openStream())
        while (true) {
            val entry = stream.nextEntry ?: break
            if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "mappings.tiny") {
                loadNamedFromTinyInputStream(stream, showError)
                break
            }
        }
    }

    private fun MappingsContainer.loadNamedFromTinyFile(url: URL, showError: Boolean = true) {
        loadNamedFromTinyInputStream(url.openStream(), showError)
    }

    private fun MappingsContainer.loadNamedFromTinyInputStream(stream: InputStream, showError: Boolean = true) {
        val mappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false)
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

    internal fun MappingsContainer.loadNamedFromGithubRepo(repo: String, branch: String, showError: Boolean = true, ignoreError: Boolean = false) =
            loadNamedFromEngimaZip(URL("https://github.com/$repo/archive/$branch.zip"), showError, ignoreError)

    private fun MappingsContainer.loadNamedFromEngimaZip(url: URL, showError: Boolean = true, ignoreError: Boolean = false) =
            loadNamedFromEngimaStream(url.openStream(), showError, ignoreError)

    private fun MappingsContainer.loadNamedFromEngimaStream(stream: InputStream, showError: Boolean = true, ignoreError: Boolean = false) {
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

    private enum class MappingsType {
        CLASS,
        FIELD,
        METHOD,
        UNKNOWN;

        companion object {
            fun getByString(string: String): MappingsType =
                    values().firstOrNull { it.name.equals(string, true) } ?: UNKNOWN
        }
    }

    private object YarnV2BlackList {
        val blacklist: MutableList<String> = mutableListOf()
        val blacklistString = """
        1.14 Pre-Release 1+build.10
        1.14 Pre-Release 1+build.11
        1.14 Pre-Release 1+build.12
        1.14 Pre-Release 1+build.2
        1.14 Pre-Release 1+build.3
        1.14 Pre-Release 1+build.4
        1.14 Pre-Release 1+build.5
        1.14 Pre-Release 1+build.6
        1.14 Pre-Release 1+build.7
        1.14 Pre-Release 1+build.8
        1.14 Pre-Release 1+build.9
        1.14 Pre-Release 2+build.1
        1.14 Pre-Release 2+build.10
        1.14 Pre-Release 2+build.11
        1.14 Pre-Release 2+build.12
        1.14 Pre-Release 2+build.13
        1.14 Pre-Release 2+build.14
        1.14 Pre-Release 2+build.2
        1.14 Pre-Release 2+build.3
        1.14 Pre-Release 2+build.4
        1.14 Pre-Release 2+build.5
        1.14 Pre-Release 2+build.6
        1.14 Pre-Release 2+build.7
        1.14 Pre-Release 2+build.8
        1.14 Pre-Release 2+build.9
        1.14 Pre-Release 3+build.1
        1.14 Pre-Release 3+build.2
        1.14 Pre-Release 3+build.3
        1.14 Pre-Release 3+build.4
        1.14 Pre-Release 4+build.1
        1.14 Pre-Release 4+build.2
        1.14 Pre-Release 4+build.3
        1.14 Pre-Release 4+build.4
        1.14 Pre-Release 4+build.5
        1.14 Pre-Release 4+build.6
        1.14 Pre-Release 4+build.7
        1.14 Pre-Release 5+build.1
        1.14 Pre-Release 5+build.2
        1.14 Pre-Release 5+build.3
        1.14 Pre-Release 5+build.4
        1.14 Pre-Release 5+build.5
        1.14 Pre-Release 5+build.6
        1.14 Pre-Release 5+build.7
        1.14 Pre-Release 5+build.8
        1.14+build.1
        1.14+build.10
        1.14+build.11
        1.14+build.12
        1.14+build.13
        1.14+build.14
        1.14+build.15
        1.14+build.16
        1.14+build.17
        1.14+build.18
        1.14+build.19
        1.14+build.2
        1.14+build.20
        1.14+build.21
        1.14+build.3
        1.14+build.4
        1.14+build.5
        1.14+build.6
        1.14+build.7
        1.14+build.8
        1.14+build.9
        1.14.1 Pre-Release 1+build.1
        1.14.1 Pre-Release 1+build.2
        1.14.1 Pre-Release 1+build.3
        1.14.1 Pre-Release 1+build.4
        1.14.1 Pre-Release 1+build.5
        1.14.1 Pre-Release 1+build.6
        1.14.1 Pre-Release 2+build.1
        1.14.1 Pre-Release 2+build.2
        1.14.1 Pre-Release 2+build.3
        1.14.1 Pre-Release 2+build.4
        1.14.1 Pre-Release 2+build.5
        1.14.1 Pre-Release 2+build.6
        1.14.1+build.1
        1.14.1+build.10
        1.14.1+build.2
        1.14.1+build.3
        1.14.1+build.4
        1.14.1+build.5
        1.14.1+build.6
        1.14.1+build.7
        1.14.1+build.8
        1.14.1+build.9
        1.14.2 Pre-Release 1+build.1
        1.14.2 Pre-Release 2+build.1
        1.14.2 Pre-Release 2+build.2
        1.14.2 Pre-Release 2+build.3
        1.14.2 Pre-Release 2+build.4
        1.14.2 Pre-Release 2+build.5
        1.14.2 Pre-Release 2+build.6
        1.14.2 Pre-Release 3+build.2
        1.14.2 Pre-Release 3+build.3
        1.14.2 Pre-Release 4+build.1
        1.14.2+build.1
        1.14.2+build.2
        1.14.2+build.3
        1.14.2+build.4
        1.14.2+build.5
        1.14.2+build.6
        1.14.2+build.7
        1.14.3+build.1
        1.14.3+build.10
        1.14.3+build.11
        1.14.3+build.12
        1.14.3+build.13
        1.14.3+build.2
        1.14.3+build.3
        1.14.3+build.4
        1.14.3+build.5
        1.14.3+build.6
        1.14.3+build.7
        1.14.3+build.8
        1.14.3-pre1+build.1
        1.14.3-pre1+build.2
        1.14.3-pre1+build.3
        1.14.3-pre1+build.4
        1.14.3-pre1+build.5
        1.14.3-pre1+build.6
        1.14.3-pre2+build.1
        1.14.3-pre2+build.10
        1.14.3-pre2+build.11
        1.14.3-pre2+build.12
        1.14.3-pre2+build.13
        1.14.3-pre2+build.14
        1.14.3-pre2+build.15
        1.14.3-pre2+build.16
        1.14.3-pre2+build.17
        1.14.3-pre2+build.18
        1.14.3-pre2+build.2
        1.14.3-pre2+build.3
        1.14.3-pre2+build.4
        1.14.3-pre2+build.5
        1.14.3-pre2+build.6
        1.14.3-pre2+build.7
        1.14.3-pre2+build.8
        1.14.3-pre2+build.9
        1.14.3-pre3+build.1
        1.14.3-pre4+build.1
        1.14.3-pre4+build.2
        1.14.3-pre4+build.3
        18w49a.1
        18w49a.10
        18w49a.11
        18w49a.12
        18w49a.13
        18w49a.14
        18w49a.15
        18w49a.16
        18w49a.17
        18w49a.18
        18w49a.2
        18w49a.20
        18w49a.21
        18w49a.22
        18w49a.3
        18w49a.4
        18w49a.5
        18w49a.6
        18w49a.7
        18w49a.8
        18w49a.9
        18w50a.1
        18w50a.10
        18w50a.100
        18w50a.11
        18w50a.12
        18w50a.13
        18w50a.14
        18w50a.15
        18w50a.16
        18w50a.17
        18w50a.18
        18w50a.19
        18w50a.2
        18w50a.20
        18w50a.21
        18w50a.22
        18w50a.23
        18w50a.24
        18w50a.25
        18w50a.26
        18w50a.27
        18w50a.28
        18w50a.29
        18w50a.3
        18w50a.30
        18w50a.31
        18w50a.32
        18w50a.33
        18w50a.34
        18w50a.35
        18w50a.36
        18w50a.37
        18w50a.38
    """.trimIndent()

        fun loadData() {
            blacklistString.split('\n').forEach { blacklist.add(it) }
        }
    }
}