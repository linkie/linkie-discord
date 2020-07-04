package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.ContentType
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.tricks.TricksManager
import java.util.*

object AddTrickCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(2..Int.MAX_VALUE, "$cmd <name> [--script] <trick>")
        val name = args.first()
        LinkieScripting.validateTrickName(name)
        args.removeAt(0)
        val type = if (args[0] == "--script") ContentType.SCRIPT else ContentType.TEXT
        if (type == ContentType.SCRIPT) args.removeAt(0)
        var content = args.joinToString(" ").trim()
        if (content.startsWith("```")) content = content.substring(3)
        if (content.endsWith("```")) content = content.substring(0, content.length - 3)
        require(!content.isBlank()) { "Empty Trick!" }
        TricksManager.addTrick(Trick(
                id = UUID.randomUUID(),
                author = user.id.asLong(),
                name = name,
                content = content,
                contentType = type,
                creation = System.currentTimeMillis(),
                guildId = event.guildId.get().asLong()
        ))
        channel.createEmbedMessage {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setTitle("Added Trick")
            setDescription("Successfully added trick: $name")
        }.subscribe()
    }

    override fun getName(): String? = "Add Trick"
    override fun getDescription(): String? = "Add a trick"
}