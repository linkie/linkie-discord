package me.shedaniel.linkie.discord.commands

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.CommandCategory
import me.shedaniel.linkie.discord.ValueKeeper
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.discord.validateUsage
import me.shedaniel.linkie.utils.dropAndTake
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

object ListTricksCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(prefix, 1, "$cmd [userId]")
        val memberId = if (args.isEmpty()) user.id.asLong() else (args.first().toLongOrNull() ?: throw NullPointerException("Member id must be a number!"))
        val guild = event.guild.block()!!
        val member = guild.getMemberById(Snowflake.of(memberId)).block() ?: throw NullPointerException("Failed to find member with the id $memberId")
        var page = 0
        val tricks = ValueKeeper(Duration.ofMinutes(2)) {
            val list = TricksManager.tricks.values.filter { it.guildId == guild.id.asLong() && it.author == member.id.asLong() }.sortedBy { it.name }
            list to ceil(list.size / 5.0).toInt()
        }
        val message = AtomicReference<Message?>()
        channel.createEmbedMessage { buildMessage(tricks.get().first, page, user, member, tricks.get().second) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(tricks.timeToKeep) {
                if (tricks.get().second > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        message.editOrCreate(channel) { buildMessage(tricks.get().first, page, user, member, tricks.get().second) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (tricks.get().second > 1) register("➡") {
                    if (page < tricks.get().second - 1) {
                        page++
                        message.editOrCreate(channel) { buildMessage(tricks.get().first, page, user, member, tricks.get().second) }.subscribe()
                    }
                }
            }.build(msg, user)
        }
    }

    private fun EmbedCreateSpec.buildMessage(tricks: List<Trick>, page: Int, user: User, member: Member, maxPage: Int) {
        setFooter("Requested by ${user.discriminatedName}", user.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("Tricks by ${member.discriminatedName} (Page ${page + 1}/$maxPage)")
        else setTitle("Tricks by ${member.discriminatedName}")
        tricks.dropAndTake(page * 5, 5).forEach { trick ->
            addInlineField(trick.name, "Created by <@${trick.author}> on " + Instant.ofEpochMilli(trick.creation).toString())
        }
    }

    override fun getName(): String? = "List Tricks"
    override fun getDescription(): String? = "List the tricks by a member"
    override fun getCategory(): CommandCategory = CommandCategory.TRICK
}