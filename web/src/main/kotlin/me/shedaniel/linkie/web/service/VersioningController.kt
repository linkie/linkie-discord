package me.shedaniel.linkie.web.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.content
import me.shedaniel.cursemetaapi.CurseMetaAPI
import me.shedaniel.linkie.utils.tryToVersion
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URL

@RestController
@Suppress("unused")
class VersioningController {
    private val versions = mutableMapOf<String, VersionHolder>()

    private fun getAliasedVersion(string: String): VersionHolder? =
            when (string) {
                "latest-release" -> getVersion(versions.entries.first { it.value.release }.key)
                "latest" -> getVersion(versions.entries.first().key)
                else -> getVersion(string)
            }

    private fun getVersion(string: String): VersionHolder? = versions[string]

    @RequestMapping("version")
    fun version(@RequestParam(value = "version", defaultValue = "latest-release") version: String): String {
        var html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Fabric Versions</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.14.2/styles/default.min.css">
</head>
<body>
    <h1>Fabric Latest Versions</h1>
    <img alt="Discord" src="https://img.shields.io/discord/507304429255393322.svg?label=discord"> <img
        alt="GitHub stars" src="https://img.shields.io/github/stars/FabricMC/yarn.svg?label=Yarn&style=social"> <img 
        alt="GitHub stars" src="https://img.shields.io/github/stars/FabricMC/fabric.svg?label=Fabric&style=social">
        
    <p>Minecraft Version:
        <select name="versions" id="versions" onchange="updateSelection()">
        {options}
        </select>
    </p>
    
    <h3>build.gradle</h3>
    <div name="code">
    <pre><code class="gradle">dependencies {
    minecraft("com.mojang:minecraft:{minecraft_version}")
    mappings("net.fabricmc:yarn:{yarn_version}:v2")
    modImplementation("net.fabricmc:fabric-loader:{loader_version}"){apis}
}</code></pre>
    </div>
    
    <h3>gradle.properties (Example Mod)</h3>
    <div name="code"><pre><code class="properties">minecraft_version={minecraft_version}
yarn_mappings={yarn_version}
loader_version={loader_version}

fabric_version={fabric_version}</code></pre></div>

<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.14.2/highlight.min.js"></script>
<script charset="UTF-8" src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/gradle.min.js"></script>
<script>
    document.addEventListener('DOMContentLoaded', (event) => {
        document.querySelectorAll('pre code').forEach((block) => {
            hljs.highlightBlock(block);
        });
    });
    
    function updateSelection() {
        var versionList = document.getElementById("versions");
        insertParam("version", versionList.value);
    }
    
    //Thanks https://stackoverflow.com/questions/486896/adding-a-parameter-to-the-url-with-javascript
    function insertParam(key, value) {
        key = encodeURI(key); value = encodeURI(value);

        var kvp = document.location.search.substr(1).split('&');

        var i = kvp.length; var x; while (i--) {
            x = kvp[i].split('=');

            if (x[0] == key) {
                x[1] = value;
                kvp[i] = x.join('=');
                break;
            }
        }

        if (i < 0) { kvp[kvp.length] = [key, value].join('='); }

        //this will reload the page, it's likely better to store this until finished
        document.location.search = kvp.join('&');
    }
</script>
</body>
</html>
""".trimIndent()
        val versionHolder = getAliasedVersion(version) ?: getVersion(versions.entries.first { it.value.release }.key)!!
        html = html.replace("{options}", versions.keys.joinToString("") {
            if (it == versionHolder.version) "<option selected=\"selected\">$it</options>" else "<option>$it</options>"
        })
        run {
            var apis = ""
            fun addToApi(element: DependencyElement) {
                if (apis.isEmpty()) apis += "\n    "
                apis += "\n    "
                apis += "modImplementation(\"${element.dependencyMaven}:${element.dependencyVersion}\")"
            }

            var fabricVersion = "{fabric_version}"
            versionHolder.dependencyElements.forEach { (key, element) ->
                if (key == "fabric-api") {
                    fabricVersion = element.dependencyVersion
                }
                addToApi(element)
            }
            html = html.replace("{minecraft_version}", versionHolder.version)
                    .replace("{yarn_version}", versionHolder.yarnVersion)
                    .replace("{loader_version}", versionHolder.loaderVersion)
                    .replace("{fabric_version}", fabricVersion)
                    .replace("{apis}", apis)
        }
        return html
    }

    private data class VersionHolder(
            val version: String,
            val release: Boolean,
            val loaderVersion: String,
            val yarnVersion: String,
            val dependencyElements: MutableMap<String, DependencyElement> = mutableMapOf()
    )

    private data class DependencyElement(
            val dependencyVersion: String,
            val dependencyMaven: String,
            val sure: Boolean
    )

    val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true))

    init {
        val tickerChannel = ticker(delayMillis = 1800000, initialDelayMillis = 0)
        CoroutineScope(Dispatchers.Default).launch {
            for (event in tickerChannel) {
                val tmpVersions = mutableMapOf<String, VersionHolder>()
                run {
                    val meta = json.parseJson(URL("https://meta.fabricmc.net/v2/versions").readText()).jsonObject
                    val loaderVersion = meta["loader"]!!.jsonArray[0].jsonObject["version"]!!.content
                    val mappings = meta["mappings"]!!.jsonArray
                    meta["game"]!!.jsonArray.content.map(JsonElement::jsonObject).forEach { obj ->
                        val version = obj["version"]!!.content
                        val release = version.tryToVersion().let { it != null && it.snapshot == null }
                        val mappingsObj = mappings.firstOrNull { it.jsonObject["gameVersion"]!!.content == version }?.jsonObject ?: return@forEach
                        val yarnVersion = mappingsObj["version"]!!.content
                        tmpVersions[version] = VersionHolder(version, release, loaderVersion, yarnVersion)
                    }
                    fillFabricApi(tmpVersions)
                }
                versions.clear()
                versions.putAll(tmpVersions)
                System.gc()
            }
        }
    }

    private suspend fun fillFabricApi(versions: MutableMap<String, VersionHolder>) {
        val displayNameRegex = "\\[(.*)].*".toRegex()
        val fileNameRegex = "fabric(?:-api)?-(.*).jar".toRegex()
        val versionSplitRegex = "([^/]+)(./.*)".toRegex()
        val addonFiles: MutableMap<String?, MutableList<CurseMetaAPI.AddonFile>> = mutableMapOf()
        CurseMetaAPI.getAddonFiles(306612).forEach {
            val displayedVersion = displayNameRegex.matchEntire(it.displayName)?.groupValues?.get(1) ?: return@forEach
            val splitMatchResult = versionSplitRegex.matchEntire(displayedVersion)
            if (splitMatchResult == null) {
                addonFiles.computeIfAbsent(displayedVersion) { mutableListOf() }.add(it)
            } else {
                val allVersions = splitMatchResult.groupValues[2].split('/').map { 
                    if (it.length == 1) splitMatchResult.groupValues[1] + it else it
                }
                allVersions.forEach { version ->
                    addonFiles.computeIfAbsent(version) { mutableListOf() }.add(it)
                }
            }
        }
        versions.forEach versionLoop@{ mcVersion, versionHolder ->
            if (versionHolder.dependencyElements.containsKey("fabric-api")) return@versionLoop
            val match = addonFiles[mcVersion]?.toMutableList()?.also { it.sortByDescending { it.fileId } }?.first()
            if (match != null) {
                versionHolder.dependencyElements["fabric-api"] = DependencyElement(
                        fileNameRegex.matchEntire(match.fileName)!!.groupValues[1],
                        when {
                            match.fileName.startsWith("fabric-api-") -> "net.fabricmc.fabric-api:fabric-api"
                            else -> "net.fabricmc:fabric"
                        },
                        true
                )
            }
        }
    }
}