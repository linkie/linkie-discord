package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.CommandCategory
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.validateUsage

object RunTrickCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(prefix, 1, "$cmd <trick>")
        val trickName = args.first()
        val trick = TricksManager[trickName to event.guildId.get().asLong()] ?: throw NullPointerException("Cannot find trick named `$trickName`")
        LinkieScripting.evalTrick(channel, args, trick) {
            LinkieScripting.simpleContext.push {
                ContextExtensions.commandContexts(event, user, args, channel, this)
            }
        }
    }

    override fun getName(): String? = "Run Trick"
    override fun getDescription(): String? = "Run a trick"
    override fun getCategory(): CommandCategory = CommandCategory.TRICK
}