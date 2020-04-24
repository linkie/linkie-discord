package me.shedaniel.linkie.web.service

import me.shedaniel.linkie.*
import me.shedaniel.linkie.accesswidener.AccessWidenerResolver
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.*
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URL

@RestController
@Suppress("unused")
class LinkieController {
    private var yarnVersion: Pair<String, Long>? = null
    private var loaderVersion: Pair<String, Long>? = null
    private var fabricVersion: Pair<String, Long>? = null

    @RequestMapping("/fabric/1.8.9")
    fun fabric1_8_9(): String = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Fabric Versions 1.8.9</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.14.2/styles/default.min.css">
</head>
<body>
    <h1>Fabric 1.8.9 Latest Versions</h1>
    <a href="https://discord.gg/Nc28ABK"><img alt="Discord" src="https://img.shields.io/discord/679635419045822474.svg?label=discord"></a> 
    <img alt="GitHub stars" src="https://img.shields.io/github/stars/Legacy-Fabric/yarn.svg?label=Yarn&style=social"> 
    <img alt="GitHub stars" src="https://img.shields.io/github/stars/Legacy-Fabric/fabric.svg?label=Fabric&style=social">

    <h3>build.gradle</h3>
    <div name="code">
    <pre><code class="gradle">dependencies {
    compile("com.google.guava:guava:23.5-jre")
    minecraft("com.mojang:minecraft:{minecraft_version}")
    mappings("net.fabricmc:yarn:{yarn_version}:v2")
    modImplementation("net.fabricmc:fabric-loader-1.8.9:{loader_version}") {
        exclude module: "guava"
    }
            
    //Fabric api
    modImplementation "{fabric_maven}{fabric_version}"
}</code></pre>
    </div>
    
