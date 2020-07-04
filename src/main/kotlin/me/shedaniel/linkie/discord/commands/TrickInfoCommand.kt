package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.TricksManager
import java.time.Instant

object TrickInfoCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(1, "$cmd <trick>")
        val trickName = args.first()
        val trick = TricksManager[trickName to event.guildId.get().asLong()] ?: throw NullPointerException("Cannot find trick named `$trickName`")
        channel.createEmbedMessage {
            setFooter("Requested by ${user.discriminatedName}", user.avatarUrl)
            setTimestampToNow()
            setTitle("Trick Info")
            addField("Name", trick.name)
            addInlineField("Author", "<@${trick.author}>")
            addInlineField("Trick Type", trick.contentType.name.toLowerCase().capitalize())
            addInlineField("Creation Time", Instant.ofEpochMilli(trick.creation).toString())
            addInlineField("Last Modification Time", Instant.ofEpochMilli(trick.modified).toString())
            addInlineField("Unique Identifier", trick.id.toString())
            setDescription("```${trick.content}```")
        }.subscribe()
    }

    override fun getName(): String? = "Trick Info"
    override fun getDescription(): String? = "Display a trick's info"
    override fun getCategory(): CommandCategory = CommandCategory.TRICK
}