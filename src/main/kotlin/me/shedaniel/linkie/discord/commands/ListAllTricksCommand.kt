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

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.discord.OptionlessCommand
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.initiate
import me.shedaniel.linkie.discord.utils.sendPages
import me.shedaniel.linkie.discord.utils.validateInGuild
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.valueKeeper
import java.time.Instant
import kotlin.math.ceil

object ListAllTricksCommand : OptionlessCommand {
    override suspend fun execute(ctx: CommandContext) {
        LinkieScripting.validateGuild(ctx)
        ctx.validateInGuild {
            val tricks by valueKeeper {
                val list = TricksManager.tricks.values.filter { it.guildId == guild.id.asLong() }.sortedBy { it.name }
                list to ceil(list.size / 5.0).toInt()
            }.initiate()
            message.sendPages(0, tricks.second) { page ->
                buildMessage(tricks.first, page, user, tricks.second)
            }
        }
    }

    private fun EmbedCreateSpec.Builder.buildMessage(tricks: List<Trick>, page: Int, user: User, maxPage: Int) {
        basicEmbed(user)
        if (maxPage > 1) title("Tricks by everyone (Page ${page + 1}/$maxPage)")
        else title("Tricks by everyone")
        tricks.dropAndTake(page * 5, 5).forEach { trick ->
            addInlineField(trick.name, "Created by <@${trick.author}> on " + Instant.ofEpochMilli(trick.creation).toString())
        }
    }
}