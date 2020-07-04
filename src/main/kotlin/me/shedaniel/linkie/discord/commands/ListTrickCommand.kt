package me.shedaniel.linkie.discord.commands

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.TricksManager
import java.time.Instant

object ListTrickCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(1, "$cmd [userId]")
        val memberId = if (args.isEmpty()) user.id.asLong() else (args.first().toLongOrNull() ?: throw NullPointerException("Member id must be a number!"))
        val guild = event.guild.block()!!
        val member = guild.getMemberById(Snowflake.of(memberId)).block() ?: throw NullPointerException("Failed to find member with the id $memberId")
        channel.createEmbedMessage {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setTitle("Tricks by ${member.mention}")
            TricksManager.tricks.values.filter { it.guildId == guild.id.asLong() && it.author == member.id.asLong() }.sortedBy { it.name }.forEach { trick ->
                addInlineField(trick.name, "Created on " + Instant.ofEpochMilli(trick.creation).toString())
            }
        }.subscribe()
    }

    override fun getName(): String? = "List Tricks"
    override fun getDescription(): String? = "List the tricks by a member"
}