    <h3>gradle.properties (Example Mod)</h3>
    <div name="code"><pre><code class="properties">minecraft_version={minecraft_version}
yarn_mappings={yarn_version}
loader_version={loader_version}

#Fabric api
fabric_version={fabric_version}</code></pre></div>
</body>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.14.2/highlight.min.js"></script>
<script charset="UTF-8" src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/gradle.min.js"></script>
<script>
    document.addEventListener('DOMContentLoaded', (event) => {
        document.querySelectorAll('pre code').forEach((block) => {
            hljs.highlightBlock(block);
        });
    });
</script>
</html>
    """.trimIndent()
            .replace("{minecraft_version}", "1.8.9")
            .replace("{yarn_version}",
                    yarnVersion?.takeIf { System.currentTimeMillis() - it.second < 60000 }.let {
                        it ?: Pair(URL("https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven/net/fabricmc/yarn/maven-metadata.xml").readText().let {
                            it.substring(it.indexOf("<latest>") + "<latest>".length, it.indexOf("</latest>"))
                        }, System.currentTimeMillis())
                    }.apply { yarnVersion = this }.first
            )
            .replace("{loader_version}",
                    loaderVersion?.takeIf { System.currentTimeMillis() - it.second < 60000 }.let {
                        it ?: Pair(URL("https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven/net/fabricmc/fabric-loader-1.8.9/maven-metadata.xml")
                                .readText().let {
                                    it.substring(it.indexOf("<latest>") + "<latest>".length, it.indexOf("</latest>"))
                                }, System.currentTimeMillis())
                    }.apply { loaderVersion = this }.first
            )
            .replace("{fabric_maven}", "net.fabricmc.fabric-api:fabric-api:")
            .replace("{fabric_version}",
                    fabricVersion?.takeIf { System.currentTimeMillis() - it.second < 60000 }.let {
                        it ?: Pair(URL("https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml").readText().let {
                            it.substring(it.indexOf("<latest>") + "<latest>".length, it.indexOf("</latest>"))
                        }, System.currentTimeMillis())
                    }.apply { fabricVersion = this }.first
            )

    @RequestMapping("/accesswidener/{version}", method = [RequestMethod.GET])
    fun accessWidener(@PathVariable version: String): ResponseEntity<Resource> {
        if (YarnNamespace.getProvider(version).isEmpty()) {
            throw IllegalArgumentException("Illegal Version: $version")
        }
        val bytes = ByteArrayResource(AccessWidenerResolver.resolveVersion(version).toString().toByteArray())
        return ResponseEntity.ok()
                .headers(
                        HttpHeaders().also {
                            it.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=all-$version.accesswidener")
                            it.add("Cache-Control", "no-cache, no-store, must-revalidate")
                            it.add("Pragma", "no-cache")
                            it.add("Expires", "0")
                        }
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.contentLength())
                .body(bytes)
    }

    @GetMapping("/namespaces")
    fun namespaces(): List<String> = Namespaces.namespaces.keys.toList()

    @GetMapping("/namespace/{id}")
    fun namespaceInfo(@PathVariable id: String): NamespaceInfo {
        val namespace = Namespaces[id.toLowerCase()]
        val defaultVersion = namespace.getDefaultVersion(null, null)
        return NamespaceInfo(
                namespace.id,
                namespace.id.capitalize(),
                namespace.getAllSortedVersions().map {
                    Version(it, it.tryToVersion()?.toString())
                },
                Version(
                        defaultVersion,
                        defaultVersion.tryToVersion()?.toString()
                ),
                namespace.getMaximumCachedVersion(),
                namespace.supportsMixin(),
                namespace.supportsAT(),
                namespace.supportsAW()
        )
    }

    @GetMapping("/namespace/{id}/{version}/class")
    fun namespaceInfo(@PathVariable id: String, @PathVariable version: String, @RequestParam(value = "limit", defaultValue = "100") limitString: String, @RequestParam(value = "skip", defaultValue = "0") skipString: String, @RequestParam(value = "search", required = false) search: String?): ClassQuery {
        val namespace = Namespaces[id.toLowerCase()]
        val mappingsProvider = namespace.getProvider(version)
        if (mappingsProvider.isEmpty()) throw NullPointerException("Invalid Version: $version")
        val limit = limitString.toInt()
        val skip = skipString.toInt()
        if (limit <= 0) throw IllegalArgumentException("Invalid Limit: $limit")
        if (skip < 0) throw IllegalArgumentException("Invalid Skip: $skip")
        val container = mappingsProvider.mappingsContainer!!.invoke()
        val classes = mutableMapOf<Class, MatchResult>()
        val searchKey = search?.replace('.', '/')
        container.classes.forEach { clazz ->
            if (!classes.contains(clazz)) {
                if (searchKey == null) {
                    classes[clazz] = MatchResult(true, null, null)
                    return@forEach
                }
                if (clazz.intermediaryName.containsOrMatchWildcard(searchKey).takeIf { it.matched }?.also { classes[clazz] = it }?.matched != true) {
                    if (clazz.mappedName.containsOrMatchWildcard(searchKey).takeIf { it.matched }?.also { classes[clazz] = it }?.matched != true) {
                        if (clazz.obfName.client.containsOrMatchWildcard(searchKey).takeIf { it.matched }?.also { classes[clazz] = it }?.matched != true) {
                            if (clazz.obfName.server.containsOrMatchWildcard(searchKey).takeIf { it.matched }?.also { classes[clazz] = it }?.matched != true) {
                                clazz.obfName.merged.containsOrMatchWildcard(searchKey).takeIf { it.matched }?.also { classes[clazz] = it }
                            }
                        }
                    }
                }
            }
        }
        val sortedClasses = classes.entries.sortedByDescending { it.value.selfTerm?.similarityOnNull(it.value.matchStr) }.map { it.key }
        return ClassQuery(
                container.mappingSource,
                sortedClasses.dropAndTake(skip, limit).map { it.toInfo(container) },
                limit,
                skip,
                sortedClasses.size
        )
    }

    open class NamespaceInfo(
            val id: String,
            val name: String,
            val versions: List<Version>,
            val latestVersion: Version,
            val maximumCachedVersionsCount: Int,
            val supportsMixin: Boolean,
            val supportsAT: Boolean,
            val supportsAW: Boolean
    )

    open class Version(
            val version: String,
            val semver: String?
    )

    open class ClassQuery(
            val source: MappingsContainer.MappingSource?,
            val classes: List<ClassInfo>,
            val limit: Int,
            val skipped: Int,
            val resultSize: Int
    )

    open class ClassInfo(
            val intermediaryName: String,
            val obfName: Obf,
            val mappedName: String?,
            val methods: List<MethodInfo>,
            val fields: List<FieldInfo>
    )

    open class FieldInfo(
            val intermediaryName: String,
            val intermediaryDescription: String,
            val obfName: Obf,
            val obfDescription: Obf,
            val mappedName: String?,
            val mappedDescription: String?
    )

    open class MethodInfo(
            val intermediaryName: String,
            val intermediaryDescription: String,
            val obfName: Obf,
            val obfDescription: Obf,
            val mappedName: String?,
            val mappedDescription: String?
    )

    open class Obf(
            var client: String? = null,
            var server: String? = null,
            var merged: String? = null
    )

    fun me.shedaniel.linkie.Obf.toObf(): Obf = Obf(client, server, merged)

    fun Class.toInfo(container: MappingsContainer): ClassInfo =
            ClassInfo(
                    intermediaryName,
                    obfName.toObf(),
                    mappedName,
                    methods.map { it.toInfo(container) },
                    fields.map { it.toInfo(container) }
            )

    fun Method.toInfo(container: MappingsContainer): MethodInfo =
            MethodInfo(
                    intermediaryName,
                    intermediaryDesc,
                    obfName.toObf(),
                    Obf(
                            obfDesc.client ?: if (!obfDesc.isMerged())
                                intermediaryDesc.remapMethodDescriptor { container.getClass(it)?.obfName?.client ?: it } else null,
                            obfDesc.server ?: if (!obfDesc.isMerged())
                                intermediaryDesc.remapMethodDescriptor { container.getClass(it)?.obfName?.server ?: it } else null,
                            obfDesc.merged ?: if (obfDesc.isMerged())
                                intermediaryDesc.remapMethodDescriptor { container.getClass(it)?.obfName?.merged ?: it } else null
                    ),
                    mappedName,
                    (mappedDesc ?: intermediaryDesc.remapMethodDescriptor { container.getClass(it)?.mappedName ?: it }).takeIf { it != intermediaryDesc }
            )

    fun Field.toInfo(container: MappingsContainer): FieldInfo =
            FieldInfo(
                    intermediaryName,
                    intermediaryDesc,
                    obfName.toObf(),
                    Obf(
                            obfDesc.client ?: if (!obfDesc.isMerged())
                                intermediaryDesc.remapFieldDescriptor { container.getClass(it)?.obfName?.client ?: it } else null,
                            obfDesc.server ?: if (!obfDesc.isMerged())
                                intermediaryDesc.remapFieldDescriptor { container.getClass(it)?.obfName?.server ?: it } else null,
                            obfDesc.merged ?: if (obfDesc.isMerged())
                                intermediaryDesc.remapFieldDescriptor { container.getClass(it)?.obfName?.merged ?: it } else null
                    ),
                    mappedName,
                    (mappedDesc ?: intermediaryDesc.remapFieldDescriptor { container.getClass(it)?.mappedName ?: it }).takeIf { it != intermediaryDesc }
            )
}