package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.*
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

class QueryFieldCommand(private val namespace: Namespace?) : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        if (this.namespace == null) {
            args.validateUsage(prefix, 2..3, "$cmd <namespace> <search> [version]\nDo !namespaces for list of namespaces.")
        } else args.validateUsage(prefix, 1..2, "$cmd <search> [version]")
        val namespace = this.namespace ?: (Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
            ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", ")))
        if (this.namespace == null) args.removeAt(0)
        namespace.validateNamespace()
        namespace.validateGuild(event)

        val mappingsProvider = if (args.size == 1) MappingsProvider.empty(namespace) else namespace.getProvider(args.last())
        if (mappingsProvider.isEmpty() && args.size == 2) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException(
                "Invalid Version: " + args.last() + "\nVersions: " +
                        if (list.size > 20)
                            list.take(20).joinToString(", ") + ", etc"
                        else list.joinToString(", ")
            )
        }
        mappingsProvider.injectDefaultVersion(namespace.getDefaultProvider {
            if (namespace == YarnNamespace) when (channel.id.asLong()) {
                602959845842485258 -> "legacy"
                661088839464386571 -> "patchwork"
                else -> namespace.getDefaultMappingChannel()
            } else namespace.getDefaultMappingChannel()
        })
        mappingsProvider.validateDefaultVersionNotEmpty()
        val message = AtomicReference<Message?>()
        val searchKey = args.first().replace('.', '/')
        val version = mappingsProvider.version!!
        var page = 0
        val maxPage = AtomicInteger(-1)
        val fields = ValueKeeper(Duration.ofMinutes(2)) { build(searchKey, namespace.getProvider(version), user, message, channel, maxPage) }
        message.editOrCreate(channel) { buildMessage(namespace, fields.get().second, fields.get().first, page, user, maxPage.get()) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(fields.timeToKeep) {
                if (maxPage.get() > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        message.editOrCreate(channel) { buildMessage(namespace, fields.get().second, fields.get().first, page, user, maxPage.get()) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (maxPage.get() > 1) register("➡") {
                    if (page < maxPage.get() - 1) {
                        page++
                        message.editOrCreate(channel) { buildMessage(namespace, fields.get().second, fields.get().first, page, user, maxPage.get()) }.subscribe()
                    }
                }
            }.build(msg, user)
        }
    }

    private enum class FindFieldMethod {
        INTERMEDIARY,
        MAPPED,
        OBF_CLIENT,
        OBF_SERVER,
        OBF_MERGED,
        WILDCARD
    }

    private data class FieldWrapper(val field: Field, val parent: Class, val cm: FindFieldMethod)

    private fun build(
        searchKey: String,
        provider: MappingsProvider,
        user: User,
        message: AtomicReference<Message?>,
        channel: MessageChannel,
        maxPage: AtomicInteger,
        hasClass: Boolean = searchKey.contains('/'),
        hasWildcard: Boolean = (hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") || searchKey.onlyClass('/') == "*",
    ): Pair<MappingsContainer, List<FieldWrapper>> {
        if (!provider.cached!! || hasWildcard) message.editOrCreate(channel) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up fields for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            setDescription(desc)
        }.block().also { message.set(it) }
        return getCatching(message, channel, user) {
            val mappingsContainer = provider.mappingsContainer!!.invoke()
            val classes = mutableMapOf<Class, FindFieldMethod>()

            if (hasClass) {
                val clazzKey = searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass()
                if (clazzKey == "*") {
                    mappingsContainer.classes.forEach { classes[it] = FindFieldMethod.WILDCARD }
                } else {
                    mappingsContainer.classes.forEach { clazz ->
                        when {
                            clazz.intermediaryName.onlyClass().contains(clazzKey, true) -> classes[clazz] = FindFieldMethod.INTERMEDIARY
                            clazz.mappedName?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindFieldMethod.MAPPED
                            clazz.obfName.client?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindFieldMethod.OBF_CLIENT
                            clazz.obfName.server?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindFieldMethod.OBF_SERVER
                            clazz.obfName.merged?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindFieldMethod.OBF_MERGED
                        }
                    }
                }
            } else mappingsContainer.classes.forEach { classes[it] = FindFieldMethod.WILDCARD }
            val fields = mutableMapOf<FieldWrapper, FindFieldMethod>()
            val fieldKey = searchKey.onlyClass('/')
            if (fieldKey == "*") {
                classes.forEach { (clazz, cm) ->
                    clazz.fields.forEach { field ->
                        if (fields.none { it.key.field == field })
                            fields[FieldWrapper(field, clazz, cm)] = FindFieldMethod.WILDCARD
                    }
                }
            } else {
                classes.forEach { (clazz, cm) ->
                    clazz.fields.forEach { field ->
                        if (fields.none { it.key.field == field })
                            when {
                                field.intermediaryName.contains(fieldKey, true) -> fields[FieldWrapper(field, clazz, cm)] = FindFieldMethod.INTERMEDIARY
                                field.mappedName?.contains(fieldKey, true) == true -> fields[FieldWrapper(field, clazz, cm)] = FindFieldMethod.MAPPED
                                field.obfName.client?.contains(fieldKey, true) == true -> fields[FieldWrapper(field, clazz, cm)] = FindFieldMethod.OBF_CLIENT
                                field.obfName.server?.contains(fieldKey, true) == true -> fields[FieldWrapper(field, clazz, cm)] = FindFieldMethod.OBF_SERVER
                                field.obfName.merged?.contains(fieldKey, true) == true -> fields[FieldWrapper(field, clazz, cm)] = FindFieldMethod.OBF_MERGED
                            }
                    }
                }
            }
            val sortedFields = when {
                fieldKey == "*" && (!hasClass || searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") -> {
                    // Class and field both wildcard
                    fields.entries.sortedBy { it.key.field.intermediaryName }.sortedBy {
                        it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                    }.map { it.key }
                }
                fieldKey == "*" -> {
                    // Only field wildcard
                    val classKey = searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass()
                    fields.entries.sortedBy { it.key.field.intermediaryName }.sortedByDescending {
                        when (it.key.cm) {
                            FindFieldMethod.MAPPED -> it.key.parent.mappedName!!.onlyClass()
                            FindFieldMethod.OBF_CLIENT -> it.key.parent.obfName.client!!.onlyClass()
                            FindFieldMethod.OBF_SERVER -> it.key.parent.obfName.server!!.onlyClass()
                            FindFieldMethod.OBF_MERGED -> it.key.parent.obfName.merged!!.onlyClass()
                            FindFieldMethod.INTERMEDIARY -> it.key.parent.intermediaryName.onlyClass()
                            else -> null
                        }.similarityOnNull(classKey)
                    }.map { it.key }
                }
                hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() != "*" -> {
                    // has class
                    fields.entries.sortedByDescending {
                        when (it.value) {
                            FindFieldMethod.MAPPED -> it.key.field.mappedName!!
                            FindFieldMethod.OBF_CLIENT -> it.key.field.obfName.client!!
                            FindFieldMethod.OBF_SERVER -> it.key.field.obfName.server!!
                            FindFieldMethod.OBF_MERGED -> it.key.field.obfName.merged!!
                            else -> it.key.field.intermediaryName
                        }.onlyClass().similarity(fieldKey)
                    }.sortedBy {
                        it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                    }.map { it.key }
                }
                else -> {
                    fields.entries.sortedBy {
                        it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                    }.sortedByDescending {
                        when (it.value) {
                            FindFieldMethod.MAPPED -> it.key.field.mappedName!!
                            FindFieldMethod.OBF_CLIENT -> it.key.field.obfName.client!!
                            FindFieldMethod.OBF_SERVER -> it.key.field.obfName.server!!
                            FindFieldMethod.OBF_MERGED -> it.key.field.obfName.merged!!
                            else -> it.key.field.intermediaryName
                        }.onlyClass().similarity(fieldKey)
                    }.map { it.key }
                }
            }
            if (sortedFields.isEmpty()) {
                if (!searchKey.onlyClass().isValidIdentifier()) {
                    throw NullPointerException("No results found! `${searchKey.onlyClass()}` is not a valid java identifier!")
                } else if (searchKey.startsWith("func_") || searchKey.startsWith("method_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a method!")
                } else if (searchKey.startsWith("class_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a class!")
                }
                throw NullPointerException("No results found!")
            }

            maxPage.set(ceil(sortedFields.size / 5.0).toInt())
            return@getCatching mappingsContainer to sortedFields
        }
    }

    private fun EmbedCreateSpec.buildMessage(namespace: Namespace, sortedFields: List<FieldWrapper>, mappingsContainer: MappingsContainer, page: Int, author: User, maxPage: Int) {
        if (mappingsContainer.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${mappingsContainer.mappingSource}", author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${mappingsContainer.name} Mappings (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${mappingsContainer.name} Mappings")
        buildSafeDescription {
            sortedFields.dropAndTake(5 * page, 5).forEach { fieldWrapper ->
                if (isNotEmpty())
                    appendLine().appendLine()
                appendLine("**MC ${mappingsContainer.version}: ${fieldWrapper.parent.optimumName}#__${fieldWrapper.field.optimumName}__**")
                append("__Name__: ")
                append(fieldWrapper.field.obfName.buildString(nonEmptySuffix = " => "))
                append("`${fieldWrapper.field.intermediaryName}`")
                append(fieldWrapper.field.mappedName.mapIfNotNullOrNotEquals(fieldWrapper.field.intermediaryName) { " => `$it`" } ?: "")
                if (namespace.supportsFieldDescription()) {
                    appendLine().append("__Type__: ")
                    append((fieldWrapper.field.mappedDesc ?: fieldWrapper.field.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappingsContainer)).localiseFieldDesc())
                }
                if (namespace.supportsMixin()) {
                    appendLine().append("__Mixin Target__: `")
                    append("L${fieldWrapper.parent.optimumName};")
                    append(fieldWrapper.field.optimumName)
                    append(':')
                    append(fieldWrapper.field.mappedDesc ?: fieldWrapper.field.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappingsContainer))
                    append('`')
                }
                if (namespace.supportsAT()) {
                    appendLine().append("__AT__: `public ${fieldWrapper.parent.optimumName.replace('/', '.')} ")
                    append(fieldWrapper.field.intermediaryName)
                    append(" # ")
                    append(fieldWrapper.field.optimumName)
                    append('`')
                } else if (namespace.supportsAW()) {
                    appendLine().append("__AW__: `accessible field ")
                    append(fieldWrapper.parent.optimumName)
                    append(' ')
                    append(fieldWrapper.field.optimumName)
                    append(' ')
                    append(fieldWrapper.field.mappedDesc ?: fieldWrapper.field.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappingsContainer))
                    append('`')
                }
            }
        }
    }

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

    override fun getName(): String =
        if (namespace != null) namespace.id.capitalize() + " Field Query"
        else "Field Query"

    override fun getDescription(): String =
        if (namespace != null) "Queries ${namespace.id} field entries."
        else "Queries field entries."
}