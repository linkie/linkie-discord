package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.gateway
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.discord.validateEmpty

object AboutCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateEmpty(prefix, cmd)
        channel.sendEmbedMessage(event.message) {
            setTitle("About Linkie")
            gateway.self.map(User::getAvatarUrl).block()?.also { url -> setThumbnail(url) }
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            description = "A mappings bot created by <@430615025066049538>."
            addField("Library Src", "https://github.com/shedaniel/linkie-core/")
            addField("Bot Src", "https://github.com/shedaniel/linkie-discord/")
            addField("License", "Apache 2.0")
            addField("Invite", "https://discordapp.com/oauth2/authorize?client_id=472081983925780490&permissions=10304&scope=bot")
            setTimestampToNow()
        }.subscribe()
    }

    override fun getDescription(): String? = "Everything about Linkie."
    override fun getName(): String? = "About Command"
}