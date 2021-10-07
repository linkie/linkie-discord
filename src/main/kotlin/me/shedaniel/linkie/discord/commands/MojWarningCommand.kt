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

import discord4j.rest.util.Color
import me.shedaniel.linkie.discord.OptionlessCommand
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.prefixedCmd
import me.shedaniel.linkie.discord.utils.reply
import me.shedaniel.linkie.discord.utils.use

object MojWarningCommand : OptionlessCommand {
    override suspend fun execute(ctx: CommandContext) {
        ctx.use { 
            message.reply { 
                title("Invalid Command")
                color(Color.RED)
                description("$prefixedCmd is not a Linkie command! Do I look like K9 to you??????")
            }
        }
    }
}