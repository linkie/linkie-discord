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
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.min

class QueryClassMethod(private val namespace: Namespace?) : CommandBase {
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
        mappingsProvider.injectDefaultVersion(namespace.getDefaultProvider(cmd, channel.id?.asLong()))
        if (mappingsProvider.isEmpty())
            throw IllegalStateException("Invalid Default Version! Linkie might be reloading its cache right now.")
        val message = channel.createEmbed {
            it.apply {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                var desc = "Searching up classes for **${namespace.id} ${mappingsProvider.version}**."
                if (!mappingsProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
                setDescription(desc)
            }
        }.block() ?: throw NullPointerException("Unknown Message!")
        try {
            val mappingsContainer = mappingsProvider.mappingsContainer!!.invoke()
            val classes = mutableMapOf<Class, MatchResult>()
            val searchKey = args.first().replace('.', '/')
            mappingsContainer.classes.forEach { clazz ->
                if (!classes.contains(clazz)) {
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
            val sortedClasses = classes.entries.sortedByDescending { it.value.selfTerm?.similarityOnNull(it.value.matchStr) }.map { it.key }
            if (sortedClasses.isEmpty()) {
                if (searchKey.startsWith("func_") || searchKey.startsWith("method_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a method!")
                } else if (searchKey.startsWith("field_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a field!")
                }
                throw NullPointerException("No results found!")
            }
            var page = 0
            val maxPage = ceil(sortedClasses.size / 5.0).toInt()
            message.edit { it.setEmbed { it.buildMessage(namespace,sortedClasses, mappingsContainer, page, user, maxPage) } }.subscribe { msg ->
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
                                        msg.edit { it.setEmbed { it.buildMessage(namespace,sortedClasses, mappingsContainer, page, user, maxPage) } }.subscribe()
                                    }
                                } else if (unicode.raw == "➡") {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                    if (page < maxPage - 1) {
                                        page++
                                        msg.edit { it.setEmbed { it.buildMessage(namespace,sortedClasses, mappingsContainer, page, user, maxPage) } }.subscribe()
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
            t.printStackTrace()
            try {
                message.edit { it.setEmbed { it.generateThrowable(t, user) } }.subscribe()
            } catch (throwable2: Throwable) {
                throwable2.addSuppressed(t)
                throw throwable2
            }
        }
    }

    private fun EmbedCreateSpec.buildMessage(namespace: Namespace, sortedClasses: List<Class>, mappingsContainer: MappingsContainer, page: Int, author: User, maxPage: Int) {
        if (mappingsContainer.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${mappingsContainer.mappingSource}", author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${mappingsContainer.name} Mappings (Page ${page + 1}/$maxPage)")
        var desc = ""
        sortedClasses.dropAndTake(5 * page, 5).forEach {
            if (desc.isNotEmpty())
                desc += "\n\n"
            val obfMap = LinkedHashMap<String, String>()
            if (!it.obfName.isMerged()) {
                if (it.obfName.client != null) obfMap["client"] = it.obfName.client!!
                if (it.obfName.server != null) obfMap["server"] = it.obfName.server!!
            }
            desc += "**MC ${mappingsContainer.version}: ${it.mappedName ?: it.intermediaryName}**\n" +
                    "__Name__: " + (if (it.obfName.isEmpty()) "" else if (it.obfName.isMerged()) "${it.obfName.merged} => " else "${obfMap.entries.joinToString { "${it.key}=**${it.value}**" }} => ") +
                    "`${it.intermediaryName}`" + (if (it.mappedName == null || it.mappedName == it.intermediaryName) "" else " => `${it.mappedName}`")
            if (namespace.supportsAW()) {
                desc += "\n__AW__: `<access> class ${it.mappedName ?: it.intermediaryName}`"
            }
        }
        setDescription(desc.substring(0, min(desc.length, 2000)))
    }

    override fun getName(): String? =
            if (namespace != null) namespace.id.capitalize() + " Class Query"
            else "Class Query"

    override fun getDescription(): String? =
            if (namespace != null) "Queries ${namespace.id} class entries."
            else "Queries class entries."
}