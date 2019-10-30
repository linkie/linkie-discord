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
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import kotlin.math.ceil
import kotlin.math.min
import kotlin.Pair

object KYarnClassCommand : AKYarnClassCommand("1.2.5")
object POMFClassCommand : AKYarnClassCommand("b1.7.3")

open class AKYarnClassCommand(private val defaultVersion: String) : CommandBase {
    override fun execute(service: ScheduledExecutorService, event: MessageCreateEvent, author: Member, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isEmpty())
            throw InvalidUsageException("+$cmd <search> [version]")
        val mappingsContainer = tryLoadMappingContainer(args.last(), getMappingsContainer(defaultVersion))
        var searchTerm = args.joinToString(" ")
        if (mappingsContainer.version == args.last()) {
            searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf(' '))
        }
        val classes = mutableMapOf<Class, Pair<FindClassMethod, String>>()
        StringUtils.splitByWholeSeparatorPreserveAllTokens(searchTerm, " ").forEach { searchKey ->
            val searchKeyOnly = searchKey.onlyClass()
            mappingsContainer.classes.forEach { clazz ->
                if (!classes.contains(clazz))
                    if (clazz.intermediaryName.onlyClass().contains(searchKeyOnly, true))
                        classes[clazz] = Pair(FindClassMethod.INTERMEDIARY, searchKeyOnly)
                    else if (clazz.mappedName != null && clazz.mappedName!!.onlyClass().contains(searchKeyOnly, true))
                        classes[clazz] = Pair(FindClassMethod.MAPPED, searchKeyOnly)
                    else if (clazz.obfName.client != null && clazz.obfName.client!!.contains(searchKeyOnly, true))
                        classes[clazz] = Pair(FindClassMethod.OBF_CLIENT, searchKeyOnly)
                    else if (clazz.obfName.server != null && clazz.obfName.server!!.contains(searchKeyOnly, true))
                        classes[clazz] = Pair(FindClassMethod.OBF_SERVER, searchKeyOnly)
                    else if (clazz.obfName.merged != null && clazz.obfName.merged!!.contains(searchKeyOnly, true))
                        classes[clazz] = Pair(FindClassMethod.OBF_MERGED, searchKeyOnly)
            }
        }
        val sortedClasses = classes.entries.sortedByDescending {
            when (it.value.first) {
                FindClassMethod.MAPPED -> it.key.mappedName!!
                FindClassMethod.OBF_CLIENT -> it.key.obfName.client!!
                FindClassMethod.OBF_SERVER -> it.key.obfName.server!!
                FindClassMethod.OBF_MERGED -> it.key.obfName.merged!!
                else -> it.key.intermediaryName
            }.onlyClass().similarity(it.value.second.onlyClass())
        }.map { it.key }
        if (sortedClasses.isEmpty())
            throw NullPointerException("No results found!")
        var page = 0
        val maxPage = ceil(sortedClasses.size / 5.0).toInt()
        channel.createEmbed { it.buildMessage(sortedClasses, mappingsContainer, page, author, maxPage) }.subscribe { msg ->
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
                                    msg.edit { it.setEmbed { it.buildMessage(sortedClasses, mappingsContainer, page, author, maxPage) } }.subscribe()
                                }
                            } else if (unicode.raw == "➡") {
                                msg.removeReaction(it.emoji, it.userId).subscribe()
                                if (page < maxPage - 1) {
                                    page++
                                    msg.edit { it.setEmbed { it.buildMessage(sortedClasses, mappingsContainer, page, author, maxPage) } }.subscribe()
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

private fun EmbedCreateSpec.buildMessage(sortedClasses: List<Class>, mappingsContainer: MappingsContainer, page: Int, author: Member, maxPage: Int) {
    setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
    setTimestampToNow()
    if (maxPage > 1) setTitle("List of Yarn Mappings (Page ${page + 1}/$maxPage)")
    var desc = ""
    sortedClasses.dropAndTake(5 * page, 5).forEach {
        if (!desc.isEmpty())
            desc += "\n\n"
        val obfMap = LinkedHashMap<String, String>()
        if (!it.obfName.isMerged()) {
            if (it.obfName.client != null) obfMap["client"] = it.obfName.client!!
            if (it.obfName.server != null) obfMap["server"] = it.obfName.server!!
        }
        desc += "**MC ${mappingsContainer.version}: ${it.mappedName ?: it.intermediaryName}**\n" +
                "__Name__: " + (if (it.obfName.isEmpty()) "" else if (it.obfName.isMerged()) "${it.obfName.merged} => " else "${obfMap.entries.joinToString { "${it.key}=**${it.value}**" }} => ") +
                "`${it.intermediaryName}`" + (if (it.mappedName == null || it.mappedName == it.intermediaryName) "" else " => `${it.mappedName}`")
    }
    setDescription(desc.substring(0, min(desc.length, 2000)))
}

private enum class FindClassMethod {
    INTERMEDIARY,
    MAPPED,
    OBF_CLIENT,
    OBF_SERVER,
    OBF_MERGED,
}