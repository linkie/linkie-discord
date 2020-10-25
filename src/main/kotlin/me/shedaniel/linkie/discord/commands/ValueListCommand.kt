package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.config.GuildConfig
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.discord.validateAdmin
import me.shedaniel.linkie.discord.validateEmpty
import me.shedaniel.linkie.discord.validateInGuild
import me.shedaniel.linkie.utils.dropAndTake
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

object ValueListCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        event.validateInGuild()
        event.member.get().validateAdmin()
        args.validateEmpty(prefix, cmd)
        val config = ConfigManager[event.guildId.get().asLong()]
        val properties = ConfigManager.getProperties().toMutableList()
        var page = 0
        val maxPage = ceil(properties.size / 5.0).toInt()
        val message = AtomicReference<Message?>()
        message.editOrCreate(channel) { buildMessage(config, properties, user, page, maxPage) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(Duration.ofMinutes(2)) {
                if (maxPage > 1) register("⬅") {
                    if (page > 0) {
                        page--
                        message.editOrCreate(channel) { buildMessage(config, properties, user, page, maxPage) }.subscribe()
                    }
                }
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                if (maxPage > 1) register("➡") {
                    if (page < maxPage - 1) {
                        page++
                        message.editOrCreate(channel) { buildMessage(config, properties, user, page, maxPage) }.subscribe()
                    }
                }
            }.build(msg, user)
        }
    }

    private fun EmbedCreateSpec.buildMessage(config: GuildConfig, properties: List<String>, user: User, page: Int, maxPage: Int) {
        setTitle("Value List")
        if (maxPage > 1) setTitle("Value List (Page ${page + 1}/$maxPage)")
        else setTitle("Value List")
        setFooter("Requested by ${user.discriminatedName}", user.avatarUrl)
        setTimestampToNow()
        properties.dropAndTake(page * 5, 5).forEach { property ->
            val value = ConfigManager.getValueOf(config, property)
            if (value.isEmpty()) addInlineField(property, "Value:")
            else addInlineField(property, "Value: `$value`")
        }
        setDescription("More information about Server Rule at https://github.com/shedaniel/linkie-discord/wiki/Server-Rules")
    }

    override fun getName(): String? = "List Values Command"
    override fun getDescription(): String? = "List the values of the config for a server."
}