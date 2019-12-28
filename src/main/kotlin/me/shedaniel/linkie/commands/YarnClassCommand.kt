package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.MatchResult
import me.shedaniel.linkie.utils.containsOrMatchWildcard
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.similarityOnNull
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.min

object YarnClassCommand : AYarnClassCommand({ if (it.id.asLong() == 602959845842485258) "1.2.5" else latestYarn }) {
    override fun getName(): String? = "Yarn Class Command"
    override fun getDescription(): String? = "Query yarn classes."
}

object POMFClassCommand : AYarnClassCommand({ "b1.7.3" }) {
    override fun getName(): String? = "POMF Class Command"
    override fun getDescription(): String? = "Query pomf classes."
}

open class AYarnClassCommand(private val defaultVersion: (MessageChannel) -> String, private val isYarn: Boolean = true) : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isEmpty())
            throw InvalidUsageException("!$cmd <search> [version]")
        val mappingsContainerGetter = if (isYarn)
            tryLoadYarnMappingContainer(args.last(), getYarnMappingsContainer(defaultVersion.invoke(channel)))
        else tryLoadMCPMappingContainer(args.last(), getMCPMappingsContainer(defaultVersion.invoke(channel)))
        var searchTerm = args.joinToString(" ")
        if (mappingsContainerGetter.first == args.last()) {
            searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf(' '))
        }
        if (searchTerm.contains(' '))
            throw InvalidUsageException("!$cmd <search> [version]")
        val message = channel.createEmbed {
            it.apply {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                var desc = "Searching up classes for **${mappingsContainerGetter.first}**."
                if (!mappingsContainerGetter.second) desc += "\nThis mappings version is not yet cached, might take some time to download."
                setDescription(desc)
            }
        }.block() ?: throw NullPointerException("Unknown Message!")

        try {
            val mappingsContainer = mappingsContainerGetter.third.invoke()

            val classes = mutableMapOf<Class, MatchResult>()
            val searchKeyOnly = searchTerm.replace('.', '/')
            mappingsContainer.classes.forEach { clazz ->
                if (!classes.contains(clazz)) {
                    if (clazz.intermediaryName.containsOrMatchWildcard(searchKeyOnly).takeIf { it.matched }?.also { classes[clazz] = it }?.matched != true) {
                        if (clazz.mappedName.containsOrMatchWildcard(searchKeyOnly).takeIf { it.matched }?.also { classes[clazz] = it }?.matched != true) {
                            if (clazz.obfName.client.containsOrMatchWildcard(searchKeyOnly).takeIf { it.matched }?.also { classes[clazz] = it }?.matched != true) {
                                if (clazz.obfName.server.containsOrMatchWildcard(searchKeyOnly).takeIf { it.matched }?.also { classes[clazz] = it }?.matched != true) {
                                    clazz.obfName.merged.containsOrMatchWildcard(searchKeyOnly).takeIf { it.matched }?.also { classes[clazz] = it }
                                }
                            }
                        }
                    }
                }
            }
            val sortedClasses = classes.entries.sortedByDescending { it.value.selfTerm?.similarityOnNull(it.value.matchStr) }.map { it.key }
            if (sortedClasses.isEmpty())
                throw NullPointerException("No results found!")
            var page = 0
            val maxPage = ceil(sortedClasses.size / 5.0).toInt()
            message.edit { it.setEmbed { it.buildMessage(sortedClasses, mappingsContainer, page, user, maxPage) } }.subscribe { msg ->
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
                                        msg.edit { it.setEmbed { it.buildMessage(sortedClasses, mappingsContainer, page, user, maxPage) } }.subscribe()
                                    }
                                } else if (unicode.raw == "➡") {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                    if (page < maxPage - 1) {
                                        page++
                                        msg.edit { it.setEmbed { it.buildMessage(sortedClasses, mappingsContainer, page, user, maxPage) } }.subscribe()
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

private fun EmbedCreateSpec.buildMessage(sortedClasses: List<Class>, mappingsContainer: MappingsContainer, page: Int, author: User, maxPage: Int) {
    setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
    setTimestampToNow()
    if (maxPage > 1) setTitle("List of ${mappingsContainer.name} Mappings (Page ${page + 1}/$maxPage)")
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