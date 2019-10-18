package me.shedaniel.linkie.kcommands

import me.shedaniel.linkie.*
import org.javacord.api.entity.message.MessageAuthor
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.event.message.MessageCreateEvent
import java.util.concurrent.ScheduledExecutorService
import kotlin.math.ceil
import kotlin.math.min

object KYarnFieldCommand : CommandBase {
    override fun execute(service: ScheduledExecutorService, event: MessageCreateEvent, author: MessageAuthor, cmd: String, args: Array<String>) {
        if (args.isEmpty())
            throw InvalidUsageException("+$cmd <search> [version]")
        var mappingsContainer = getMappingsContainer(args.last())
        var searchTerm = args.joinToString(" ")
        if (mappingsContainer == null) {
            mappingsContainer = getMappingsContainer("1.2.5")
        } else {
            searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf(' '))
        }
        if (searchTerm.contains(' '))
            throw InvalidUsageException("+$cmd <search> [version]")
        val hasClass = searchTerm.contains('.')
        val classes = mutableSetOf<Class>()
        if (hasClass) {
            val clazzKey = searchTerm.substring(0, searchTerm.lastIndexOf('.')).onlyClass()
            println(clazzKey)
            mappingsContainer!!.classes.forEach { clazz ->
                when {
                    clazz.intermediaryName.onlyClass().contains(clazzKey, true) -> classes.add(clazz)
                    clazz.mappedName?.onlyClass()?.contains(clazzKey, true) == true -> classes.add(clazz)
                    clazz.obfName.client?.onlyClass()?.contains(clazzKey, true) == true -> classes.add(clazz)
                    clazz.obfName.server?.onlyClass()?.contains(clazzKey, true) == true -> classes.add(clazz)
                    clazz.obfName.merged?.onlyClass()?.contains(clazzKey, true) == true -> classes.add(clazz)
                }
            }
        } else classes.addAll(mappingsContainer!!.classes)
        val fields = mutableMapOf<FieldWrapper, FindFieldMethod>()
        val fieldKey = searchTerm.onlyClass('.')
        classes.forEach {
            it.fields.forEach { field ->
                if (fields.none { it.key.field == field })
                    when {
                        field.intermediaryName.contains(fieldKey, true) -> fields[FieldWrapper(field, it)] = FindFieldMethod.INTERMEDIARY
                        field.mappedName?.contains(fieldKey, true) == true -> fields[FieldWrapper(field, it)] = FindFieldMethod.MAPPED
                        field.obfName.client?.contains(fieldKey, true) == true -> fields[FieldWrapper(field, it)] = FindFieldMethod.OBF_CLIENT
                        field.obfName.server?.contains(fieldKey, true) == true -> fields[FieldWrapper(field, it)] = FindFieldMethod.OBF_SERVER
                        field.obfName.merged?.contains(fieldKey, true) == true -> fields[FieldWrapper(field, it)] = FindFieldMethod.OBF_MERGED
                    }
            }
        }
        val sortedFields = fields.entries.sortedByDescending {
            similarity(when (it.value) {
                FindFieldMethod.MAPPED -> it.key.field.mappedName!!
                FindFieldMethod.OBF_CLIENT -> it.key.field.obfName.client!!
                FindFieldMethod.OBF_SERVER -> it.key.field.obfName.server!!
                FindFieldMethod.OBF_MERGED -> it.key.field.obfName.merged!!
                else -> it.key.field.intermediaryName
            }.onlyClass(), fieldKey)
        }.map { it.key }
        var page = 0
        val maxPage = ceil(sortedFields.size / 5.0).toInt()
        event.channel.sendMessage(buildMessage(sortedFields, mappingsContainer, page, author, maxPage)).whenComplete { msg, _ ->
            if (msg.isServerMessage)
                msg.removeAllReactions().get()
            msg.addReactions("⬅", "❌", "➡").thenRun({
                msg.addReactionAddListener({ reactionAddEvent ->
                    if (reactionAddEvent.user.discriminatedName != LinkieBot.getApi().yourself.discriminatedName && reactionAddEvent.user.discriminatedName != author.discriminatedName) {
                        reactionAddEvent.removeReaction()
                    } else if (reactionAddEvent.user.discriminatedName == author.discriminatedName) {
                        if (reactionAddEvent.emoji.equalsEmoji("❌"))
                            reactionAddEvent.deleteMessage()
                        else if (reactionAddEvent.emoji.equalsEmoji("⬅")) {
                            reactionAddEvent.removeReaction()
                            if (page > 0) {
                                page--
                                msg.edit(buildMessage(sortedFields, mappingsContainer, page, author, maxPage))
                            }
                        } else if (reactionAddEvent.emoji.equalsEmoji("➡")) {
                            reactionAddEvent.removeReaction()
                            if (page < maxPage - 1) {
                                page++
                                msg.edit(buildMessage(sortedFields, mappingsContainer, page, author, maxPage))
                            }
                        }
                    }
                })
            })
        }
    }
}

private enum class FindFieldMethod {
    INTERMEDIARY,
    MAPPED,
    OBF_CLIENT,
    OBF_SERVER,
    OBF_MERGED,
}

data class FieldWrapper(val field: Field, val parent: Class)

private fun buildMessage(sortedMethods: List<FieldWrapper>, mappingsContainer: MappingsContainer, page: Int, author: MessageAuthor, maxPage: Int): EmbedBuilder {
    val builder = EmbedBuilder()
            .setFooter("Requested by " + author.discriminatedName, author.avatar)
            .setTimestampToNow()
    if (maxPage > 1) builder.setTitle("List of Yarn Mappings (Page ${page + 1}/$maxPage)")
    var desc = ""
    sortedMethods.stream().skip(5.toLong() * page).limit(5).forEach {
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
    builder.setDescription(desc.substring(0, min(desc.length, 2000)))
    return builder
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