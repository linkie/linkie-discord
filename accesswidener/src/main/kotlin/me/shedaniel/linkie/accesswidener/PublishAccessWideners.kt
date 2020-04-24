@file:JvmName("PublishAccessWideners")

package me.shedaniel.linkie.accesswidener

import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.tryToVersion
import org.kohsuke.github.GitHubBuilder

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
    release.body.split("\n").forEach {
        if (it.isBlank() || it.startsWith("# ") || it.startsWith("## ")) return@forEach
        val version = it.substring(0, it.indexOf(": "))
        val link = it.substring(version.length + 2)
        finishedBuilds[version] = link
        builds.removeIf { it.second == version }
    }
    val sortedBuildsToBuild = builds.sortedWith(Comparator.nullsFirst(compareBy { it.first.tryToVersion() })).asReversed()
    sortedBuildsToBuild.forEach { version ->
        System.gc()
        val aw = AccessWidenerResolver.resolveVersion(version.first).toString()
        val downloadUrl = release.uploadAsset("all-$version.accesswidener", aw.byteInputStream(), "application/octet-stream").browserDownloadUrl
        System.gc()
        finishedBuilds[version.second] = downloadUrl
        changed = true
    }
    if (changed) {
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
}