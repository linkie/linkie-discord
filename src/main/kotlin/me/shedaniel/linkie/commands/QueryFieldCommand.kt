package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.*
import java.time.Duration
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.min

class QueryFieldCommand(private val namespace: Namespace?) : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (this.namespace == null) {
            if (args.size !in 2..3)
                throw InvalidUsageException("!$cmd <namespace> <search> [version]\n" +
                        "Do !namespaces for list of namespaces.")
        } else if (args.size !in 1..2)
            throw InvalidUsageException("!$cmd <search> [version]")
        val namespace = this.namespace ?: (Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
                ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", ")))
        val args = if (this.namespace == null) args.drop(1).toTypedArray() else args
        if (namespace.reloading)
            throw IllegalStateException("Mappings (ID: ${namespace.id}) is reloading now, please try again in 5 seconds.")

        val mappingsProvider = if (args.size == 1) Namespace.MappingsProvider.ofEmpty() else namespace.getProvider(args.last())
        if (mappingsProvider.isEmpty() && args.size == 2) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (list.size > 20)
                        list.take(20).joinToString(", ") + ", etc"
                    else list.joinToString(", "))
        }
        mappingsProvider.injectDefaultVersion(namespace.getDefaultProvider(cmd, channel.id))
        if (mappingsProvider.isEmpty())
            throw IllegalStateException("Invalid Default Version! Linkie might be reloading its cache right now.")
        val searchKey = args.first().replace('.', '/')
        val hasClass = searchKey.contains('/')
        val hasWildcard = (hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") || searchKey.onlyClass('/') == "*"

        val message = channel.createEmbed {
            it.apply {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                var desc = "Searching up fields for **${namespace.id} ${mappingsProvider.version}**."
                if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
                if (!mappingsProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
                setDescription(desc)
            }
        }.block() ?: throw NullPointerException("Unknown Message!")
        try {
            val mappingsContainer = mappingsProvider.mappingsContainer!!.invoke()
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
                if (searchKey.startsWith("func_") || searchKey.startsWith("method_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a method!")
                } else if (searchKey.startsWith("class_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a class!")
                }
                throw NullPointerException("No results found!")
            }
            var page = 0
            val maxPage = ceil(sortedFields.size / 5.0).toInt()
            message.edit { it.setEmbed { it.buildMessage(namespace, sortedFields, mappingsContainer, page, user, maxPage) } }.subscribe { msg ->
                if (channel.type.name.startsWith("GUILD_"))
                    msg.removeAllReactions().block()
                msg.subscribeReactions("⬅", "❌", "➡")
                api.eventDispatcher.on(ReactionAddEvent::class.java).filter { e -> e.messageId == msg.id }.take(Duration.ofMinutes(15)).subscribe {
                    when (it.userId) {
                        api.selfId.get() -> {
                        }
                        user.id -> {
                            if (!it.emoji.asUnicodeEmoji().isPresent) {
                                msg.removeReaction(it.emoji, it.userId).subscribe()
                            } else {
                                val unicode = it.emoji.asUnicodeEmoji().get()
                                if (unicode.raw == "❌") {
                                    msg.delete().subscribe()
                                } else if (unicode.raw == "⬅") {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                    if (page > 0) {
                                        page--
                                        msg.edit { it.setEmbed { it.buildMessage(namespace, sortedFields, mappingsContainer, page, user, maxPage) } }.subscribe()
                                    }
                                } else if (unicode.raw == "➡") {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                    if (page < maxPage - 1) {
                                        page++
                                        msg.edit { it.setEmbed { it.buildMessage(namespace, sortedFields, mappingsContainer, page, user, maxPage) } }.subscribe()
                                    }
                                } else {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                }
                            }
                        }
                        else -> msg.removeReaction(it.emoji, it.userId).subscribe()
                    }
                }
            }
        } catch (t: Throwable) {
            try {
                message.edit { it.setEmbed { it.generateThrowable(t, user) } }.subscribe()
            } catch (throwable2: Throwable) {
                throwable2.addSuppressed(t)
                throw throwable2
            }
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

    private fun EmbedCreateSpec.buildMessage(namespace: Namespace, sortedMethods: List<FieldWrapper>, mappingsContainer: MappingsContainer, page: Int, author: User, maxPage: Int) {
        if (mappingsContainer.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${mappingsContainer.mappingSource}", author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${mappingsContainer.name} Mappings (Page ${page + 1}/$maxPage)")
        var desc = ""
        sortedMethods.dropAndTake(5 * page, 5).forEach {
            if (desc.isNotEmpty())
                desc += "\n\n"
            val obfMap = LinkedHashMap<String, String>()
            if (!it.field.obfName.isMerged()) {
                if (it.field.obfName.client != null) obfMap["client"] = it.field.obfName.client!!
                if (it.field.obfName.server != null) obfMap["server"] = it.field.obfName.server!!
            }
            desc += "**MC ${mappingsContainer.version}: ${it.parent.mappedName
                    ?: it.parent.intermediaryName}.${it.field.mappedName ?: it.field.intermediaryName}**\n" +
                    "__Name__: " + (if (it.field.obfName.isEmpty()) "" else if (it.field.obfName.isMerged()) "${it.field.obfName.merged} => " else "${obfMap.entries.joinToString { "${it.key}=**${it.value}**" }} => ") +
                    "`${it.field.intermediaryName}`" + (if (it.field.mappedName == null || it.field.mappedName == it.field.intermediaryName) "" else " => `${it.field.mappedName}`")
            if (namespace.supportsFieldDescription()) {
                desc += "\n__Type__: `${(it.field.mappedDesc
                        ?: it.field.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)).localiseFieldDesc()}`"
            }
            if (namespace.supportsMixin()) {
                desc += "\n__Mixin Target__: `L${it.parent.mappedName
                        ?: it.parent.intermediaryName};${if (it.field.mappedName == null) it.field.intermediaryName else it.field.mappedName}:" +
                        "${it.field.mappedDesc ?: it.field.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)}`"
            }
            if (namespace.supportsAT()) {
                desc += "\n__AT__: `public ${(it.parent.mappedName ?: it.parent.intermediaryName).replace('/', '.')}" +
                        " ${it.field.intermediaryName} # ${if (it.field.mappedName == null) it.field.intermediaryName else it.field.mappedName}`"
            } else if (namespace.supportsAW()) {
                desc += "\n__AW__: `<access> field ${it.parent.mappedName ?: it.parent.intermediaryName} ${it.field.mappedName ?: it.field.intermediaryName} " +
                        "${it.field.mappedDesc ?: it.field.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)}`"
            }
        }
        setDescription(desc.substring(0, min(desc.length, 2000)))
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

    override fun getName(): String? =
            if (namespace != null) namespace.id.capitalize() + " Field Query"
            else "Field Query"

    override fun getDescription(): String? =
            if (namespace != null) "Queries ${namespace.id} field entries."
            else "Queries field entries."
}