package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.CommandCategory
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.ContentType
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.tricks.TrickFlags
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.sendEmbedMessage
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.discord.validateUsage
import java.util.*

object AddTrickCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(prefix, 2..Int.MAX_VALUE, "$cmd <name> [--script] <trick>")
        val name = args.first()
        LinkieScripting.validateTrickName(name)
        args.removeAt(0)
        var type = ContentType.TEXT
        val flags = mutableListOf<Char>()
        val iterator = args.iterator()
        while (iterator.hasNext()) {
            val arg = iterator.next()
            if (arg.startsWith("-")) {
                when {
                    arg == "--script" -> {
                        iterator.remove()
                        type = ContentType.SCRIPT
                    }
                    arg.startsWith("--") -> {
                        throw IllegalStateException("Flag '$arg' does not exist!")
                    }
                    arg.length >= 2 -> {
                        iterator.remove()
                        flags.addAll(arg.substring(1).toCharArray().toList())
                    }
                }
            } else break
        }
        if (flags.contains('s')) {
            type = ContentType.SCRIPT
            flags.remove('s')
        }
        val member = event.member.get()
        if (flags.isNotEmpty()) {
            if (member.basePermissions.block()?.contains(Permission.MANAGE_MESSAGES) != true) {
                throw IllegalStateException("Adding tricks with flags requires `${Permission.MANAGE_MESSAGES.name.toLowerCase(Locale.ROOT).capitalize()}` permission!")
            }
        }
        flags.forEach {
            if (!TrickFlags.flags.containsKey(it)) {
                throw IllegalStateException("Flag '$it' does not exist!")
            }
        }
        var content = args.joinToString(" ").trim { it == '\n' }
        if (content.startsWith("```")) content = content.substring(3)
        if (content.endsWith("```")) content = content.substring(0, content.length - 3)
        require(content.isNotBlank()) { "Empty Trick!" }
        val l = System.currentTimeMillis()
        TricksManager.addTrick(
            Trick(
                id = UUID.randomUUID(),
                author = user.id.asLong(),
                name = name,
                content = content,
                contentType = type,
                creation = l,
                modified = l,
                guildId = event.guildId.get().asLong(),
                flags = flags
            )
        )
        channel.sendEmbedMessage(event.message) {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setTitle("Added Trick")
            description = "Successfully added trick: $name"
        }.subscribe()
    }

    override fun getName(): String? = "Add Trick"
    override fun getDescription(): String? = "Add a trick"
    override fun getCategory(): CommandCategory = CommandCategory.TRICK
}