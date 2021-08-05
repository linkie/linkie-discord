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

package me.shedaniel.linkie.discord.commands

import me.shedaniel.linkie.discord.SimpleCommand
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.opt
import me.shedaniel.linkie.discord.scommands.string
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.use

object EvaluateCommand : SimpleCommand<String> {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        val script = string("script", "The JavaScript code to be evaluated")
        executeCommandWith { opt(script) }
    }

    override suspend fun execute(ctx: CommandContext, script: String) {
        ctx.use {
            if (guildId != null) {
                require(ConfigManager[guildId!!.asLong()].evalEnabled) { "Eval is not enabled on this server." }
            }
            var string = script
            if (string.startsWith("```")) string = string.substring(3)
            if (string.endsWith("```")) string = string.substring(0, string.length - 3)
            LinkieScripting.eval(LinkieScripting.simpleContext.push {
                ContextExtensions.commandContexts(EvalContext(
                    ctx,
                    null,
                    emptyList(),
                    emptyList(),
                    parent = false,
                ), user, channel, message, this)
            }, string)
        }
    }
}