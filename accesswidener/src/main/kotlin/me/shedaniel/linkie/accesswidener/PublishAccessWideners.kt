@file:JvmName("PublishAccessWideners")

package me.shedaniel.linkie.accesswidener

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    val github = GitHubBuilder().withOAuthToken(System.getenv("repo-token")).build()
    val repository = github.getRepository("shedaniel/LinkieBot")
    val release = repository.latestRelease

    val allPattern = Pattern.compile("^all-\\.(.*)(?:\\.\\1)(\\+?b?u?i?l?d?\\.\\d{1,10}).accesswidener")
    val safePattern = Pattern.compile("^safe-\\.(.*)(?:\\.\\1)(\\+?b?u?i?l?d?\\.\\d{1,10}).accesswidener")

    val allFinishedBuilds = mutableMapOf<Pair<String, String>, String>()
    val allBuilds = YarnNamespace.yarnBuilds.map { Pair(it.key, it.value.version) }.toMutableList()
    val safeFinishedBuilds = mutableMapOf<Pair<String, String>, String>()
    val safeBuilds = YarnNamespace.yarnBuilds.map { Pair(it.key, it.value.version) }.toMutableList()

    release.assets.forEach { asset ->
        val allMatches = allPattern.matcher(asset.name)
        if (allMatches.matches()) {
            allFinishedBuilds[Pair(allMatches.group(1), allMatches.group(1) + allMatches.group(2))] = asset.browserDownloadUrl
            allBuilds.removeIf { it.first == allMatches.group(1) && it.second == allMatches.group(1) + allMatches.group(2) }
        } else {
            val safeMatches = safePattern.matcher(asset.name)
            if (safeMatches.matches()) {
                safeFinishedBuilds[Pair(safeMatches.group(1), safeMatches.group(1) + safeMatches.group(2))] = asset.browserDownloadUrl
                safeBuilds.removeIf { it.first == safeMatches.group(1) && it.second == safeMatches.group(1) + safeMatches.group(2) }
            }
        }
    }
    val sortedAllBuildsToBuild = allBuilds.sortedWith(Comparator.nullsFirst(compareBy { it.first.tryToVersion() })).asReversed()
    val sortedSafeBuildsToBuild = safeBuilds.sortedWith(Comparator.nullsFirst(compareBy { it.first.tryToVersion() })).asReversed()

    val versionJsonMap = mutableMapOf<String, String>()
    versionJsonMap.clear()
    val versionManifest = YarnNamespace.json.parseJson(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").readText())
    versionManifest.jsonObject["versions"]!!.jsonArray.forEach { versionElement ->
        val versionString = versionElement.jsonObject["id"]!!.content
        versionString.tryToVersion() ?: return@forEach
        val urlString = versionElement.jsonObject["url"]!!.content
        versionJsonMap[versionString] = urlString
    }

    val allContext = Semaphore(7)
    runBlocking {
        sortedAllBuildsToBuild.map { version ->
            GlobalScope.launch {
                allContext.withPermit {
                    System.gc()
                    if (version.first.contains("combat") || version.first.contains("infinite") || version.first.contains("Shareware") || version.first.contains("Pre-Release"))
                        return@withPermit
                    try {
                        val aw = AccessWidenerResolver.resolveVersion(version.first, versionJsonMap = versionJsonMap).toString()
                        val downloadUrl = release.uploadAsset("all-$version.accesswidener", aw.byteInputStream(), "application/octet-stream").browserDownloadUrl
                        allFinishedBuilds[version] = downloadUrl
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    System.gc()
                }
            }
        }.joinAll()
    }
    runBlocking {
        val safeContext = Semaphore(7)
        sortedSafeBuildsToBuild.map { version ->
            GlobalScope.launch {
                safeContext.withPermit {
                    System.gc()
                    if (version.first.contains("combat") || version.first.contains("infinite") || version.first.contains("Shareware") || version.first.contains("Pre-Release"))
                        return@withPermit
                    try {
                        val aw = AccessWidenerResolver.resolveVersion(version.first, safe = true, versionJsonMap = versionJsonMap).toString()
                        val downloadUrl = release.uploadAsset("safe-$version.accesswidener", aw.byteInputStream(), "application/octet-stream").browserDownloadUrl
                        safeFinishedBuilds[version] = downloadUrl
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    System.gc()
                }
            }
        }.joinAll()
    }

    val allSortedFinishedBuilds = allFinishedBuilds.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
    val safeSortedFinishedBuilds = safeFinishedBuilds.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })

    val builder = StringBuilder()
    builder.append("# Everything Access Widener™\n")
    allSortedFinishedBuilds.keys.toMutableList().also { it.addAll(safeFinishedBuilds.keys) }.distinctBy { it.first }.sortedWith(Comparator.nullsFirst(compareBy { it.first.tryToVersion() })).asReversed().forEach { (mcVersion, _) ->
        builder.append("\n### $mcVersion\n")
        safeSortedFinishedBuilds.filterKeys { it.first == mcVersion }.entries.reversed().forEach { (versionPair, link) ->
            builder.append("Safe ${versionPair.second}: $link\n")
        }
        allSortedFinishedBuilds.filterKeys { it.first == mcVersion }.entries.reversed().forEach { (versionPair, link) ->
            builder.append("All ${versionPair.second}: $link\n")
        }
    }
    release.update().body(builder.toString()).update()
}