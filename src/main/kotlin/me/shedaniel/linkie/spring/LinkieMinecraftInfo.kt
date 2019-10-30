package me.shedaniel.linkie.spring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import me.shedaniel.cursemetaapi.CurseMetaAPI
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val metaService = Executors.newScheduledThreadPool(16)!!
private val json = Json(JsonConfiguration.Stable.copy(strictMode = false))
var minecraftVersions: MutableList<MinecraftVersion> = ArrayList()
var loaderVersion: String? = null
var fabricApi: Map<String, Pair<CurseMetaAPI.AddonFile, Boolean>> = LinkedHashMap()

fun startInfoSync() {
    metaService.scheduleAtFixedRate({
        try {
            minecraftVersions.clear()
            minecraftVersions.addAll(json.parse(MinecraftVersion.serializer().list, URL("https://meta.fabricmc.net/v2/versions/game").readText()))
            println("Loaded ${minecraftVersions.size} MC Versions.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val list = json.parse(YarnBuild.serializer().list, URL("https://meta.fabricmc.net/v2/versions/yarn").readText())
            if (!list.isEmpty()) {
                minecraftVersions.forEach { version -> version.yarnMaven = null }
                for (build in list) {
                    for (minecraftVersion in minecraftVersions) {
                        if (minecraftVersion.version.equals(build.gameVersion, ignoreCase = true)) {
                            if (minecraftVersion.yarnMaven == null)
                                minecraftVersion.yarnMaven = build.maven
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            json.parse(LoaderBuild.serializer().list, URL("https://meta.fabricmc.net/v2/versions/loader").readText()).firstOrNull()?.apply {
                loaderVersion = maven
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val map = mutableMapOf<String, Pair<CurseMetaAPI.AddonFile, Boolean>>()
            val files = CurseMetaAPI.getAddonFiles(306612)
            files.sortByDescending { it.fileId }
            for (file in files) {
                val displayName = file.displayName
                if (displayName[0] == '[' && displayName.indexOf(']') > -1) {
                    var version = displayName.substring(1).split("]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                    if (version.contains("/"))
                        version = version.substring(0, version.indexOf("/"))
                    val isSnapshot = version.toLowerCase().contains("pre") || version.toLowerCase().startsWith("1.14_combat-") || version.toLowerCase().startsWith("19w") || version.toLowerCase().startsWith("20w") || version.toLowerCase().startsWith("18w") || version.toLowerCase().startsWith("21w")
                    if (!map.containsKey(version))
                        map[version] = Pair(file, !isSnapshot)
                }
            }
            if (!map.isEmpty()) {
                fabricApi = map
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }, 0, 15, TimeUnit.MINUTES)
}

@Serializable
data class MinecraftVersion(
        val version: String,
        val stable: Boolean,
        var yarnMaven: String? = null
)

@Serializable
data class YarnBuild(
        val gameVersion: String,
        val maven: String
)

@Serializable
data class LoaderBuild(
        val maven: String
)