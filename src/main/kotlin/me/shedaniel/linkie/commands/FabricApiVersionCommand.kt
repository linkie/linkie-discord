package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.cursemetaapi.CurseMetaAPI
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.dropAndTake
import java.time.Duration
import kotlin.math.ceil

object FabricApiVersionCommand : CommandBase {

    private const val itemsPerPage = 24f

    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.size > 2)
            throw InvalidUsageException("+$cmd [page] [-r]")
        var page = if (args.isEmpty()) 0 else if (args.size == 1 && args[0].equals("-r", ignoreCase = true)) 0 else args[0].toInt() - 1
        val showReleaseOnly = args.size == 2 && args[1].equals("-r", ignoreCase = true) || args.size == 1 && args[0].equals("-r", ignoreCase = true)
        require(page >= 0) { "The minimum page is 1!" }
        val map = mutableMapOf<String, CurseMetaAPI.AddonFile>()
        CurseMetaAPI.getAddonFiles(306612).apply { sortByDescending { it.fileId } }.forEach { file ->
            val displayName = file.displayName
            if (displayName[0] == '[' && displayName.indexOf(']') > -1) {
                val version = displayName.substring(1).split("]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                if (showReleaseOnly && (version.contains("pre", true) || version.startsWith("19w", true) || version.startsWith("20w", true) || version.startsWith("18w", true) || version.startsWith("21w", true)))
                    return
                if (!map.containsKey(version))
                    map[version] = file
            }
        }
        require(page <= map.size / itemsPerPage) { "The maximum page is ${ceil(map.size / itemsPerPage).toInt() + 1}!" }

        channel.createEmbed { it.buildMessage(map, page, user, showReleaseOnly) }.subscribe { msg ->
            if (channel.type.name.startsWith("GUILD_"))
                msg.removeAllReactions().block()
            msg.subscribeReactions("⬅", "❌", "➡")
            api.eventDispatcher.on(ReactionAddEvent::class.java).filter { e -> e.messageId == msg.id }.take(Duration.ofMinutes(15)).subscribe {
                when {
                    it.userId == api.selfId.get() -> {
                    }
                    it.userId == user.id -> {
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
                                    msg.edit { it.setEmbed { it.buildMessage(map, page, user, showReleaseOnly) } }.subscribe()
                                }
                            } else if (unicode.raw == "➡") {
                                msg.removeReaction(it.emoji, it.userId).subscribe()
                                if (page < ceil(map.size / itemsPerPage).toInt() - 1) {
                                    page++
                                    msg.edit { it.setEmbed { it.buildMessage(map, page, user, showReleaseOnly) } }.subscribe()
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
    }

    private fun EmbedCreateSpec.buildMessage(map: MutableMap<String, CurseMetaAPI.AddonFile>, page: Int, author: User, showReleaseOnly: Boolean) {
        setTitle("Fabric API Versions")
        setFooter("Page ${page + 1}/${ceil(map.size / itemsPerPage).toInt()}. Requested by ${author.discriminatedName}", author.avatarUrl)
        setTimestampToNow()
        map.entries.dropAndTake(itemsPerPage.toInt() * page, itemsPerPage.toInt()).forEach { (version, file) ->
            addField(version, file.fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", ""), true)
        }
        if (!showReleaseOnly) setDescription("Tips: Use -r for release only.")
    }

    override fun getDescription(): String? = "Displays a list of Fabric APIs"
    override fun getName(): String? = "Fabric API Command"

}