package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.tricks.TricksManager

object TrickHandler : CommandAcceptor {
    override fun getPrefix(event: MessageCreateEvent): String? =
        event.guildId.orElse(null)?.let { ConfigManager[it.asLong()].tricksPrefix }

    override fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        if (event.guildId.isPresent) {
            val guildId = event.guildId.get().asLong()
            if (!ConfigManager[guildId].tricksEnabled) return
            val trick = TricksManager[cmd to guildId] ?: return
            LinkieScripting.evalTrick(EvalContext(
                event,
                trick.flags,
                args
            ), channel, trick) {
                LinkieScripting.simpleContext.push {
                    ContextExtensions.commandContexts(EvalContext(
                        event,
                        trick.flags,
                        args
                    ), user, channel, this)
                }
            }
        }
    }
}