package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.config.ConfigManager

object GetValueCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        event.validateInGuild()
        event.member.get().validateAdmin()
        args.validateUsage(prefix, 1, "$cmd <property>")
        val config = ConfigManager[event.guildId.get().asLong()]
        val property = args[0].toLowerCase()
        val value = ConfigManager.getValueOf(config, property)
        channel.createEmbedMessage {
            setTitle("Value Get!")
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setDescription("The value of property `$property` is set as `$value`.")
        }.subscribe()
    }

    override fun getName(): String? = "Get Value Command"
    override fun getDescription(): String? = "Get a value of the config for a server."
}