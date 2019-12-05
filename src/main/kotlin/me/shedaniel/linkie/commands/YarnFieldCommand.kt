package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.similarity
import me.shedaniel.linkie.utils.similarityOnNull
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.min

object YarnFieldCommand : AYarnFieldCommand({ if (it.id.asLong() == 602959845842485258) "1.2.5" else latestYarn }) {
    override fun getName(): String? = "Yarn Field Command"
    override fun getDescription(): String? = "Query yarn fields."
}

object POMFFieldCommand : AYarnFieldCommand({ "b1.7.3" }) {
    override fun getName(): String? = "POMF Field Command"
    override fun getDescription(): String? = "Query pomf fields."
}

open class AYarnFieldCommand(private val defaultVersion: (MessageChannel) -> String) : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isEmpty())
            throw InvalidUsageException("!$cmd <search> [version]")

        val mappingsContainerGetter = tryLoadMappingContainer(args.last(), getMappingsContainer(defaultVersion.invoke(channel)))

        var searchTerm = args.joinToString(" ").replace('.', '/')
        if (mappingsContainerGetter.first == args.last()) {
            searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf(' '))
        }
        if (searchTerm.contains(' '))
            throw InvalidUsageException("!$cmd <search> [version]")
        val hasClass = searchTerm.contains('/')
        val hasWildcard = (hasClass && searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass() == "*") || searchTerm.onlyClass('/') == "*"

        val message = channel.createEmbed {
            it.apply {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                var desc = "Searching up fields for **${mappingsContainerGetter.first}**."
                if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
                if (!mappingsContainerGetter.second) desc += "\nThis mappings version is not yet cached, might take some time to download."
                setDescription(desc)
            }
        }.block() ?: throw NullPointerException("Unknown Message!")

        try {
            val mappingsContainer = mappingsContainerGetter.third.invoke()

            val classes = mutableMapOf<Class, FindFieldMethod>()

            if (hasClass) {
                val clazzKey = searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass()
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
            val fieldKey = searchTerm.onlyClass('/')
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
                fieldKey == "*" && (!hasClass || searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass() == "*") -> {
                    // Class and field both wildcard
                    fields.entries.sortedBy { it.key.field.intermediaryName }.sortedBy {
                        it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                    }.map { it.key }
                }
                fieldKey == "*" -> {
                    // Only field wildcard
                    val classKey = searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass()
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
                hasClass && searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass() != "*" -> {
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
            if (sortedFields.isEmpty())
                throw NullPointerException("No results found!")
            var page = 0
            val maxPage = ceil(sortedFields.size / 5.0).toInt()
            message.edit { it.setEmbed { it.buildMessage(sortedFields, mappingsContainer, page, user, maxPage) } }.subscribe { msg ->
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
                                        msg.edit { it.setEmbed { it.buildMessage(sortedFields, mappingsContainer, page, user, maxPage) } }.subscribe()
                                    }
                                } else if (unicode.raw == "➡") {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                    if (page < maxPage - 1) {
                                        page++
                                        msg.edit { it.setEmbed { it.buildMessage(sortedFields, mappingsContainer, page, user, maxPage) } }.subscribe()
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

private fun EmbedCreateSpec.buildMessage(sortedMethods: List<FieldWrapper>, mappingsContainer: MappingsContainer, page: Int, author: User, maxPage: Int) {
    setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
    setTimestampToNow()
    if (maxPage > 1) setTitle("List of ${mappingsContainer.name} Mappings (Page ${page + 1}/$maxPage)")
    var desc = ""
    sortedMethods.dropAndTake(5 * page, 5).forEach {
        if (!desc.isEmpty())
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
        desc += "\n__Type__: `${(it.field.mappedDesc
                ?: it.field.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)).localiseFieldDesc()}`"
        desc += "\n__Mixin Target__: `L${it.parent.mappedName
                ?: it.parent.intermediaryName};${if (it.field.mappedName == null) it.field.intermediaryName else it.field.mappedName}:" +
                "${it.field.mappedDesc ?: it.field.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)}`"
    }
    setDescription(desc.substring(0, min(desc.length, 2000)))
}

private fun String.localiseFieldDesc(): String {
    if (length == 1) {
        val c = first()
        when (c) {
            'Z' -> return "boolean"
            'C' -> return "char"
            'B' -> return "byte"
            'S' -> return "short"
            'I' -> return "int"
            'F' -> return "float"
            'J' -> return "long"
            'D' -> return "double"
        }
    }
    if (length == 2 && first() == '[') {
        val c = last()
        when (c) {
            'Z' -> return "boolean[]"
            'C' -> return "char[]"
            'B' -> return "byte[]"
            'S' -> return "short[]"
            'I' -> return "int[]"
            'F' -> return "float[]"
            'J' -> return "long[]"
            'D' -> return "double[]"
        }
    }
    if (startsWith("[L"))
        return substring(2, length - 1).replace('/', '.') + "[]"
    if (startsWith("L"))
        return substring(1, length - 1).replace('/', '.')
    if (startsWith("[[L"))
        return substring(3, length - 1).replace('/', '.')
    return replace('/', '.')
}