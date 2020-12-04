package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.*
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.discord.utils.MappingsQuery.get
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.properties.Delegates

class QueryCompoundCommand(private val namespace: Namespace?) : CommandBase {
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
        val searchKey = args.first().replace('.', '/')
        val version = mappingsProvider.version!!
        var page = 0
        val maxPage = AtomicInteger(-1)
        val methods = ValueKeeper(Duration.ofMinutes(2)) { build(event.message, searchKey, namespace.getProvider(version), user, message, channel, maxPage) }
        message.editOrCreate(channel, event.message) { buildMessage(namespace, methods.get().value, methods.get().mappings, page, user, maxPage.get()) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(methods.timeToKeep) {
                if (maxPage.get() > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        message.editOrCreate(channel, event.message) { buildMessage(namespace, methods.get().value, methods.get().mappings, page, user, maxPage.get()) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (maxPage.get() > 1) register("➡") {
                    if (page < maxPage.get() - 1) {
                        page++
                        message.editOrCreate(channel, event.message) { buildMessage(namespace, methods.get().value, methods.get().mappings, page, user, maxPage.get()) }.subscribe()
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
        hasClass: Boolean = searchKey.contains('/'),
        hasWildcard: Boolean = (hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") || searchKey.onlyClass('/') == "*",
    ): QueryResultCompound<List<ResultHolder<*>>> {
        if (!provider.cached!! || hasWildcard) message.editOrCreate(channel, previous) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up entries for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        return getCatching(message, channel, user) {
            val mappingsContainer = provider.get()
            val context = QueryContext(
                provider = MappingsProvider.of(provider.namespace, mappingsContainer.version, mappingsContainer),
                searchKey = searchKey,
            )
            val result: MutableList<ResultHolder<*>> = mutableListOf()
            var classes by Delegates.notNull<ClassResultSequence>()
            var methods by Delegates.notNull<MethodResultSequence>()
            var fields by Delegates.notNull<FieldResultSequence>()
            runBlocking {
                launch {
                    classes = MappingsQuery.queryClasses(context).value
                }
                launch {
                    methods = MappingsQuery.queryMethods(context).value
                }
                launch {
                    fields = MappingsQuery.queryFields(context).value
                }
            }
            result.addAll(classes)
            result.addAll(methods)
            result.addAll(fields)
            result.sortByDescending { it.score }
            maxPage.set(ceil(result.size / 5.0).toInt())
            return@getCatching QueryResultCompound(mappingsContainer, result)
        }
    }

    private fun EmbedCreateSpec.buildMessage(namespace: Namespace, sortedResults: List<ResultHolder<*>>, mappings: MappingsContainer, page: Int, author: User, maxPage: Int) {
        MappingsQuery.buildHeader(this, mappings, page, author, maxPage)
        val metadata = mappings.toMetadata()
        buildSafeDescription {
            sortedResults.dropAndTake(3 * page, 3).forEach { (value, score) ->
                if (isNotEmpty())
                    appendLine().appendLine()
                when {
                    value is Class -> {
                        MappingsQuery.buildClass(this, namespace, value, metadata)
                    }
                    value is Pair<*, *> && value.second is Field -> {
                        MappingsQuery.buildField(this, namespace, value.second as Field, value.first as Class, mappings)
                    }
                    value is Pair<*, *> && value.second is Method -> {
                        MappingsQuery.buildMethod(this, namespace, value.second as Method, value.first as Class, mappings)
                    }
                }
            }
        }
    }
}