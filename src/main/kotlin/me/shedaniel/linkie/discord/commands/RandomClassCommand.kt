package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.*
import java.time.Duration
import java.util.*
import kotlin.math.min

object RandomClassCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.size != 3)
            throw InvalidUsageException("!$cmd <namespace> <version> <amount>\nDo !namespaces for list of namespaces.")
        val namespace = Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
                ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", "))
        if (namespace.reloading)
            throw IllegalStateException("Mappings (ID: ${namespace.id}) is reloading now, please try again in 5 seconds.")
        val mappingsProvider = namespace.getProvider(args[1])
        if (mappingsProvider.isEmpty()) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (list.size > 20)
                        list.take(20).joinToString(", ") + ", etc"
                    else list.joinToString(", "))
        }
        val count = args[2].toIntOrNull()
        if (count == null || count !in 1..20) {
            throw IllegalArgumentException("Invalid Amount: ${args[2]}")
        }
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
            message.edit { it.setEmbed { it.buildMessage(mappingsContainer, count, user) } }.subscribe { msg ->
                if (channel.type.name.startsWith("GUILD_"))
                    msg.removeAllReactions().block()
                msg.subscribeReactions("❌", "🔁")
                api.eventDispatcher.on(ReactionAddEvent::class.java).filter { e -> e.messageId == msg.id }.take(Duration.ofMinutes(15)).subscribe {
                    when (it.userId) {
                        api.selfId.get() -> {
                        }
                        user.id -> {
                            if (!it.emoji.asUnicodeEmoji().isPresent) {
                                msg.removeReaction(it.emoji, it.userId).subscribe()
                            } else {
                                val unicode = it.emoji.asUnicodeEmoji().get()
                                when (unicode.raw) {
                                    "❌" -> msg.delete().subscribe()
                                    "🔁" -> {
                                        message.edit { it.setEmbed { it.buildMessage(mappingsContainer, count, user) } }.subscribe()
                                        msg.removeReaction(it.emoji, it.userId).subscribe()
                                    }
                                    else -> msg.removeReaction(it.emoji, it.userId).subscribe()
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

    private fun EmbedCreateSpec.buildMessage(mappingsContainer: MappingsContainer, count: Int, author: User) {
        val range = mappingsContainer.classes.indices
        val set = mutableSetOf<Int>()
        for (i in 0 until count) randomIndex(range, set)
        if (mappingsContainer.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${mappingsContainer.mappingSource}", author.avatarUrl)
        setTitle("List of Random ${mappingsContainer.name} Classes")
        var desc = ""
        set.sorted().map { mappingsContainer.classes[it] }.forEach { mappingsClass ->
            if (desc.isNotEmpty())
                desc += "\n"
            desc += mappingsClass.mappedName ?: mappingsClass.intermediaryName
        }
        setDescription(desc.substring(0, min(desc.length, 2000)))
        setTimestampToNow()
    }

    private fun randomIndex(range: IntRange, set: MutableSet<Int>): Int {
        val random = range.random()
        if (random in set) return randomIndex(range, set)
        return random.also { set.add(it) }
    }
}