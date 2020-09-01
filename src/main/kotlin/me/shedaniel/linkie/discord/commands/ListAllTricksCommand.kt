package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.utils.dropAndTake
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

object ListAllTricksCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateEmpty(prefix, cmd)
        val guild = event.guild.block()!!
        var page = 0
        val tricks = ValueKeeper(Duration.ofMinutes(2)) {
            val list = TricksManager.tricks.values.filter { it.guildId == guild.id.asLong() }.sortedBy { it.name }
            list to ceil(list.size / 5.0).toInt()
        }
        val message = AtomicReference<Message?>()
        message.editOrCreate(channel) { buildMessage(tricks.get().first, page, user, tricks.get().second) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(tricks.timeToKeep) {
                if (tricks.get().second > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        message.editOrCreate(channel) { buildMessage(tricks.get().first, page, user, tricks.get().second) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (tricks.get().second > 1) register("➡") {
                    if (page < tricks.get().second - 1) {
                        page++
                        message.editOrCreate(channel) { buildMessage(tricks.get().first, page, user, tricks.get().second) }.subscribe()
                    }
                }
            }.build(msg, user)
        }
    }

    private fun EmbedCreateSpec.buildMessage(tricks: List<Trick>, page: Int, user: User, maxPage: Int) {
        setFooter("Requested by ${user.discriminatedName}", user.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("Tricks by everyone (Page ${page + 1}/$maxPage)")
        else setTitle("Tricks by everyone")
        tricks.dropAndTake(page * 5, 5).forEach { trick ->
            addInlineField(trick.name, "Created by <@${trick.author}> on " + Instant.ofEpochMilli(trick.creation).toString())
        }
    }

    override fun getName(): String? = "List All Tricks"
    override fun getDescription(): String? = "List all the tricks"
    override fun getCategory(): CommandCategory = CommandCategory.TRICK
}