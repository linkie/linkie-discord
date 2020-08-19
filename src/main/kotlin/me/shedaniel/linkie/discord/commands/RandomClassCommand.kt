package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.*
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

object RandomClassCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateUsage(3, "$cmd <namespace> <version> <amount>\nDo !namespaces for list of namespaces.")
        val namespace = Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
                ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", "))
        namespace.validateNamespace()
        val mappingsProvider = namespace.getProvider(args[1])
        if (mappingsProvider.isEmpty()) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (list.size > 20)
                        list.take(20).joinToString(", ") + ", etc"
                    else list.joinToString(", "))
        }
        val count = args[2].toIntOrNull()
        require(count in 1..20) { "Invalid Amount: ${args[2]}" }
        val message = AtomicReference<Message?>()
        val version = mappingsProvider.version!!
        val mappingsContainer = ValueKeeper(Duration.ofMinutes(2)) { build(namespace.getProvider(version), user, message, channel) }
        message.get().editOrCreate(channel) { buildMessage(mappingsContainer.get(), count!!, user) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(mappingsContainer.timeToKeep) {
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                register("🔁") {
                    msg.editOrCreate(channel) { buildMessage(mappingsContainer.get(), count!!, user) }.subscribe()
                }
            }.build(msg, user)
        }
    }

    private fun build(
            provider: MappingsProvider,
            user: User,
            message: AtomicReference<Message?>,
            channel: MessageChannel
    ): MappingsContainer {
        if (!provider.cached!!) message.get().editOrCreate(channel) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up classes for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            setDescription(desc)
        }.block().also { message.set(it) }
        return provider.mappingsContainer!!.invoke()
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