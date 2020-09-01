package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.config.ConfigManager

object SetValueCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        event.validateInGuild()
        event.member.get().validateAdmin()
        args.validateUsage(prefix, 1..Int.MAX_VALUE, "$cmd <property> <value>")
        val config = ConfigManager[event.guildId.get().asLong()]
        val property = args[0].toLowerCase()
        val value = args.asSequence().drop(1).joinToString(" ")
        ConfigManager.setValueOf(config, property, value)
        ConfigManager.save()
        channel.createEmbedMessage {
            setTitle("Successfully Set!")
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setDescription("The value of property `$property` is now set to `$value`.")
        }.subscribe()
    }

    override fun getName(): String? = "Set Value Command"
    override fun getDescription(): String? = "Set a value of the config for a server."
}