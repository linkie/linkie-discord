package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.LinkieScripting.context
import me.shedaniel.linkie.discord.validateNotEmpty

object EvaluateCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateNotEmpty("$cmd <script>")
        var string = args.joinToString(" ").trim()
        if (string.startsWith("```")) string = string.substring(3)
        if (string.endsWith("```")) string = string.substring(0, string.length - 3)
        LinkieScripting.eval(LinkieScripting.simpleContext().context {
            ContextExtensions.commandContexts(event, user, cmd, args, channel, it)
        }, string)
    }

    override fun getName(): String? = "Evaluate Script"
    override fun getDescription(): String? = "Evaluate PESL Code"
}