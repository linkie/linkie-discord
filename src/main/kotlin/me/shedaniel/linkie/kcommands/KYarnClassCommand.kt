package me.shedaniel.linkie.kcommands

import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.similarity
import org.apache.commons.lang3.StringUtils
import org.javacord.api.entity.message.MessageAuthor
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.event.message.MessageCreateEvent
import java.util.concurrent.ScheduledExecutorService
import kotlin.math.ceil
import kotlin.math.min

object KYarnClassCommand : CommandBase {
    override fun execute(service: ScheduledExecutorService, event: MessageCreateEvent, author: MessageAuthor, cmd: String, args: Array<String>) {
        if (args.isEmpty())
            throw InvalidUsageException("+$cmd <search> [version]")
        val mappingsContainer = tryLoadMappingContainer(args.last())
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
            when (it.value.key) {
                FindClassMethod.MAPPED -> it.key.mappedName!!
                FindClassMethod.OBF_CLIENT -> it.key.obfName.client!!
                FindClassMethod.OBF_SERVER -> it.key.obfName.server!!
                FindClassMethod.OBF_MERGED -> it.key.obfName.merged!!
                else -> it.key.intermediaryName
            }.onlyClass().similarity(it.value.value.onlyClass())
        }.map { it.key }
        if (sortedClasses.isEmpty())
            throw NullPointerException("No results found!")
        var page = 0
        val maxPage = ceil(sortedClasses.size / 5.0).toInt()
        event.channel.sendMessage(buildMessage(sortedClasses, mappingsContainer, page, author, maxPage)).whenComplete { msg, _ ->
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
                                msg.edit(buildMessage(sortedClasses, mappingsContainer, page, author, maxPage))
                            }
                        } else if (reactionAddEvent.emoji.equalsEmoji("➡")) {
                            reactionAddEvent.removeReaction()
                            if (page < maxPage - 1) {
                                page++
                                msg.edit(buildMessage(sortedClasses, mappingsContainer, page, author, maxPage))
                            }
                        }
                    }
                })
            })
        }
    }
}

private fun buildMessage(sortedClasses: List<Class>, mappingsContainer: MappingsContainer, page: Int, author: MessageAuthor, maxPage: Int): EmbedBuilder {
    val builder = EmbedBuilder()
            .setFooter("Requested by " + author.discriminatedName, author.avatar)
            .setTimestampToNow()
    if (maxPage > 1) builder.setTitle("List of Yarn Mappings (Page ${page + 1}/$maxPage)")
    var desc = ""
    sortedClasses.stream().skip(5.toLong() * page).limit(5).forEach {
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
    builder.setDescription(desc.substring(0, min(desc.length, 2000)))
    return builder
}

private enum class FindClassMethod {
    INTERMEDIARY,
    MAPPED,
    OBF_CLIENT,
    OBF_SERVER,
    OBF_MERGED,
}