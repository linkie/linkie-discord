/*
 * Copyright (c) 2019, 2020 shedaniel
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
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.discord.validateUsage
import java.time.Instant

object TrickInfoCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(prefix, 1, "$cmd <trick>")
        val trickName = args.first()
        val trick = TricksManager[trickName to event.guildId.get().asLong()] ?: throw NullPointerException("Cannot find trick named `$trickName`")
        message.sendEmbed {
            setFooter("Requested by ${user.discriminatedName}", user.avatarUrl)
            setTimestampToNow()
            setTitle("Trick Info")
            addField("Name", trick.name)
            addInlineField("Author", "<@${trick.author}>")
            addInlineField("Trick Type", trick.contentType.name.toLowerCase().capitalize())
            addInlineField("Creation Time", Instant.ofEpochMilli(trick.creation).toString())
            addInlineField("Last Modification Time", Instant.ofEpochMilli(trick.modified).toString())
            addInlineField("Unique Identifier", trick.id.toString())
            description = "```${trick.content}```"
        }.subscribe()
    }
}