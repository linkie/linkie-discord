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

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.discord.SimpleCommand
import me.shedaniel.linkie.discord.ValueKeeper
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.opt
import me.shedaniel.linkie.discord.scommands.user
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.sendPages
import me.shedaniel.linkie.discord.utils.validateInGuild
import me.shedaniel.linkie.utils.dropAndTake
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

object ListTricksCommand : SimpleCommand<User> {
    override suspend fun SlashCommandBuilderInterface.buildCommand() {
        val member = user("member", "The member to search for")
        executeCommandWith { opt(member) }
    }

    override suspend fun execute(ctx: CommandContext, options: User) {
        LinkieScripting.validateGuild(ctx)
        ctx.validateInGuild {
            val member = guild.getMemberById(options.id).block() ?: throw NullPointerException("Failed to find member in guild: ${options.discriminatedName}")
            val tricks = ValueKeeper(Duration.ofMinutes(2)) {
                val list = TricksManager.tricks.values.filter { it.guildId == guild.id.asLong() && it.author == member.id.asLong() }.sortedBy { it.name }
                list to ceil(list.size / 5.0).toInt()
            }
            message.sendPages(ctx, 0, tricks.get().second) { page ->
                buildMessage(tricks.get().first, page, user, member, tricks.get().second)
            }
        }
    }

    private fun EmbedCreateSpec.Builder.buildMessage(tricks: List<Trick>, page: Int, user: User, member: Member, maxPage: Int) {
        basicEmbed(user)
        if (maxPage > 1) title("Tricks by ${member.discriminatedName} (Page ${page + 1}/$maxPage)")
        else title("Tricks by ${member.discriminatedName}")
        tricks.dropAndTake(page * 5, 5).forEach { trick ->
            addInlineField(trick.name, "Created by <@${trick.author}> on " + Instant.ofEpochMilli(trick.creation).toString())
        }
    }
}