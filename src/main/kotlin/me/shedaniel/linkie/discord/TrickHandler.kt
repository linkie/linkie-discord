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

import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.handler.CommandAcceptor
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.CommandContext

class TrickHandler(private val prefix: String) : CommandAcceptor {
    override fun getPrefix(event: MessageCreateEvent): String = prefix

    override suspend fun execute(event: MessageCreateEvent, ctx: CommandContext, args: MutableList<String>): Boolean {
        if (event.guildId.isPresent) {
            val guildId = event.guildId.get().asLong()
            if (!ConfigManager[guildId].tricksEnabled) return false
            val trick = TricksManager[ctx.cmd to guildId] ?: return false
            val evalContext = EvalContext(
                ctx,
                event.message,
                trick.flags,
                args,
                parent = false,
            )
            LinkieScripting.evalTrick(evalContext, ctx.message, trick) {
                LinkieScripting.simpleContext.push {
                    ContextExtensions.commandContexts(evalContext, ctx.user, ctx.channel, ctx.message, this)
                }
            }
            return true
        }
        return false
    }
}
