package me.shedaniel.linkie.namespaces

import discord4j.core.`object`.util.Snowflake
import kotlinx.serialization.builtins.list
import me.shedaniel.linkie.*
import java.net.URL

object YarnNamespace : Namespace("yarn") {
    private val yarnBuilds = mutableMapOf<String, YarnBuild>()
    private var yarnBuild1_8_9 = ""

    init {
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

    override fun reloadData() {
        val buildMap = LinkedHashMap<String, MutableList<YarnBuild>>()
        json.parse(YarnBuild.serializer().list, URL("https://meta.fabricmc.net/v2/versions/yarn").readText()).forEach { buildMap.getOrPut(it.gameVersion, { mutableListOf() }).add(it) }
        buildMap.forEach { (version, builds) -> builds.maxBy { it.build }?.apply { yarnBuilds[version] = this } }
        val pom189 = URL("https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven/net/fabricmc/yarn/maven-metadata.xml").readText()
        yarnBuild1_8_9 = pom189.substring(pom189.indexOf("<latest>") + "<latest>".length, pom189.indexOf("</latest>"))
    }

    override fun getDefaultVersion(command: String?, snowflake: Snowflake?): String =
            when (snowflake?.asLong()) {
                602959845842485258 -> "1.2.5"
                661088839464386571 -> "1.14.3"
                else -> yarnBuilds.keys.first()
            }
}