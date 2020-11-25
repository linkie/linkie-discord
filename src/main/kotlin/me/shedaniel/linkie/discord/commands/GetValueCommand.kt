package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.sendEmbedMessage
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.discord.validateAdmin
import me.shedaniel.linkie.discord.validateInGuild
import me.shedaniel.linkie.discord.validateUsage

object GetValueCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        event.validateInGuild()
        event.member.get().validateAdmin()
        args.validateUsage(prefix, 1, "$cmd <property>")
        val config = ConfigManager[event.guildId.get().asLong()]
        val property = args[0].toLowerCase()
        val value = ConfigManager.getValueOf(config, property)
        channel.sendEmbedMessage(event.message) {
            setTitle("Value Get!")
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            description = "The value of property `$property` is set as `$value`."
        }.subscribe()
    }

    override fun getName(): String? = "Get Value Command"
    override fun getDescription(): String? = "Get a value of the config for a server."
}