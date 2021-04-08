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
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.ValueKeeper
import me.shedaniel.linkie.discord.basicEmbed
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.sendPages
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.validateEmpty
import me.shedaniel.linkie.utils.dropAndTake
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

object ListAllTricksCommand : CommandBase {
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateEmpty(prefix, cmd)
        val guild = event.guild.block()!!
        val tricks = ValueKeeper(Duration.ofMinutes(2)) {
            val list = TricksManager.tricks.values.filter { it.guildId == guild.id.asLong() }.sortedBy { it.name }
            list to ceil(list.size / 5.0).toInt()
        }
        message.sendPages(0, tricks.get().second) { page ->
            buildMessage(tricks.get().first, page, user, tricks.get().second)
        }
    }

    private fun EmbedCreateSpec.buildMessage(tricks: List<Trick>, page: Int, user: User, maxPage: Int) {
        basicEmbed(user)
        if (maxPage > 1) setTitle("Tricks by everyone (Page ${page + 1}/$maxPage)")
        else setTitle("Tricks by everyone")
        tricks.dropAndTake(page * 5, 5).forEach { trick ->
            addInlineField(trick.name, "Created by <@${trick.author}> on " + Instant.ofEpochMilli(trick.creation).toString())
        }
    }
}