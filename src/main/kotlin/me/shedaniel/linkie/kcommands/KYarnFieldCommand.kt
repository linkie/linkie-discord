package me.shedaniel.linkie.kcommands

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.similarity
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import kotlin.math.ceil
import kotlin.math.min

object KYarnFieldCommand : AKYarnFieldCommand("1.2.5")
object POMFFieldCommand : AKYarnFieldCommand("b1.7.3")

open class AKYarnFieldCommand(private val defaultVersion: String) : CommandBase {
    override fun execute(service: ScheduledExecutorService, event: MessageCreateEvent, author: Member, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isEmpty())
            throw InvalidUsageException("+$cmd <search> [version]")
        val mappingsContainer = tryLoadMappingContainer(args.last(), getMappingsContainer(defaultVersion))
        var searchTerm = args.joinToString(" ")
        if (mappingsContainer.version == args.last()) {
            searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf(' '))
        }
        if (searchTerm.contains(' '))
            throw InvalidUsageException("+$cmd <search> [version]")
        val hasClass = searchTerm.contains('.')
        val classes = mutableSetOf<Class>()
        if (hasClass) {
            val clazzKey = searchTerm.substring(0, searchTerm.lastIndexOf('.')).onlyClass()
            println(clazzKey)
            mappingsContainer.classes.forEach { clazz ->
                when {
                    clazz.intermediaryName.onlyClass().contains(clazzKey, true) -> classes.add(clazz)
                    clazz.mappedName?.onlyClass()?.contains(clazzKey, true) == true -> classes.add(clazz)
                    clazz.obfName.client?.onlyClass()?.contains(clazzKey, true) == true -> classes.add(clazz)
                    clazz.obfName.server?.onlyClass()?.contains(clazzKey, true) == true -> classes.add(clazz)
                    clazz.obfName.merged?.onlyClass()?.contains(clazzKey, true) == true -> classes.add(clazz)
                }
            }
        } else classes.addAll(mappingsContainer.classes)
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
            when (it.value) {
                FindFieldMethod.MAPPED -> it.key.field.mappedName!!
                FindFieldMethod.OBF_CLIENT -> it.key.field.obfName.client!!
                FindFieldMethod.OBF_SERVER -> it.key.field.obfName.server!!
                FindFieldMethod.OBF_MERGED -> it.key.field.obfName.merged!!
                else -> it.key.field.intermediaryName
            }.onlyClass().similarity(fieldKey)
        }.map { it.key }
        if (sortedFields.isEmpty())
            throw NullPointerException("No results found!")
        var page = 0
        val maxPage = ceil(sortedFields.size / 5.0).toInt()
        channel.createEmbed { it.buildMessage(sortedFields, mappingsContainer, page, author, maxPage) }.subscribe { msg ->
            if (channel.type.name.startsWith("GUILD_"))
                msg.removeAllReactions().block()
            msg.subscribeReactions("⬅", "❌", "➡")
            api.eventDispatcher.on(ReactionAddEvent::class.java).filter { e -> e.messageId == msg.id }.take(Duration.ofMinutes(30)).subscribe {
                when {
                    it.userId == api.selfId.get() -> {
                    }
                    it.userId == author.id -> {
                        if (!it.emoji.asUnicodeEmoji().isPresent) {
                            msg.removeReaction(it.emoji, it.userId)
                        } else {
                            val unicode = it.emoji.asUnicodeEmoji().get()
                            if (unicode.raw == "❌") {
                                msg.delete().subscribe()
                            } else if (unicode.raw == "⬅") {
                                msg.removeReaction(it.emoji, it.userId)
                                if (page > 0) {
                                    page--
                                    msg.edit { it.setEmbed { it.buildMessage(sortedFields, mappingsContainer, page, author, maxPage) } }
                                }
                            } else if (unicode.raw == "➡") {
                                msg.removeReaction(it.emoji, it.userId)
                                if (page < maxPage - 1) {
                                    page++
                                    msg.edit { it.setEmbed { it.buildMessage(sortedFields, mappingsContainer, page, author, maxPage) } }
                                }
                            } else {
                                msg.removeReaction(it.emoji, it.userId)
                            }
                        }
                    }
                    else -> msg.removeReaction(it.emoji, it.userId)
                }
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
}

data class FieldWrapper(val field: Field, val parent: Class)

private fun EmbedCreateSpec.buildMessage(sortedMethods: List<FieldWrapper>, mappingsContainer: MappingsContainer, page: Int, author: Member, maxPage: Int) {
    setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
    setTimestampToNow()
    if (maxPage > 1) setTitle("List of Yarn Mappings (Page ${page + 1}/$maxPage)")
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