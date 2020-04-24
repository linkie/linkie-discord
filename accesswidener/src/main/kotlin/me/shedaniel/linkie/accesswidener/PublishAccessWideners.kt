@file:JvmName("PublishAccessWideners")

package me.shedaniel.linkie.accesswidener

import kotlinx.serialization.json.content
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.tryToVersion
import org.kohsuke.github.GitHubBuilder
import java.net.URL
import java.util.regex.Pattern

fun main() {
    arrayOf("pomf", "spigot", "mcp").forEach {
        Namespaces.namespaces.remove(it)
    }
    Namespaces.startLoop()
    Thread.sleep(4000)
    var changed = false
    val finishedBuilds = mutableMapOf<String, String>()
    val builds = YarnNamespace.yarnBuilds.map { Pair(it.key, it.value.version) }.toMutableList()
    val github = GitHubBuilder().withOAuthToken(System.getenv("repo-token")).build()
    val repository = github.getRepository("shedaniel/LinkieBot")
    val release = repository.latestRelease
    val pattern = Pattern.compile("^all-\\.(.*)(?:\\.\\1)(\\+?b?u?i?l?d?\\.\\d{1,10}).accesswidener")
    release.assets.forEach { asset ->
        val matches = pattern.matcher(asset.name)
        if (matches.matches()) {
            finishedBuilds[matches.group(1) + matches.group(2)] = asset.browserDownloadUrl
            builds.removeIf { it.first == matches.group(1) }
        }
    }
    val sortedBuildsToBuild = builds.sortedWith(Comparator.nullsFirst(compareBy { it.first.tryToVersion() })).asReversed()
    val versionJsonMap = mutableMapOf<String, String>()
    versionJsonMap.clear()
    val versionManifest = YarnNamespace.json.parseJson(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").readText())
    versionManifest.jsonObject["versions"]!!.jsonArray.forEach { versionElement ->
        val versionString = versionElement.jsonObject["id"]!!.content
        versionString.tryToVersion() ?: return@forEach
        val urlString = versionElement.jsonObject["url"]!!.content
        versionJsonMap[versionString] = urlString
    }
    sortedBuildsToBuild.forEach { version ->
        System.gc()
        if (version.first.contains("combat") || version.first.contains("infinite") || version.first.contains("Shareware") || version.first.contains("Pre-Release"))
            return@forEach
        try {
            val aw = AccessWidenerResolver.resolveVersion(version.first, versionJsonMap).toString()
            val downloadUrl = release.uploadAsset("all-$version.accesswidener", aw.byteInputStream(), "application/octet-stream").browserDownloadUrl
            finishedBuilds[version.second] = downloadUrl
            changed = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        System.gc()
    }
    val builder = StringBuilder()
    builder.append("# Everything Access Widener™\n")
    builds.forEach { (mcVersion, _) ->
        builder.append("\n## $mcVersion\n")
        finishedBuilds.forEach { (version, link) ->
            if (version.startsWith("$mcVersion+build.")) {
                builder.append("$version: $link\n")
            }
        }
    }
    release.update().body(builder.toString()).update()
}