package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.CommandCategory
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.validateNotEmpty

object EvaluateCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateNotEmpty(prefix, "$cmd <script>")
        var string = args.joinToString(" ")
        if (string.startsWith("```")) string = string.substring(3)
        if (string.endsWith("```")) string = string.substring(0, string.length - 3)
        LinkieScripting.eval(LinkieScripting.simpleContext.push {
            ContextExtensions.commandContexts(EvalContext(
                event,
                emptyList(),
                emptyList()
            ), user, channel, this)
        }, string)
    }

    override fun getName(): String? = "Evaluate Script"
    override fun getDescription(): String? = "Evaluate PESL Code"
    override fun getCategory(): CommandCategory = CommandCategory.TRICK
}