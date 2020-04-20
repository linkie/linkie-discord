package me.shedaniel.linkie.web.service

import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Suppress("unused")
class LinkieController {
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
                    (mappedDesc ?: intermediaryDesc.remapMethodDescriptor { container.getClass(it)?.mappedName ?: it })?.takeIf { it != intermediaryDesc }
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
                    (mappedDesc ?: intermediaryDesc.remapFieldDescriptor { container.getClass(it)?.mappedName ?: it })?.takeIf { it != intermediaryDesc }
            )
}