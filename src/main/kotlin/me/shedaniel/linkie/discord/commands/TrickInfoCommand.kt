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
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.opt
import me.shedaniel.linkie.discord.scommands.string
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.addField
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.reply
import me.shedaniel.linkie.discord.utils.validateInGuild
import java.time.Instant

object TrickInfoCommand : SimpleCommand<String> {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        val trickName = string("trick_name", "Name of the trick")
        executeCommandWith { opt(trickName) }
    }

    override suspend fun execute(ctx: CommandContext, trickName: String) {
        ctx.validateInGuild {
            LinkieScripting.validateGuild(ctx)
            val trick = TricksManager[trickName to guildId.asLong()] ?: throw NullPointerException("Cannot find trick named `$trickName`")
            message.reply {
                basicEmbed(user)
                title("Trick Info")
                addField("Name", trick.name)
                addInlineField("Author", "<@${trick.author}>")
                addInlineField("Trick Type", trick.contentType.name.toLowerCase().capitalize())
                addInlineField("Creation Time", Instant.ofEpochMilli(trick.creation).toString())
                addInlineField("Last Modification Time", Instant.ofEpochMilli(trick.modified).toString())
                addInlineField("Unique Identifier", trick.id.toString())
                description = "```${trick.content}```"
            }
        }
    }
}