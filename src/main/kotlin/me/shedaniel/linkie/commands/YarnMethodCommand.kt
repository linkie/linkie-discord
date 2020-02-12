package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.similarity
import me.shedaniel.linkie.utils.similarityOnNull
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.min

object YarnMethodCommand : AYarnMethodCommand({ if (it.id.asLong() == 602959845842485258) "1.2.5" else if (it.id.asLong() == 661088839464386571) "1.14.3" else latestYarn }) {
    override fun getName(): String? = "Yarn Method Command"
    override fun getDescription(): String? = "Query yarn methods."
}

object POMFMethodCommand : AYarnMethodCommand({ "b1.7.3" }) {
    override fun getName(): String? = "POMF Method Command"
    override fun getDescription(): String? = "Query pomf methods."
}

open class AYarnMethodCommand(private val defaultVersion: (MessageChannel) -> String, private val isYarn: Boolean = true) : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isEmpty())
            throw InvalidUsageException("!$cmd <search> [version]")
        val mappingsContainerGetter = if (isYarn)
            tryLoadYarnMappingContainer(args.last(), getYarnMappingsContainer(defaultVersion.invoke(channel)))
        else tryLoadMCPMappingContainer(args.last(), getMCPMappingsContainer(defaultVersion.invoke(channel)))
        var searchTerm = args.joinToString(" ").replace('.', '/')
        if (mappingsContainerGetter.first == args.last()) {
            searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf(' '))
        }
        if (searchTerm.contains(' '))
            throw InvalidUsageException("!$cmd <search> [version]")
        val hasClass = searchTerm.contains('/')
        val hasWildcard = (hasClass && searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass() == "*") || searchTerm.onlyClass('/') == "*"

        val message = channel.createEmbed {
            it.apply {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                var desc = "Searching up methods for **${mappingsContainerGetter.first}**."
                if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
                if (!mappingsContainerGetter.second) desc += "\nThis mappings version is not yet cached, might take some time to download."
                setDescription(desc)
            }
        }.block() ?: throw NullPointerException("Unknown Message!")

        try {
            val mappingsContainer = mappingsContainerGetter.third()

            val classes = mutableMapOf<Class, FindMethodMethod>()
            if (hasClass) {
                val clazzKey = searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass()
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
            val methodKey = searchTerm.onlyClass('/')
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
                methodKey == "*" && (!hasClass || searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass() == "*") -> {
                    // Class and method both wildcard
                    methods.entries.sortedBy { it.key.method.intermediaryName }.sortedBy {
                        it.key.parent.mappedName?.onlyClass() ?: it.key.parent.intermediaryName
                    }.map { it.key }
                }
                methodKey == "*" -> {
                    // Only method wildcard
                    val classKey = searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass()
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
                hasClass && searchTerm.substring(0, searchTerm.lastIndexOf('/')).onlyClass() != "*" -> {
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
            if (sortedMethods.isEmpty())
                throw NullPointerException("No results found!")
            var page = 0
            val maxPage = ceil(sortedMethods.size / 5.0).toInt()
            message.edit { it.setEmbed { it.buildMessage(sortedMethods, mappingsContainer, page, user, maxPage, isYarn) } }.subscribe { msg ->
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
                                        msg.edit { it.setEmbed { it.buildMessage(sortedMethods, mappingsContainer, page, user, maxPage, isYarn) } }.subscribe()
                                    }
                                } else if (unicode.raw == "➡") {
                                    msg.removeReaction(it.emoji, it.userId).subscribe()
                                    if (page < maxPage - 1) {
                                        page++
                                        msg.edit { it.setEmbed { it.buildMessage(sortedMethods, mappingsContainer, page, user, maxPage, isYarn) } }.subscribe()
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

private enum class FindMethodMethod {
    INTERMEDIARY,
    MAPPED,
    OBF_CLIENT,
    OBF_SERVER,
    OBF_MERGED,
    WILDCARD
}

private data class MethodWrapper(val method: Method, val parent: Class, val cm: FindMethodMethod)

private fun EmbedCreateSpec.buildMessage(sortedMethods: List<MethodWrapper>, mappingsContainer: MappingsContainer, page: Int, author: User, maxPage: Int, isYarn: Boolean) {
    setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
    setTimestampToNow()
    if (maxPage > 1) setTitle("List of ${mappingsContainer.name} Mappings (Page ${page + 1}/$maxPage)")
    var desc = ""
    sortedMethods.dropAndTake(5 * page, 5).forEach {
        if (!desc.isEmpty())
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
//        desc += "\n__Descriptor__: `${if (it.method.mappedName == null) it.method.intermediaryName else it.method.mappedName}${it.method.mappedDesc
//                ?: it.method.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)}`"
        if (isYarn) {
            desc += "\n__Mixin Target__: `L${it.parent.mappedName
                    ?: it.parent.intermediaryName};${if (it.method.mappedName == null) it.method.intermediaryName else it.method.mappedName}${it.method.mappedDesc
                    ?: it.method.intermediaryDesc.mapIntermediaryDescToNamed(mappingsContainer)}`"
        } else {
            desc += "\n__AT__: `public ${(it.parent.intermediaryName).replace('/', '.')}" +
                    " ${it.method.intermediaryName}${it.method.obfDesc.merged!!.mapObfDescToNamed(mappingsContainer)}" +
                    " # ${if (it.method.mappedName == null) it.method.intermediaryName else it.method.mappedName}`"
        }
    }
    setDescription(desc.substring(0, min(desc.length, 2000)))
}

private fun String.mapObfDescToNamed(container: MappingsContainer): String {
    if (startsWith('(') && contains(')')) {
        val split = split(')')
        val parametersOG = split[0].substring(1).toCharArray()
        val returnsOG = split[1].toCharArray()
        val parametersUnmapped = mutableListOf<String>()
        val returnsUnmapped = mutableListOf<String>()

        var lastT: String? = null
        for (char in parametersOG) {
            when {
                lastT != null && char == ';' -> {
                    parametersUnmapped.add(lastT)
                    lastT = null
                }
                lastT != null -> {
                    lastT += char
                }
                char == 'L' -> {
                    lastT = ""
                }
                else -> parametersUnmapped.add(char.toString())
            }
        }
        for (char in returnsOG) {
            when {
                lastT != null && char == ';' -> {
                    returnsUnmapped.add(lastT)
                    lastT = null
                }
                lastT != null -> {
                    lastT += char
                }
                char == 'L' -> {
                    lastT = ""
                }
                else -> returnsUnmapped.add(char.toString())
            }
        }
        return "(" + parametersUnmapped.joinToString("") {
            if (it.length != 1) {
                "L${container.getClassByObfName(it)?.intermediaryName ?: it};"
            } else
                it
        } + ")" + returnsUnmapped.joinToString("") {
            if (it.length != 1) {
                "L${container.getClassByObfName(it)?.intermediaryName ?: it};"
            } else
                it
        }
    }
    return this
}
