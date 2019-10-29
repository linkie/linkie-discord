package me.shedaniel.linkie.kcommands

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.*
import java.util.concurrent.ScheduledExecutorService

object KHelpCommand : CommandBase {
    override fun execute(service: ScheduledExecutorService, event: MessageCreateEvent, author: Member, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isNotEmpty())
            throw InvalidUsageException("+$cmd")
        val prefix = commandApi.getPrefix(!channel.type.name.startsWith("GUILD_") || event.guildId.isPresent && event.guildId.get().asLong() != 432055962233470986L)
        channel.createEmbed {
            it.setTitle("Linkie Help Command")
            it.setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
            it.addField("Help Command", "Value: " + prefix + "help, " + prefix + "?, " + prefix + "commands\nDisplays this message.")
            it.addField("Fabric Api Versions Command", "Value: " + prefix + "fabricapi\nList Fabric API versions for every mc version.")
            it.addField("Yarn Class Command", "Value: " + prefix + "yc\nCheck yarn mappings.")
            it.addField("Yarn Field Command", "Value: " + prefix + "yf\nCheck yarn mappings.")
            it.addField("Yarn Method Command", "Value: " + prefix + "ym\nCheck yarn mappings.")
            it.setTimestampToNow()
        }
    }
}