package me.shedaniel.linkie.discord.utils

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.*

typealias ClassResultSequence = Sequence<ResultHolder<Class>>
typealias FieldResultSequence = Sequence<ResultHolder<Pair<Class, Field>>>
typealias MethodResultSequence = Sequence<ResultHolder<Pair<Class, Method>>>

object MappingsQuery {
    fun MappingsProvider.get(): MappingsContainer = mappingsContainer!!.invoke()

    private data class FieldResult(
        val parent: Class,
        val field: Field,
        val cm: QueryDefinition,
    )

    private data class MethodResult(
        val parent: Class,
        val method: Method,
        val cm: QueryDefinition,
    )

    private enum class QueryDefinition {
        INTERMEDIARY,
        MAPPED,
        OBF_CLIENT,
        OBF_SERVER,
        OBF_MERGED,
        WILDCARD
    }

    fun queryClasses(context: QueryContext): QueryResultCompound<ClassResultSequence> {
        val searchKey = context.searchKey
        val hasWildcard = searchKey == "*"

        val mappingsContainer = context.provider.get()
        val classes = mutableMapOf<Class, MatchResult>()

        mappingsContainer.classes.forEach { clazz ->
            if (hasWildcard) {
                classes[clazz] = MatchResult(true, null, null)
            } else if (!classes.contains(clazz)) {
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
        if (classes.entries.isEmpty()) {
            if (searchKey.onlyClass().firstOrNull()?.isDigit() == true && !searchKey.onlyClass().isValidIdentifier()) {
                throw NullPointerException("No results found! `${searchKey.onlyClass()}` is not a valid java identifier!")
            } else if (searchKey.startsWith("func_") || searchKey.startsWith("method_")) {
                throw NullPointerException("No results found! `$searchKey` looks like a method!")
            } else if (searchKey.startsWith("field_")) {
                throw NullPointerException("No results found! `$searchKey` looks like a field!")
            } else if ((!searchKey.startsWith("class_") && searchKey.firstOrNull()?.isLowerCase() == true) || searchKey.firstOrNull()?.isDigit() == true) {
                throw NullPointerException("No results found! `$searchKey` doesn't look like a class!")
            }
            throw NullPointerException("No results found!")
        }

        val sortedClasses: Sequence<ResultHolder<Class>> = when {
            hasWildcard -> classes.entries.asSequence().sortedBy { it.key.intermediaryName }.mapIndexed { index, entry -> entry.key hold (classes.entries.size - index + 1) * 100.0 }
            else -> classes.entries.asSequence().map { it.key hold (it.value.selfTerm?.similarityOnNull(it.value.matchStr) ?: 0.0) }
        }.sortedByDescending { it.score }
        return QueryResultCompound(mappingsContainer, sortedClasses)
    }

    fun queryFields(context: QueryContext): QueryResultCompound<FieldResultSequence> {
        val searchKey = context.searchKey
        val hasClass = searchKey.contains('/')
        val mappingsContainer = context.provider.get()
        val classes = mutableMapOf<Class, QueryDefinition>()

        if (hasClass) {
            val clazzKey = searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass()
            if (clazzKey == "*") {
                mappingsContainer.classes.forEach { classes[it] = QueryDefinition.WILDCARD }
            } else {
                mappingsContainer.classes.forEach { clazz ->
                    when {
                        clazz.intermediaryName.onlyClass().contains(clazzKey, true) -> classes[clazz] = QueryDefinition.INTERMEDIARY
                        clazz.mappedName?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = QueryDefinition.MAPPED
                        clazz.obfName.client?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = QueryDefinition.OBF_CLIENT
                        clazz.obfName.server?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = QueryDefinition.OBF_SERVER
                        clazz.obfName.merged?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = QueryDefinition.OBF_MERGED
                    }
                }
            }
        } else mappingsContainer.classes.forEach { classes[it] = QueryDefinition.WILDCARD }
        val addedFields = mutableSetOf<Field>()
        val fields = mutableMapOf<FieldResult, QueryDefinition>()
        val fieldKey = searchKey.onlyClass('/')
        if (fieldKey == "*") {
            classes.forEach { (parent, cm) ->
                parent.fields.forEach { field ->
                    if (addedFields.add(field))
                        fields[FieldResult(parent, field, cm)] = QueryDefinition.WILDCARD
                }
            }
        } else {
            classes.forEach { (clazz, cm) ->
                clazz.fields.forEach { field ->
                    if (addedFields.add(field))
                        when {
                            field.intermediaryName.contains(fieldKey, true) -> fields[FieldResult(clazz, field, cm)] = QueryDefinition.INTERMEDIARY
                            field.mappedName?.contains(fieldKey, true) == true -> fields[FieldResult(clazz, field, cm)] = QueryDefinition.MAPPED
                            field.obfName.client?.contains(fieldKey, true) == true -> fields[FieldResult(clazz, field, cm)] = QueryDefinition.OBF_CLIENT
                            field.obfName.server?.contains(fieldKey, true) == true -> fields[FieldResult(clazz, field, cm)] = QueryDefinition.OBF_SERVER
                            field.obfName.merged?.contains(fieldKey, true) == true -> fields[FieldResult(clazz, field, cm)] = QueryDefinition.OBF_MERGED
                        }
                }
            }
        }
        addedFields.clear()
        val sortedFields: Sequence<ResultHolder<FieldResult>> = when {
            fieldKey == "*" && (!hasClass || searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") -> {
                // Class and field both wildcard
                fields.entries.asSequence().sortedBy { it.key.field.intermediaryName }.sortedBy {
                    it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                }.mapIndexed { index, entry -> entry.key hold (classes.entries.size - index + 1) * 100.0 }
            }
            fieldKey == "*" -> {
                // Only field wildcard
                val classKey = searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass()
                fields.entries.asSequence().sortedBy { it.key.field.intermediaryName }.map {
                    it.key hold when (it.key.cm) {
                        QueryDefinition.MAPPED -> it.key.parent.mappedName!!.onlyClass()
                        QueryDefinition.OBF_CLIENT -> it.key.parent.obfName.client!!.onlyClass()
                        QueryDefinition.OBF_SERVER -> it.key.parent.obfName.server!!.onlyClass()
                        QueryDefinition.OBF_MERGED -> it.key.parent.obfName.merged!!.onlyClass()
                        QueryDefinition.INTERMEDIARY -> it.key.parent.intermediaryName.onlyClass()
                        else -> null
                    }.similarityOnNull(classKey)
                }
            }
            hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() != "*" -> {
                // has class
                fields.entries.asSequence().sortedByDescending {
                    when (it.value) {
                        QueryDefinition.MAPPED -> it.key.field.mappedName!!
                        QueryDefinition.OBF_CLIENT -> it.key.field.obfName.client!!
                        QueryDefinition.OBF_SERVER -> it.key.field.obfName.server!!
                        QueryDefinition.OBF_MERGED -> it.key.field.obfName.merged!!
                        else -> it.key.field.intermediaryName
                    }.onlyClass().similarity(fieldKey)
                }.sortedBy {
                    it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                }.mapIndexed { index, entry -> entry.key hold (classes.entries.size - index + 1) * 100.0 }
            }
            else -> {
                fields.entries.asSequence().sortedBy {
                    it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                }.map {
                    it.key hold when (it.value) {
                        QueryDefinition.MAPPED -> it.key.field.mappedName!!
                        QueryDefinition.OBF_CLIENT -> it.key.field.obfName.client!!
                        QueryDefinition.OBF_SERVER -> it.key.field.obfName.server!!
                        QueryDefinition.OBF_MERGED -> it.key.field.obfName.merged!!
                        else -> it.key.field.intermediaryName
                    }.onlyClass().similarity(fieldKey)
                }
            }
        }

        if (fields.entries.isEmpty()) {
            if (searchKey.onlyClass().firstOrNull()?.isDigit() == true && !searchKey.onlyClass().isValidIdentifier()) {
                throw NullPointerException("No results found! `${searchKey.onlyClass()}` is not a valid java identifier!")
            } else if (searchKey.startsWith("func_") || searchKey.startsWith("method_")) {
                throw NullPointerException("No results found! `$searchKey` looks like a method!")
            } else if (searchKey.startsWith("class_")) {
                throw NullPointerException("No results found! `$searchKey` looks like a class!")
            }
            throw NullPointerException("No results found!")
        }

        val result: FieldResultSequence = sortedFields.map {
            it.value.parent to it.value.field hold it.score
        }.sortedByDescending { it.score }
        return QueryResultCompound(mappingsContainer, result)
    }

    fun queryMethods(context: QueryContext): QueryResultCompound<MethodResultSequence> {
        val searchKey = context.searchKey
        val hasClass = searchKey.contains('/')

        val mappingsContainer = context.provider.get()
        val classes = mutableMapOf<Class, QueryDefinition>()
        if (hasClass) {
            val clazzKey = searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass()
            if (clazzKey == "*") {
                mappingsContainer.classes.forEach { classes[it] = QueryDefinition.WILDCARD }
            } else {
                mappingsContainer.classes.forEach { clazz ->
                    when {
                        clazz.intermediaryName.onlyClass().contains(clazzKey, true) -> classes[clazz] = QueryDefinition.INTERMEDIARY
                        clazz.mappedName?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = QueryDefinition.MAPPED
                        clazz.obfName.client?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = QueryDefinition.OBF_CLIENT
                        clazz.obfName.server?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = QueryDefinition.OBF_SERVER
                        clazz.obfName.merged?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = QueryDefinition.OBF_MERGED
                    }
                }
            }
        } else mappingsContainer.classes.forEach { classes[it] = QueryDefinition.WILDCARD }
        val addedMethods = mutableSetOf<Method>()
        val methods = mutableMapOf<MethodResult, QueryDefinition>()
        val methodKey = searchKey.onlyClass('/')
        if (methodKey == "*") {
            classes.forEach { (parent, cm) ->
                parent.methods.forEach { method ->
                    if (addedMethods.add(method))
                        methods[MethodResult(parent, method, cm)] = QueryDefinition.WILDCARD
                }
            }
        } else {
            classes.forEach { (parent, cm) ->
                parent.methods.forEach { method ->
                    if (addedMethods.add(method))
                        when {
                            method.intermediaryName.contains(methodKey, true) -> methods[MethodResult(parent, method, cm)] = QueryDefinition.INTERMEDIARY
                            method.mappedName?.contains(methodKey, true) == true -> methods[MethodResult(parent, method, cm)] = QueryDefinition.MAPPED
                            method.obfName.client?.contains(methodKey, true) == true -> methods[MethodResult(parent, method, cm)] = QueryDefinition.OBF_CLIENT
                            method.obfName.server?.contains(methodKey, true) == true -> methods[MethodResult(parent, method, cm)] = QueryDefinition.OBF_SERVER
                            method.obfName.merged?.contains(methodKey, true) == true -> methods[MethodResult(parent, method, cm)] = QueryDefinition.OBF_MERGED
                        }
                }
            }
        }
        addedMethods.clear()
        val sortedMethods: Sequence<ResultHolder<MethodResult>> = when {
            methodKey == "*" && (!hasClass || searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") -> {
                // Class and method both wildcard
                methods.entries.asSequence().sortedBy { it.key.method.intermediaryName }.sortedBy {
                    it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                }.mapIndexed { index, entry -> entry.key hold (classes.entries.size - index + 1) * 100.0 }
            }
            methodKey == "*" -> {
                // Only method wildcard
                val classKey = searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass()
                methods.entries.asSequence().sortedBy { it.key.method.intermediaryName }.map {
                    it.key hold when (it.key.cm) {
                        QueryDefinition.MAPPED -> it.key.parent.mappedName!!.onlyClass()
                        QueryDefinition.OBF_CLIENT -> it.key.parent.obfName.client!!.onlyClass()
                        QueryDefinition.OBF_SERVER -> it.key.parent.obfName.server!!.onlyClass()
                        QueryDefinition.OBF_MERGED -> it.key.parent.obfName.merged!!.onlyClass()
                        QueryDefinition.INTERMEDIARY -> it.key.parent.intermediaryName.onlyClass()
                        else -> null
                    }.similarityOnNull(classKey)
                }
            }
            hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() != "*" -> {
                // has class
                methods.entries.asSequence().sortedByDescending {
                    when (it.value) {
                        QueryDefinition.MAPPED -> it.key.method.mappedName!!
                        QueryDefinition.OBF_CLIENT -> it.key.method.obfName.client!!
                        QueryDefinition.OBF_SERVER -> it.key.method.obfName.server!!
                        QueryDefinition.OBF_MERGED -> it.key.method.obfName.merged!!
                        else -> it.key.method.intermediaryName
                    }.onlyClass().similarity(methodKey)
                }.sortedBy {
                    it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                }.mapIndexed { index, entry -> entry.key hold (classes.entries.size - index + 1) * 100.0 }
            }
            else -> {
                methods.entries.asSequence().sortedBy {
                    it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                }.map {
                    it.key hold when (it.value) {
                        QueryDefinition.MAPPED -> it.key.method.mappedName!!
                        QueryDefinition.OBF_CLIENT -> it.key.method.obfName.client!!
                        QueryDefinition.OBF_SERVER -> it.key.method.obfName.server!!
                        QueryDefinition.OBF_MERGED -> it.key.method.obfName.merged!!
                        else -> it.key.method.intermediaryName
                    }.onlyClass().similarity(methodKey)
                }
            }
        }

        if (methods.entries.isEmpty()) {
            if (searchKey.onlyClass().firstOrNull()?.isDigit() == true && !searchKey.onlyClass().isValidIdentifier()) {
                throw NullPointerException("No results found! `${searchKey.onlyClass()}` is not a valid java identifier!")
            } else if (searchKey.startsWith("class_")) {
                throw NullPointerException("No results found! `$searchKey` looks like a class!")
            } else if (searchKey.startsWith("field_")) {
                throw NullPointerException("No results found! `$searchKey` looks like a field!")
            }
            throw NullPointerException("No results found!")
        }

        val result: MethodResultSequence = sortedMethods.map {
            it.value.parent to it.value.method hold it.score
        }.sortedByDescending { it.score }
        return QueryResultCompound(mappingsContainer, result)
    }

    fun buildHeader(spec: EmbedCreateSpec, metadata: MappingsMetadata, page: Int, author: User, maxPage: Int) = spec.apply {
        if (metadata.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${metadata.mappingSource}", author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${metadata.name} Mappings for ${metadata.version} (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${metadata.name} Mappings for ${metadata.version}")
    }

    fun buildHeader(spec: EmbedCreateSpec, metadata: MappingsContainer, page: Int, author: User, maxPage: Int) = spec.apply {
        if (metadata.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${metadata.mappingSource}", author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${metadata.name} Mappings for ${metadata.version} (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${metadata.name} Mappings for ${metadata.version}")
    }

    fun buildClass(builder: StringBuilder, namespace: Namespace, classEntry: Class, mappings: MappingsMetadata) = builder.apply {
        appendLine("**Class: __${classEntry.optimumName}__**")
        append("__Name__: ")
        append(classEntry.obfName.buildString(nonEmptySuffix = " => "))
        append("`${classEntry.intermediaryName}`")
        append(classEntry.mappedName.mapIfNotNullOrNotEquals(classEntry.intermediaryName) { " => `$it`" } ?: "")
        if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible class ${classEntry.optimumName}`")
        } else if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${classEntry.intermediaryName.replace('/', '.')}`")
        }
    }

    fun buildField(builder: StringBuilder, namespace: Namespace, field: Field, parent: Class, mappings: MappingsContainer) = builder.apply {
        appendLine("**Field: ${parent.optimumName}#__${field.optimumName}__**")
        append("__Name__: ")
        append(field.obfName.buildString(nonEmptySuffix = " => "))
        append("`${field.intermediaryName}`")
        append(field.mappedName.mapIfNotNullOrNotEquals(field.intermediaryName) { " => `$it`" } ?: "")
        if (namespace.supportsFieldDescription()) {
            appendLine().append("__Type__: ")
            append((field.mappedDesc ?: field.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappings)).localiseFieldDesc())
        }
        if (namespace.supportsMixin()) {
            appendLine().append("__Mixin Target__: `")
            append("L${parent.optimumName};")
            append(field.optimumName)
            append(':')
            append(field.mappedDesc ?: field.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappings))
            append('`')
        }
        if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${parent.optimumName.replace('/', '.')} ")
            append(field.intermediaryName)
            append(" # ")
            append(field.optimumName)
            append('`')
        } else if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible field ")
            append(parent.optimumName)
            append(' ')
            append(field.optimumName)
            append(' ')
            append(field.mappedDesc ?: field.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappings))
            append('`')
        }
    }

    fun buildMethod(builder: StringBuilder, namespace: Namespace, method: Method, parent: Class, mappings: MappingsContainer) = builder.apply {
        appendLine("**Method: ${parent.optimumName}#__${method.optimumName}__**")
        append("__Name__: ")
        append(method.obfName.buildString(nonEmptySuffix = " => "))
        append("`${method.intermediaryName}`")
        append(method.mappedName.mapIfNotNullOrNotEquals(method.intermediaryName) { " => `$it`" } ?: "")
        if (namespace.supportsMixin()) {
            appendLine().append("__Mixin Target__: `")
            append("L${parent.optimumName};")
            append(method.optimumName)
            append(method.mappedDesc ?: method.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappings))
            append('`')
        }
        if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${parent.optimumName.replace('/', '.')} ")
            append(method.intermediaryName)
            append(method.obfDesc.merged!!.mapObfDescToNamed(mappings))
            append(" # ")
            append(method.optimumName)
            append('`')
        } else if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible method ")
            append(parent.optimumName)
            append(' ')
            append(method.optimumName)
            append(' ')
            append(method.mappedDesc ?: method.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappings))
            append('`')
        }
    }

    private fun String.mapObfDescToNamed(container: MappingsContainer): String =
        remapMethodDescriptor { container.getClassByObfName(it)?.intermediaryName ?: it }

    private fun String.localiseFieldDesc(): String {
        if (isEmpty()) return this
        if (length == 1) {
            return localisePrimitive(first())
        }
        val s = this
        var offset = 0
        for (i in s.indices) {
            if (s[i] == '[')
                offset++
            else break
        }
        if (offset + 1 == length) {
            val primitive = StringBuilder(localisePrimitive(first()))
            for (i in 1..offset) primitive.append("[]")
            return primitive.toString()
        }
        if (s[offset + 1] == 'L') {
            val substring = StringBuilder(substring(offset + 1))
            for (i in 1..offset) substring.append("[]")
            return substring.toString()
        }
        return s
    }

    private fun localisePrimitive(char: Char): String =
        when (char) {
            'Z' -> "boolean"
            'C' -> "char"
            'B' -> "byte"
            'S' -> "short"
            'I' -> "int"
            'F' -> "float"
            'J' -> "long"
            'D' -> "double"
            else -> char.toString()
        }
}

data class ResultHolder<T>(
    val value: T,
    val score: Double,
)

infix fun <T> T.hold(score: Double): ResultHolder<T> = ResultHolder(this, score)

fun MappingsContainer.toMetadata(): MappingsMetadata = MappingsMetadata(
    version = version,
    name = name,
    mappingSource = mappingSource,
    namespace = namespace ?: ""
)

data class QueryContext(
    val provider: MappingsProvider,
    val searchKey: String,
)

data class QueryResultCompound<T>(
    val mappings: MappingsContainer,
    val value: T,
)

fun <T> QueryResultCompound<T>.decompound(): QueryResult<T> = QueryResult(mappings.toMetadata(), value)

data class QueryResult<T>(
    val mappings: MappingsMetadata,
    val value: T,
)

data class MappingsMetadata(
    val version: String,
    val name: String,
    var mappingSource: MappingsContainer.MappingSource?,
    var namespace: String,
)

inline fun <T, V> QueryResultCompound<T>.map(transformer: (T) -> V): QueryResultCompound<V> {
    return QueryResultCompound(mappings, transformer(value))
}

inline fun <T, V> QueryResult<T>.map(transformer: (T) -> V): QueryResult<V> {
    return QueryResult(mappings, transformer(value))
}