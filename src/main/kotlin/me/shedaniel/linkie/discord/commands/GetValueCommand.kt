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
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.validateAdmin
import me.shedaniel.linkie.discord.utils.validateInGuild

object GetValueCommand : SimpleCommand<String> {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        val propertyName = string("property", "The property name")
        executeCommandWith { opt(propertyName) }
    }

    override suspend fun execute(ctx: CommandContext, propertyName: String) {
        ctx.validateInGuild {
            member.validateAdmin()
            val config = ConfigManager[guildId.asLong()]
            val value = ConfigManager.getValueOf(config, propertyName)
            message.reply {
                title("Value Get!")
                basicEmbed(user)
                description = "The value of property `$propertyName` is set as `$value`."
            }
        }
    }
}
