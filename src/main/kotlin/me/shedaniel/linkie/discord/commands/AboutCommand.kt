package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.discord.*

object AboutCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.isNotEmpty())
            throw InvalidUsageException("!$cmd")
        channel.createEmbed {
            it.setTitle("About Linkie")
            api.self.map { it.avatarUrl }.block()?.also { url -> it.setThumbnail(url) }
            it.setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            it.setDescription("A mappings bot created by <@430615025066049538>.")
            it.addField("Source", "https://github.com/shedaniel/LinkieBot")
            it.addField("License", "GNU v3")
            it.addField("Invite", "https://discordapp.com/oauth2/authorize?client_id=472081983925780490&permissions=10304&scope=bot")
            it.setTimestampToNow()
        }.subscribe()
    }

    override fun getDescription(): String? = "Everything about Linkie."
    override fun getName(): String? = "About Command"
}