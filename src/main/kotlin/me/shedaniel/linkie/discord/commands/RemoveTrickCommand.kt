package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.CommandCategory
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.tricks.canManageTrick
import me.shedaniel.linkie.discord.utils.createEmbedMessage
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.discord.validateUsage

object RemoveTrickCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(prefix, 1, "$cmd <name>")
        val name = args.first()
        LinkieScripting.validateTrickName(name)
        val trick = TricksManager[name to event.guildId.get().asLong()] ?: throw NullPointerException("Cannot find trick named `$name`")
        require(event.member.get().canManageTrick(trick)) { "You don't have permission to manage this trick!" }
        TricksManager.removeTrick(trick)
        channel.createEmbedMessage {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setTitle("Removed Trick")
            setDescription("Successfully removed trick: $name")
        }.subscribe()
    }

    override fun getName(): String? = "Remove Trick"
    override fun getDescription(): String? = "Remove a trick"
    override fun getCategory(): CommandCategory = CommandCategory.TRICK
}