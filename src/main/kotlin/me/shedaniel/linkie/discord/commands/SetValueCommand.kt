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
import me.shedaniel.linkie.discord.scommands.stringUnlimited
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.validateAdmin
import me.shedaniel.linkie.discord.utils.validateInGuild

object SetValueCommand : SimpleCommand<SetValueCommand.SetValueData> {
    data class SetValueData(
        val property: String,
        val value: String,
    )

    override suspend fun SlashCommandBuilderInterface.buildCommand() {
        val propertyName = string("property", "The property name")
        val value = stringUnlimited("value", "The property value")
        executeCommandWith {
            SetValueData(
                property = opt(propertyName),
                value = opt(value),
            )
        }
    }

    override suspend fun execute(ctx: CommandContext, options: SetValueData) {
        ctx.validateInGuild {
            member.validateAdmin()
            val config = ConfigManager[guildId.asLong()]
            ConfigManager.setValueOf(config, options.property, options.value)
            ConfigManager.save()
            message.reply {
                title("Successfully Set!")
                basicEmbed(user)
                description = "The value of property `${options.property}` is now set to `${options.value}`."
            }
        }
    }
}