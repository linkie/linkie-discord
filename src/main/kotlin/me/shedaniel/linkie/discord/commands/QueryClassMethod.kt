package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.utils.MatchResult
import me.shedaniel.linkie.utils.containsOrMatchWildcard
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.similarityOnNull
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.min

class QueryClassMethod(private val namespace: Namespace?) : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        if (this.namespace == null) {
            args.validateUsage(2..3, "$cmd <namespace> <search> [version]\nDo !namespaces for list of namespaces.")
        } else args.validateUsage(1..2, "$cmd <namespace> <search> [version]")
        val namespace = this.namespace ?: (Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
                ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", ")))
        if (this.namespace == null) args.removeAt(0)
        namespace.validateNamespace()

        val mappingsProvider = if (args.size == 1) MappingsProvider.empty(namespace) else namespace.getProvider(args.last())
        if (mappingsProvider.isEmpty() && args.size == 2) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (list.size > 20)
                        list.take(20).joinToString(", ") + ", etc"
                    else list.joinToString(", "))
        }
        mappingsProvider.injectDefaultVersion(namespace.getDefaultProvider(cmd, channel.id.asLong()))
        mappingsProvider.validateDefaultVersionNotEmpty()
        val message = AtomicReference<Message?>()
        val version = mappingsProvider.version!!
        var page = 0
        val maxPage = AtomicInteger(-1)
        val searchKey = args.first().replace('.', '/')
        val classes = ValueKeeper(Duration.ofMinutes(2)) { build(searchKey, namespace.getProvider(version), user, message, channel, maxPage) }
        message.get().editOrCreate(channel) { buildMessage(namespace, classes.get().second, classes.get().first, page, user, maxPage.get()) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(classes.timeToKeep) {
                if (maxPage.get() > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        msg.editOrCreate(channel) { buildMessage(namespace, classes.get().second, classes.get().first, page, user, maxPage.get()) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (maxPage.get() > 1) register("➡") {
                    if (page < maxPage.get() - 1) {
                        page++
                        msg.editOrCreate(channel) { buildMessage(namespace, classes.get().second, classes.get().first, page, user, maxPage.get()) }.subscribe()
                    }
                }
            }.build(msg, user)
        }
    }

    private fun build(
            searchKey: String,
            provider: MappingsProvider,
            user: User,
            message: AtomicReference<Message?>,
            channel: MessageChannel,
            maxPage: AtomicInteger
    ): Pair<MappingsContainer, List<Class>> {
        if (provider.cached!!) message.get().editOrCreate(channel) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up classes for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            setDescription(desc)
        }.block().also { message.set(it) }
        return getCatching(message.get(), channel, user) {
            val mappingsContainer = provider.mappingsContainer!!.invoke()
            val classes = mutableMapOf<Class, MatchResult>()

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

            maxPage.set(ceil(sortedClasses.size / 5.0).toInt())
            return@getCatching mappingsContainer to sortedClasses
        }
    }

    private fun EmbedCreateSpec.buildMessage(namespace: Namespace, sortedClasses: List<Class>, mappingsContainer: MappingsContainer, page: Int, author: User, maxPage: Int) {
        if (mappingsContainer.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${mappingsContainer.mappingSource}", author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${mappingsContainer.name} Mappings (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${mappingsContainer.name} Mappings")
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