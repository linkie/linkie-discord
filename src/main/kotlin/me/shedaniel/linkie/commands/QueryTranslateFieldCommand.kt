package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.min

class QueryTranslateFieldCommand(private val source: Namespace, private val target: Namespace) : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (source.reloading)
            throw IllegalStateException("Mappings (ID: ${source.id}) is reloading now, please try again in 5 seconds.")
        if (target.reloading)
            throw IllegalStateException("Mappings (ID: ${target.id}) is reloading now, please try again in 5 seconds.")
        if (args.size !in 1..2)
            throw InvalidUsageException("!$cmd <search> [version]")
        val sourceMappingsProvider = if (args.size == 1) Namespace.MappingsProvider.ofEmpty() else source.getProvider(args.last())
        val allVersions = source.getAllSortedVersions().toMutableList()
        allVersions.retainAll(target.getAllSortedVersions())
        if (sourceMappingsProvider.isEmpty() && args.size == 2) {
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (allVersions.size > 20)
                        allVersions.take(20).joinToString(", ") + ", etc"
                    else allVersions.joinToString(", "))
        }
        sourceMappingsProvider.injectDefaultVersion(source.getProvider(allVersions.first()))
        if (sourceMappingsProvider.isEmpty())
            throw IllegalStateException("Invalid Default Version! Linkie might be reloading its cache right now.")
        val targetMappingsProvider = target.getProvider(sourceMappingsProvider.version!!)
        if (targetMappingsProvider.isEmpty()) {
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (allVersions.size > 20)
                        allVersions.take(20).joinToString(", ") + ", etc"
                    else allVersions.joinToString(", "))
        }
        val searchTerm = args.first().replace('.', '/').onlyClass()
        var message = channel.createEmbed {
            it.apply {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                var desc = "Searching up fields for **${source.id} ${sourceMappingsProvider.version}**."
                if (!sourceMappingsProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
                setDescription(desc)
            }
        }.block() ?: throw NullPointerException("Unknown Message!")
        try {
            val sourceMappings = sourceMappingsProvider.mappingsContainer!!.invoke()
            message = message.edit {
                it.setEmbed {
                    it.apply {
                        setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                        setTimestampToNow()
                        var desc = "Searching up fields for **${target.id} ${targetMappingsProvider.version}**."
                        if (!targetMappingsProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
                        setDescription(desc)
                    }
                }
            }.block() ?: throw NullPointerException("Unknown Message!")
            val targetMappings = targetMappingsProvider.mappingsContainer!!.invoke()
            val sourceFields = mutableMapOf<Field, Class>()
            sourceMappings.classes.forEach { clazz ->
                clazz.fields.forEach {
                    if (it.intermediaryName.onlyClass().equals(searchTerm, true) || it.mappedName?.onlyClass()?.equals(searchTerm, true) == true) {
                        sourceFields[it] = clazz
                    }
                }
            }
            val remappedFields = mutableMapOf<String, String>()
            sourceFields.forEach { (sourceField, sourceClassParent) ->
                val obfName = sourceField.obfName.merged!!
                val parentObfName = sourceClassParent.obfName.merged!!
                val targetClass = targetMappings.getClassByObfName(parentObfName) ?: return@forEach
                val targetField = targetClass.fields.firstOrNull { it.obfName.merged == obfName } ?: return@forEach
                remappedFields[(sourceClassParent.mappedName ?: sourceClassParent.intermediaryName).onlyClass() + "." + (sourceField.mappedName
                        ?: sourceField.intermediaryName)] = (targetClass.mappedName ?: targetClass.intermediaryName).onlyClass() + "." + (targetField.mappedName ?: targetField.intermediaryName)
            }
            if (remappedFields.isEmpty()) {
                if (searchTerm.startsWith("method_") || searchTerm.startsWith("func_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a method!")
                } else if (searchTerm.startsWith("class_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a class!")
                }
                throw NullPointerException("No results found!")
            }
            var page = 0
            val maxPage = ceil(remappedFields.size / 5.0).toInt()
            val sourceFieldsList = remappedFields.keys.toList()
            message.edit { it.setEmbed { it.buildMessage(remappedFields, sourceFieldsList, sourceMappings.version, page, user, maxPage) } }.subscribe { msg ->
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
                                        msg.edit { it.setEmbed { it.buildMessage(remappedFields, sourceFieldsList, sourceMappings.version, page, user, maxPage) } }.subscribe()
                                    }
                                } else if (unicode.raw == "➡") {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                    if (page < maxPage - 1) {
                                        page++
                                        msg.edit { it.setEmbed { it.buildMessage(remappedFields, sourceFieldsList, sourceMappings.version, page, user, maxPage) } }.subscribe()
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

    private fun EmbedCreateSpec.buildMessage(remappedFields: MutableMap<String, String>, sourceFieldsList: List<String>, version: String, page: Int, author: User, maxPage: Int) {
        setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings (Page ${page + 1}/$maxPage)")
        var desc = ""
        sourceFieldsList.dropAndTake(5 * page, 5).forEach {
            if (desc.isNotEmpty())
                desc += "\n"
            val targetName = remappedFields[it]
            desc += "**MC $version: $it => `$targetName`**\n"
        }
        setDescription(desc.substring(0, min(desc.length, 2000)))
    }

    override fun getName(): String = "${source.id.capitalize()}->${target.id.capitalize()} Field Command"
    override fun getDescription(): String = "Query ${source.id}->${target.id} fields."
}