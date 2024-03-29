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

package me.shedaniel.linkie.discord.tricks

import me.shedaniel.linkie.discord.Command
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.args
import me.shedaniel.linkie.discord.scommands.optNullable
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.acknowledge
import me.shedaniel.linkie.discord.utils.validateInGuild

class TrickBasedCommand(val trick: TrickBase) : Command {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        val args = args(required = false)
        executeCommandWithGetter { ctx, options -> execute(ctx, options.optNullable(args) ?: mutableListOf()) }
    }

    suspend fun execute(ctx: CommandContext, args: MutableList<String>) {
        ctx.validateInGuild {
            LinkieScripting.validateGuild(ctx)
            val evalContext = EvalContext(
                ctx,
                null,
                trick.flags,
                args,
                parent = false,
            )
            message.acknowledge()
            LinkieScripting.evalTrick(evalContext, message, trick) {
                LinkieScripting.simpleContext.push {
                    ContextExtensions.commandContexts(evalContext, user, channel, message, this)
                }
            }
        }
    }
}