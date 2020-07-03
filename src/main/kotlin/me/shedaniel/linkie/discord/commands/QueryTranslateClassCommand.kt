package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.getClassByObfName
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.min

class QueryTranslateClassCommand(private val source: Namespace, private val target: Namespace) : CommandBase {
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
        val searchTerm = args.first().replace('.', '/').onlyClass()
        val sourceVersion = sourceMappingsProvider.version!!
        val targetVersion = targetMappingsProvider.version!!
        val message = AtomicReference<Message?>()
        var page = 0
        val maxPage = AtomicInteger(-1)
        val remappedClasses = ValueKeeper(Duration.ofMinutes(2)) { build(searchTerm, source.getProvider(sourceVersion), target.getProvider(targetVersion), user, message, channel, maxPage) }
        message.get().editOrCreate(channel) { buildMessage(remappedClasses.get(), sourceVersion, page, user, maxPage.get()) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(remappedClasses.timeToKeep) {
                if (maxPage.get() > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        msg.editOrCreate(channel) { buildMessage(remappedClasses.get(), sourceVersion, page, user, maxPage.get()) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (maxPage.get() > 1) register("➡") {
                    if (page < maxPage.get() - 1) {
                        page++
                        msg.editOrCreate(channel) { buildMessage(remappedClasses.get(), sourceVersion, page, user, maxPage.get()) }.subscribe()
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
            var desc = "Searching up classes for **${sourceProvider.namespace.id} ${sourceProvider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!sourceProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            setDescription(desc)
        }.block().also { message.set(it) }
        if (targetProvider.cached!!) message.get().editOrCreate(channel) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up classes for **${targetProvider.namespace.id} ${targetProvider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!targetProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            setDescription(desc)
        }.block()
        return getCatching(message.get(), channel, user) {
            val sourceMappings = sourceProvider.mappingsContainer!!.invoke()
            val targetMappings = targetProvider.mappingsContainer!!.invoke()
            val sourceClasses = sourceMappings.classes.filter {
                it.intermediaryName.onlyClass().equals(searchTerm, false) ||
                        it.mappedName?.onlyClass()?.equals(searchTerm, false) == true
            }
            val remappedClasses = mutableMapOf<String, String>()
            sourceClasses.forEach { yarnClass ->
                val obfName = yarnClass.obfName.merged!!
                val targetClass = targetMappings.getClassByObfName(obfName) ?: return@forEach
                remappedClasses[yarnClass.mappedName ?: yarnClass.intermediaryName] = targetClass.mappedName ?: targetClass.intermediaryName
            }
            if (remappedClasses.isEmpty()) {
                if (searchTerm.startsWith("func_") || searchTerm.startsWith("method_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a method!")
                } else if (searchTerm.startsWith("field_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a field!")
                }
                throw NullPointerException("No results found!")
            }

            maxPage.set(ceil(remappedClasses.size / 5.0).toInt())
            return@getCatching remappedClasses
        }
    }

    private fun EmbedCreateSpec.buildMessage(remappedClasses: MutableMap<String, String>, version: String, page: Int, author: User, maxPage: Int) {
        setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings")
        var desc = ""
        remappedClasses.keys.dropAndTake(5 * page, 5).forEach {
            if (desc.isNotEmpty())
                desc += "\n"
            val yarnName = remappedClasses[it]
            desc += "**MC $version: $it => `$yarnName`**\n"
        }
        setDescription(desc.substring(0, min(desc.length, 2000)))
    }

    override fun getName(): String = "${source.id.capitalize()}->${target.id.capitalize()} Class Command"
    override fun getDescription(): String = "Query ${source.id}->${target.id} classes."
}