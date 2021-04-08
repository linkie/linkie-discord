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
import me.shedaniel.linkie.discord.basicEmbed
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.config.GuildConfig
import me.shedaniel.linkie.discord.sendPages
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.validateAdmin
import me.shedaniel.linkie.discord.validateEmpty
import me.shedaniel.linkie.discord.validateInGuild
import me.shedaniel.linkie.utils.dropAndTake
import kotlin.math.ceil

object ValueListCommand : CommandBase {
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        event.validateInGuild()
        event.member.get().validateAdmin()
        args.validateEmpty(prefix, cmd)
        val config = ConfigManager[event.guildId.get().asLong()]
        val properties = ConfigManager.getProperties().toMutableList()
        val maxPage = ceil(properties.size / 5.0).toInt()
        message.sendPages(0, maxPage) { page ->
            buildMessage(config, properties, user, page, maxPage)
        }
    }

    private fun EmbedCreateSpec.buildMessage(config: GuildConfig, properties: List<String>, user: User, page: Int, maxPage: Int) {
        setTitle("Value List")
        if (maxPage > 1) setTitle("Value List (Page ${page + 1}/$maxPage)")
        else setTitle("Value List")
        basicEmbed(user)
        properties.dropAndTake(page * 5, 5).forEach { property ->
            val value = ConfigManager.getValueOf(config, property)
            if (value.isEmpty()) addInlineField(property, "Value:")
            else addInlineField(property, "Value: `$value`")
        }
        description = "More information about Server Rule at https://github.com/shedaniel/linkie-discord/wiki/Server-Rules"
    }

}