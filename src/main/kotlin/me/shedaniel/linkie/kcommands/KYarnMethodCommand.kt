package me.shedaniel.linkie.kcommands

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.similarity
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import kotlin.math.ceil
import kotlin.math.min

object KYarnMethodCommand : AKYarnMethodCommand("1.2.5")
object POMFMethodCommand : AKYarnMethodCommand("b1.7.3")

open class AKYarnMethodCommand(private val defaultVersion: String) : CommandBase {
    override fun execute(service: ScheduledExecutorService, event: MessageCreateEvent, author: Member, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isEmpty())
            throw InvalidUsageException("+$cmd <search> [version]")
        val mappingsContainerGetter = tryLoadMappingContainer(args.last(), getMappingsContainer(defaultVersion))
        var searchTerm = args.joinToString(" ")
        if (mappingsContainerGetter.first == args.last()) {
            searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf(' '))
        }
        if (searchTerm.contains(' '))
            throw InvalidUsageException("+$cmd <search> [version]")
        val hasClass = searchTerm.contains('.')
        val hasWildcard = (hasClass && searchTerm.substring(0, searchTerm.lastIndexOf('.')).onlyClass() == "*") || searchTerm.onlyClass('.') == "*"

        val message = channel.createEmbed {
            it.apply {
                setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
                setTimestampToNow()
                var desc = "Searching up methods for **${mappingsContainerGetter.first}**."
                if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
                if (!mappingsContainerGetter.second) desc += "\nThis mappings version is not yet cached, might take some time to download."
                setDescription(desc)
            }
        }.block() ?: throw NullPointerException("Unknown Message!")

        val mappingsContainer = mappingsContainerGetter.third.invoke()

        val classes = mutableMapOf<Class, FindMethodMethod>()
        if (hasClass) {
            val clazzKey = searchTerm.substring(0, searchTerm.lastIndexOf('.')).onlyClass()
            if (clazzKey == "*") {
                mappingsContainer.classes.forEach { classes[it] = FindMethodMethod.WILDCARD }
            } else {
                mappingsContainer.classes.forEach { clazz ->
                    when {
                        clazz.intermediaryName.onlyClass().contains(clazzKey, true) -> classes[clazz] = FindMethodMethod.INTERMEDIARY
                        clazz.mappedName?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindMethodMethod.MAPPED
                        clazz.obfName.client?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindMethodMethod.OBF_CLIENT
                        clazz.obfName.server?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindMethodMethod.OBF_SERVER
                        clazz.obfName.merged?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindMethodMethod.OBF_MERGED
                    }
                }
            }
        } else  mappingsContainer.classes.forEach { classes[it] = FindMethodMethod.WILDCARD }
        val methods = mutableMapOf<MethodWrapper, FindMethodMethod>()
        val methodKey = searchTerm.onlyClass('.')
        if (methodKey == "*") {
            classes.forEach {(clazz, cm) ->
                clazz.methods.forEach { method ->
                    if (methods.none { it.key.method == method })
                        methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.WILDCARD
                }
            }
        } else {
            classes.forEach { (clazz, cm) ->
                clazz.methods.forEach { method ->
                    if (methods.none { it.key.method == method })
                        when {
                            method.intermediaryName.contains(methodKey, true) -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.INTERMEDIARY
                            method.mappedName?.contains(methodKey, true) == true -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.MAPPED
                            method.obfName.client?.contains(methodKey, true) == true -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.OBF_CLIENT
                            method.obfName.server?.contains(methodKey, true) == true -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.OBF_SERVER
                            method.obfName.merged?.contains(methodKey, true) == true -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.OBF_MERGED
                        }
                }
            }
        }
        val sortedMethods = if (methodKey == "*") {
            if (!hasClass || searchTerm.substring(0, searchTerm.lastIndexOf('.')).onlyClass() == "*") {
                methods.entries.sortedBy { it.key.method.intermediaryName }.sortedBy {
                    it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                }.map { it.key }
            } else {
                val classKey = searchTerm.substring(0, searchTerm.lastIndexOf('.')).onlyClass()
                methods.entries.sortedBy { it.key.method.intermediaryName }.sortedByDescending {
                    when (it.key.cm) {
                        FindMethodMethod.MAPPED -> it.key.parent.mappedName!!
                        FindMethodMethod.OBF_CLIENT -> it.key.parent.obfName.client!!
                        FindMethodMethod.OBF_SERVER -> it.key.parent.obfName.server!!
                        FindMethodMethod.OBF_MERGED -> it.key.parent.obfName.merged!!
                        else -> it.key.parent.intermediaryName
                    }.onlyClass().similarity(classKey)
                }.map { it.key }
            }
        } else
            methods.entries.sortedByDescending {
                when (it.value) {
                    FindMethodMethod.MAPPED -> it.key.method.mappedName!!
                    FindMethodMethod.OBF_CLIENT -> it.key.method.obfName.client!!
                    FindMethodMethod.OBF_SERVER -> it.key.method.obfName.server!!
                    FindMethodMethod.OBF_MERGED -> it.key.method.obfName.merged!!
                    else -> it.key.method.intermediaryName
                }.onlyClass().similarity(methodKey)
            }.sortedBy {
                it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
            }.map { it.key }
        if (sortedMethods.isEmpty())
            throw NullPointerException("No results found!")
        var page = 0
        val maxPage = ceil(sortedMethods.size / 5.0).toInt()
        message.edit { it.setEmbed { it.buildMessage(sortedMethods, mappingsContainer, page, author, maxPage) } }.subscribe { msg ->
            if (channel.type.name.startsWith("GUILD_"))
                msg.removeAllReactions().block()
            msg.subscribeReactions("⬅", "❌", "➡")
            api.eventDispatcher.on(ReactionAddEvent::class.java).filter { e -> e.messageId == msg.id }.take(Duration.ofMinutes(15)).subscribe {
                when {
                    it.userId == api.selfId.get() -> {
                    }
                    it.userId == author.id -> {
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
                                    msg.edit { it.setEmbed { it.buildMessage(sortedMethods, mappingsContainer, page, author, maxPage) } }.subscribe()
                                }
                            } else if (unicode.raw == "➡") {
                                msg.removeReaction(it.emoji, it.userId).subscribe()
                                if (page < maxPage - 1) {
                                    page++
                                    msg.edit { it.setEmbed { it.buildMessage(sortedMethods, mappingsContainer, page, author, maxPage) } }.subscribe()
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
    }
}

private enum class FindMethodMethod {
    INTERMEDIARY,
    MAPPED,
    OBF_CLIENT,
    OBF_SERVER,
    OBF_MERGED,
    WILDCARD
}

private data class MethodWrapper(val method: Method, val parent: Class, val cm: FindMethodMethod)

private fun EmbedCreateSpec.buildMessage(sortedMethods: List<MethodWrapper>, mappingsContainer: MappingsContainer, page: Int, author: Member, maxPage: Int) {
    setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
    setTimestampToNow()
    if (maxPage > 1) setTitle("List of Yarn Mappings (Page ${page + 1}/$maxPage)")
    var desc = ""
    sortedMethods.dropAndTake(5 * page, 5).forEach {
        if (!desc.isEmpty())
            desc += "\n\n"
        val obfMap = LinkedHashMap<String, String>()
        if (!it.method.obfName.isMerged()) {
            if (it.method.obfName.client != null) obfMap["client"] = it.method.obfName.client!!
            if (it.method.obfName.server != null) obfMap["server"] = it.method.obfName.server!!
        }
        desc += "**MC ${mappingsContainer.version}: ${it.parent.mappedName
                ?: it.parent.intermediaryName}.${it.method.mappedName ?: it.method.intermediaryName}**\n" +
                "__Name__: " + (if (it.method.obfName.isEmpty()) "" else if (it.method.obfName.isMerged()) "${it.method.obfName.merged} => " else "${obfMap.entries.joinToString { "${it.key}=**${it.value}**" }} => ") +
                "`${it.method.intermediaryName}`" + (if (it.method.mappedName == null || it.method.mappedName == it.method.intermediaryName) "" else " => `${it.method.mappedName}`")
        desc += "\n__Descriptor__: `${if (it.method.mappedName == null) it.method.intermediaryName else it.method.mappedName}${it.method.mappedDesc
                ?: it.method.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)}`"
        desc += "\n__Mixin Target__: `L${it.parent.mappedName
                ?: it.parent.intermediaryName};${if (it.method.mappedName == null) it.method.intermediaryName else it.method.mappedName}${it.method.mappedDesc
                ?: it.method.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)}`"
    }
    setDescription(desc.substring(0, min(desc.length, 2000)))
}