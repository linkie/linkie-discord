/*
 * Copyright (c) 2019, 2020, 2021 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        if (event.guildId.isPresent) {
            val guildId = event.guildId.get().asLong()
            if (!ConfigManager[guildId].tricksEnabled) return
            val trick = TricksManager[cmd to guildId] ?: return
            val evalContext = EvalContext(
                prefix,
                cmd,
                event,
                trick.flags,
                args,
                parent = false,
            )
            LinkieScripting.evalTrick(evalContext, message, trick) {
                LinkieScripting.simpleContext.push {
                    ContextExtensions.commandContexts(evalContext, user, channel, message, this)
                }
            }
        }
    }
}