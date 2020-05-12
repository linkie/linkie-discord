package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.*
import java.time.Duration
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.min

class QueryMethodCommand(private val namespace: Namespace?) : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (this.namespace == null) {
            if (args.size !in 2..3)
                throw InvalidUsageException("!$cmd <namespace> <search> [version]\n" +
                        "Do !namespaces for list of namespaces.")
        } else if (args.size !in 1..2)
            throw InvalidUsageException("!$cmd <search> [version]")
        val namespace = this.namespace ?: (Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
                ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", ")))
        val mappingsArgs = if (this.namespace == null) args.drop(1).toTypedArray() else args
        if (namespace.reloading)
            throw IllegalStateException("Mappings (ID: ${namespace.id}) is reloading now, please try again in 5 seconds.")

        val mappingsProvider = if (mappingsArgs.size == 1) Namespace.MappingsProvider.ofEmpty() else namespace.getProvider(mappingsArgs.last())
        if (mappingsProvider.isEmpty() && mappingsArgs.size == 2) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException("Invalid Version: " + mappingsArgs.last() + "\nVersions: " +
                    if (list.size > 20)
                        list.take(20).joinToString(", ") + ", etc"
                    else list.joinToString(", "))
        }
        mappingsProvider.injectDefaultVersion(namespace.getDefaultProvider(cmd, channel.id?.asLong()))
        if (mappingsProvider.isEmpty())
            throw IllegalStateException("Invalid Default Version! Linkie might be reloading its cache right now.")
        val searchKey = mappingsArgs.first().replace('.', '/')
        val hasClass = searchKey.contains('/')
        val hasWildcard = (hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") || searchKey.onlyClass('/') == "*"

        val message = if (mappingsProvider.cached!! && !hasWildcard) null else {
            channel.createEmbed {
                it.apply {
                    setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                    setTimestampToNow()
                    var desc = "Searching up methods for **${namespace.id} ${mappingsProvider.version}**.\nIf you are stuck with this message, please do the command again."
                    if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
                    if (!mappingsProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
                    setDescription(desc)
                }
            }.block() ?: throw NullPointerException("Unknown Message!")
        }
        try {
            val mappingsContainer = mappingsProvider.mappingsContainer!!.invoke()
            val classes = mutableMapOf<Class, FindMethodMethod>()
            if (hasClass) {
                val clazzKey = searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass()
                if (clazzKey == "*") {
                    mappingsContainer.classes.forEach { classes[it] = FindMethodMethod.WILDCARD }
                } else {
                    mappingsContainer.classes.forEach { clazz ->
                        when {
                            clazz.intermediaryName.onlyClass().contains(clazzKey, true) -> classes[clazz] = FindMethodMethod.INTERMEDIARY
                            clazz.mappedName?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindMethodMethod.MAPPED
                            clazz.obfName.client?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindMethodMethod.OBF_CLIENT
                            clazz.obfName.server?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindMethodMethod.OBF_SERVER
                            clazz.obfName.merged?.onlyClass()?.contains(clazzKey, true) == true -> classes[clazz] = FindMethodMethod.OBF_MERGED
                        }
                    }
                }
            } else mappingsContainer.classes.forEach { classes[it] = FindMethodMethod.WILDCARD }
            val methods = mutableMapOf<MethodWrapper, FindMethodMethod>()
            val methodKey = searchKey.onlyClass('/')
            if (methodKey == "*") {
                classes.forEach { (clazz, cm) ->
                    clazz.methods.forEach { method ->
                        if (methods.none { it.key.method == method })
                            methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.WILDCARD
                    }
                }
            } else {
                classes.forEach { (clazz, cm) ->
                    clazz.methods.forEach { method ->
                        if (methods.none { it.key.method == method })
                            when {
                                method.intermediaryName.contains(methodKey, true) -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.INTERMEDIARY
                                method.mappedName?.contains(methodKey, true) == true -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.MAPPED
                                method.obfName.client?.contains(methodKey, true) == true -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.OBF_CLIENT
                                method.obfName.server?.contains(methodKey, true) == true -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.OBF_SERVER
                                method.obfName.merged?.contains(methodKey, true) == true -> methods[MethodWrapper(method, clazz, cm)] = FindMethodMethod.OBF_MERGED
                            }
                    }
                }
            }
            val sortedMethods = when {
                methodKey == "*" && (!hasClass || searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") -> {
                    // Class and method both wildcard
                    methods.entries.sortedBy { it.key.method.intermediaryName }.sortedBy {
                        it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                    }.map { it.key }
                }
                methodKey == "*" -> {
                    // Only method wildcard
                    val classKey = searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass()
                    methods.entries.sortedBy { it.key.method.intermediaryName }.sortedByDescending {
                        when (it.key.cm) {
                            FindMethodMethod.MAPPED -> it.key.parent.mappedName!!.onlyClass()
                            FindMethodMethod.OBF_CLIENT -> it.key.parent.obfName.client!!.onlyClass()
                            FindMethodMethod.OBF_SERVER -> it.key.parent.obfName.server!!.onlyClass()
                            FindMethodMethod.OBF_MERGED -> it.key.parent.obfName.merged!!.onlyClass()
                            FindMethodMethod.INTERMEDIARY -> it.key.parent.intermediaryName.onlyClass()
                            else -> null
                        }.similarityOnNull(classKey)
                    }.map { it.key }
                }
                hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() != "*" -> {
                    // has class
                    methods.entries.sortedByDescending {
                        when (it.value) {
                            FindMethodMethod.MAPPED -> it.key.method.mappedName!!
                            FindMethodMethod.OBF_CLIENT -> it.key.method.obfName.client!!
                            FindMethodMethod.OBF_SERVER -> it.key.method.obfName.server!!
                            FindMethodMethod.OBF_MERGED -> it.key.method.obfName.merged!!
                            else -> it.key.method.intermediaryName
                        }.onlyClass().similarity(methodKey)
                    }.sortedBy {
                        it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                    }.map { it.key }
                }
                else -> {
                    methods.entries.sortedBy {
                        it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                    }.sortedByDescending {
                        when (it.value) {
                            FindMethodMethod.MAPPED -> it.key.method.mappedName!!
                            FindMethodMethod.OBF_CLIENT -> it.key.method.obfName.client!!
                            FindMethodMethod.OBF_SERVER -> it.key.method.obfName.server!!
                            FindMethodMethod.OBF_MERGED -> it.key.method.obfName.merged!!
                            else -> it.key.method.intermediaryName
                        }.onlyClass().similarity(methodKey)
                    }.map { it.key }
                }
            }
            if (sortedMethods.isEmpty()) {
                if (searchKey.startsWith("class_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a class!")
                } else if (searchKey.startsWith("field_")) {
                    throw NullPointerException("No results found! `$searchKey` looks like a field!")
                }
                throw NullPointerException("No results found!")
            }
            var page = 0
            val maxPage = ceil(sortedMethods.size / 5.0).toInt()
            message.editOrCreate(channel) { it.buildMessage(namespace, sortedMethods, mappingsContainer, page, user, maxPage) }.subscribe { msg ->
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
                                        msg.edit { it.setEmbed { it.buildMessage(namespace, sortedMethods, mappingsContainer, page, user, maxPage) } }.subscribe()
                                    }
                                } else if (unicode.raw == "➡") {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                    if (page < maxPage - 1) {
                                        page++
                                        msg.edit { it.setEmbed { it.buildMessage(namespace, sortedMethods, mappingsContainer, page, user, maxPage) } }.subscribe()
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
                message.editOrCreate(channel) { it.generateThrowable(t, user) }.subscribe()
            } catch (throwable2: Throwable) {
                throwable2.addSuppressed(t)
                throw throwable2
            }
        }
    }

    private enum class FindMethodMethod {
        INTERMEDIARY,
        MAPPED,
        OBF_CLIENT,
        OBF_SERVER,
        OBF_MERGED,
        WILDCARD
    }

    private data class MethodWrapper(val method: Method, val parent: Class, val cm: FindMethodMethod)

    private fun EmbedCreateSpec.buildMessage(namespace: Namespace, sortedMethods: List<MethodWrapper>, mappingsContainer: MappingsContainer, page: Int, author: User, maxPage: Int) {
        if (mappingsContainer.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${mappingsContainer.mappingSource}", author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${mappingsContainer.name} Mappings (Page ${page + 1}/$maxPage)")
        var desc = ""
        sortedMethods.dropAndTake(5 * page, 5).forEach {
            if (desc.isNotEmpty())
                desc += "\n\n"
            val obfMap = LinkedHashMap<String, String>()
            if (!it.method.obfName.isMerged()) {
                if (it.method.obfName.client != null) obfMap["client"] = it.method.obfName.client!!
                if (it.method.obfName.server != null) obfMap["server"] = it.method.obfName.server!!
            }
            desc += "**MC ${mappingsContainer.version}: ${it.parent.mappedName
                    ?: it.parent.intermediaryName}.${it.method.mappedName ?: it.method.intermediaryName}**\n" +
                    "__Name__: " + (if (it.method.obfName.isEmpty()) "" else if (it.method.obfName.isMerged()) "${it.method.obfName.merged} => " else "${obfMap.entries.joinToString { "${it.key}=**${it.value}**" }} => ") +
                    "`${it.method.intermediaryName}`" + (if (it.method.mappedName == null || it.method.mappedName == it.method.intermediaryName) "" else " => `${it.method.mappedName}`")
            if (namespace.supportsMixin()) {
                desc += "\n__Mixin Target__: `L${it.parent.mappedName
                        ?: it.parent.intermediaryName};${if (it.method.mappedName == null) it.method.intermediaryName else it.method.mappedName}${it.method.mappedDesc
                        ?: it.method.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappingsContainer)}`"
            }
            if (namespace.supportsAT()) {
                desc += "\n__AT__: `public ${(it.parent.intermediaryName).replace('/', '.')}" +
                        " ${it.method.intermediaryName}${it.method.obfDesc.merged!!.mapObfDescToNamed(mappingsContainer)}" +
                        " # ${if (it.method.mappedName == null) it.method.intermediaryName else it.method.mappedName}`"
            } else if (namespace.supportsAW()) {
                desc += "\n__AW__: `<access> method ${it.parent.mappedName ?: it.parent.intermediaryName} ${it.method.mappedName ?: it.method.intermediaryName} " +
                        "${it.method.mappedDesc ?: it.method.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappingsContainer)}`"
            }
        }
        setDescription(desc.substring(0, min(desc.length, 2000)))
    }

    private fun String.mapObfDescToNamed(container: MappingsContainer): String =
            remapMethodDescriptor { container.getClassByObfName(it)?.intermediaryName ?: it }

    override fun getName(): String? =
            if (namespace != null) namespace.id.capitalize() + " Method Query"
            else "Method Query"

    override fun getDescription(): String? =
            if (namespace != null) "Queries ${namespace.id} method entries."
            else "Queries method entries."
}