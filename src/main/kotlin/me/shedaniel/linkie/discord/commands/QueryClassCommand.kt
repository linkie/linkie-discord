package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.*
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

class QueryClassCommand(private val namespace: Namespace?) : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        if (this.namespace == null) {
            args.validateUsage(prefix, 2..3, "$cmd <namespace> <search> [version]\nDo !namespaces for list of namespaces.")
        } else args.validateUsage(prefix, 1..2, "$cmd <search> [version]")
        val namespace = this.namespace ?: (Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
            ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", ")))
        if (this.namespace == null) args.removeAt(0)
        namespace.validateNamespace()
        namespace.validateGuild(event)

        val mappingsProvider = if (args.size == 1) MappingsProvider.empty(namespace) else namespace.getProvider(args.last())
        if (mappingsProvider.isEmpty() && args.size == 2) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException(
                "Invalid Version: " + args.last() + "\nVersions: " +
                        if (list.size > 20)
                            list.take(20).joinToString(", ") + ", etc"
                        else list.joinToString(", ")
            )
        }
        mappingsProvider.injectDefaultVersion(namespace.getDefaultProvider {
            if (namespace == YarnNamespace) when (channel.id.asLong()) {
                602959845842485258 -> "legacy"
                661088839464386571 -> "patchwork"
                else -> namespace.getDefaultMappingChannel()
            } else namespace.getDefaultMappingChannel()
        })
        mappingsProvider.validateDefaultVersionNotEmpty()
        val message = AtomicReference<Message?>()
        val version = mappingsProvider.version!!
        var page = 0
        val maxPage = AtomicInteger(-1)
        val searchKey = args.first().replace('.', '/')
        val classes = ValueKeeper(Duration.ofMinutes(2)) { build(event.message, searchKey, namespace.getProvider(version), user, message, channel, maxPage) }
        message.editOrCreate(channel, event.message) { buildMessage(namespace, classes.get().second, classes.get().first, page, user, maxPage.get()) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(classes.timeToKeep) {
                if (maxPage.get() > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        message.editOrCreate(channel, event.message) { buildMessage(namespace, classes.get().second, classes.get().first, page, user, maxPage.get()) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (maxPage.get() > 1) register("➡") {
                    if (page < maxPage.get() - 1) {
                        page++
                        message.editOrCreate(channel, event.message) { buildMessage(namespace, classes.get().second, classes.get().first, page, user, maxPage.get()) }.subscribe()
                    }
                }
            }.build(msg, user)
        }
    }

    private fun build(
        previous: Message,
        searchKey: String,
        provider: MappingsProvider,
        user: User,
        message: AtomicReference<Message?>,
        channel: MessageChannel,
        maxPage: AtomicInteger,
    ): Pair<MappingsContainer, List<Class>> {
        val hasWildcard = searchKey == "*"
        if (!provider.cached!!) message.editOrCreate(channel, previous) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            buildSafeDescription {
                append("Searching up classes for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again.")
                if (!provider.cached!!) append("\nThis mappings version is not yet cached, might take some time to download.")
            }
        }.block()
        return getCatching(message, channel, user) {
            val mappingsContainer = provider.mappingsContainer!!.invoke()
            val classes = mutableMapOf<Class, MatchResult>()

            mappingsContainer.classes.forEach { clazz ->
                if (hasWildcard) {
                    classes[clazz] = MatchResult(true, null, null)
                } else if (!classes.contains(clazz)) {
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
            val sortedClasses = when {
                hasWildcard -> classes.entries.sortedBy { it.key.intermediaryName }
                else -> classes.entries.sortedByDescending { it.value.selfTerm?.similarityOnNull(it.value.matchStr) }
            }.map { it.key }
            if (sortedClasses.isEmpty()) {
                if (!searchKey.onlyClass().isValidIdentifier()) {
                    throw NullPointerException("No results found! `${searchKey.onlyClass()}` is not a valid java identifier!")
                } else if (searchKey.startsWith("func_") || searchKey.startsWith("method_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a method!")
                } else if (searchKey.startsWith("field_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a field!")
                } else if ((!searchKey.startsWith("class_") && searchKey.firstOrNull()?.isLowerCase() == true) || searchKey.firstOrNull()?.isDigit() == true) {
                    throw NullPointerException("No results found! `$searchKey` doesn't look like a class!")
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
        buildSafeDescription {
            sortedClasses.dropAndTake(5 * page, 5).forEach { classEntry ->
                if (isNotEmpty())
                    appendLine().appendLine()
                appendLine("**MC ${mappingsContainer.version}: __${classEntry.optimumName}__**")
                append("__Name__: ")
                append(classEntry.obfName.buildString(nonEmptySuffix = " => "))
                append("`${classEntry.intermediaryName}`")
                append(classEntry.mappedName.mapIfNotNullOrNotEquals(classEntry.intermediaryName) { " => `$it`" } ?: "")
                if (namespace.supportsAW()) {
                    appendLine().append("__AW__: `accessible class ${classEntry.optimumName}`")
                } else if (namespace.supportsAT()) {
                    appendLine().append("__AT__: `public ${classEntry.intermediaryName.replace('/', '.')}`")
                }
            }
        }
    }

    override fun getName(): String =
        if (namespace != null) namespace.id.capitalize() + " Class Query"
        else "Class Query"

    override fun getDescription(): String =
        if (namespace != null) "Queries ${namespace.id} class entries."
        else "Queries class entries."
}