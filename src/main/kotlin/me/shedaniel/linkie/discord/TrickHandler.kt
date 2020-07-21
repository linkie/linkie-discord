package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.tricks.TricksManager

object TrickHandler : CommandAcceptor {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        if (event.guildId.isPresent) {
            val guildId = event.guildId.get().asLong()
            if (!ConfigManager[guildId].tricksEnabled) return
            val trick = TricksManager[cmd to guildId] ?: return
            LinkieScripting.evalTrick(channel, args, trick) {
                LinkieScripting.simpleContext.push {
                    ContextExtensions.commandContexts(event, user, args, channel, this)
                }
            }
        }
    }
}