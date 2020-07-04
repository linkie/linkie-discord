package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.min

class QueryTranslateMethodCommand(private val source: Namespace, private val target: Namespace) : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        source.validateNamespace()
        target.validateNamespace()
        args.validateUsage(1..2, "$cmd <search> [version]")
        val sourceMappingsProvider = if (args.size == 1) MappingsProvider.empty(source) else source.getProvider(args.last())
        val allVersions = source.getAllSortedVersions().toMutableList()
        allVersions.retainAll(target.getAllSortedVersions())
        if (sourceMappingsProvider.isEmpty() && args.size == 2) {
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (allVersions.size > 20)
                        allVersions.take(20).joinToString(", ") + ", etc"
                    else allVersions.joinToString(", "))
        }
        sourceMappingsProvider.injectDefaultVersion(source.getProvider(allVersions.first()))
        sourceMappingsProvider.validateDefaultVersionNotEmpty()
        val targetMappingsProvider = target.getProvider(sourceMappingsProvider.version!!)
        if (targetMappingsProvider.isEmpty()) {
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (allVersions.size > 20)
                        allVersions.take(20).joinToString(", ") + ", etc"
                    else allVersions.joinToString(", "))
        }
        require(!args.first().replace('.', '/').contains('/')) { "Query with classes are not available with translating queries." }
        val searchTerm = args.first().replace('.', '/').onlyClass()
        val sourceVersion = sourceMappingsProvider.version!!
        val targetVersion = targetMappingsProvider.version!!
        val message = AtomicReference<Message?>()
        var page = 0
        val maxPage = AtomicInteger(-1)
        val remappedMethods = ValueKeeper(Duration.ofMinutes(2)) { build(searchTerm, source.getProvider(sourceVersion), target.getProvider(targetVersion), user, message, channel, maxPage) }
        message.get().editOrCreate(channel) { buildMessage(remappedMethods.get(), sourceVersion, page, user, maxPage.get()) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(remappedMethods.timeToKeep) {
                if (maxPage.get() > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        msg.editOrCreate(channel) { buildMessage(remappedMethods.get(), sourceVersion, page, user, maxPage.get()) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (maxPage.get() > 1) register("➡") {
                    if (page < maxPage.get() - 1) {
                        page++
                        msg.editOrCreate(channel) { buildMessage(remappedMethods.get(), sourceVersion, page, user, maxPage.get()) }.subscribe()
                    }
                }
            }.build(msg, user)
        }
    }

    private fun build(
            searchTerm: String,
            sourceProvider: MappingsProvider,
            targetProvider: MappingsProvider,
            user: User,
            message: AtomicReference<Message?>,
            channel: MessageChannel,
            maxPage: AtomicInteger
    ): MutableMap<String, String> {
        if (sourceProvider.cached!!) message.get().editOrCreate(channel) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up methods for **${sourceProvider.namespace.id} ${sourceProvider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!sourceProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            setDescription(desc)
        }.block().also { message.set(it) }
        if (targetProvider.cached!!) message.get().editOrCreate(channel) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up methods for **${targetProvider.namespace.id} ${targetProvider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!targetProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            setDescription(desc)
        }.block()
        return getCatching(message.get(), channel, user) {
            val sourceMappings = sourceProvider.mappingsContainer!!.invoke()
            val targetMappings = targetProvider.mappingsContainer!!.invoke()
            val sourceMethods = mutableMapOf<Method, Class>()
            sourceMappings.classes.forEach { clazz ->
                clazz.methods.forEach {
                    if (it.intermediaryName.onlyClass().equals(searchTerm, true) || it.mappedName?.onlyClass()?.equals(searchTerm, true) == true) {
                        sourceMethods[it] = clazz
                    }
                }
            }
            val remappedMethods = mutableMapOf<String, String>()
            sourceMethods.forEach { (sourceMethod, sourceClassParent) ->
                val obfName = sourceMethod.obfName.merged!!
                val obfDesc = sourceMethod.obfDesc.merged!!
                val parentObfName = sourceClassParent.obfName.merged!!
                val targetClass = targetMappings.getClassByObfName(parentObfName) ?: return@forEach
                val targetMethod = targetClass.methods.firstOrNull { it.obfName.merged == obfName && it.obfDesc.merged == obfDesc } ?: return@forEach
                remappedMethods[(sourceClassParent.mappedName ?: sourceClassParent.intermediaryName).onlyClass() + "." + (sourceMethod.mappedName
                        ?: sourceMethod.intermediaryName)] = (targetClass.mappedName ?: targetClass.intermediaryName).onlyClass() + "." + (targetMethod.mappedName ?: targetMethod.intermediaryName)
            }
            if (remappedMethods.isEmpty()) {
                if (searchTerm.startsWith("field_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a field!")
                } else if (searchTerm.startsWith("class_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a class!")
                }
                throw NullPointerException("No results found!")
            }

            maxPage.set(ceil(remappedMethods.size / 5.0).toInt())
            return@getCatching remappedMethods
        }
    }

    private fun EmbedCreateSpec.buildMessage(remappedMethods: MutableMap<String, String>, version: String, page: Int, author: User, maxPage: Int) {
        setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings")
        var desc = ""
        remappedMethods.keys.dropAndTake(5 * page, 5).forEach {
            if (desc.isNotEmpty())
                desc += "\n"
            val targetName = remappedMethods[it]
            desc += "**MC $version: $it => `$targetName`**\n"
        }
        setDescription(desc.substring(0, min(desc.length, 2000)))
    }

    override fun getName(): String = "${source.id.capitalize()}->${target.id.capitalize()} Method Command"
    override fun getDescription(): String = "Query ${source.id}->${target.id} methods."
}