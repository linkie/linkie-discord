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
import discord4j.rest.util.Permission
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.tricks.ContentType
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.tricks.TrickFlags
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.discord.validateUsage
import java.util.*

object AddTrickCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        LinkieScripting.validateGuild(event)
        args.validateUsage(prefix, 2..Int.MAX_VALUE, "$cmd <name> [--script] <trick>")
        val name = args.first()
        LinkieScripting.validateTrickName(name)
        args.removeAt(0)
        var type = ContentType.TEXT
        val flags = mutableListOf<Char>()
        val iterator = args.iterator()
        while (iterator.hasNext()) {
            val arg = iterator.next()
            if (arg.startsWith("-")) {
                when {
                    arg == "--script" -> {
                        iterator.remove()
                        type = ContentType.SCRIPT
                    }
                    arg.startsWith("--") -> {
                        throw IllegalStateException("Flag '$arg' does not exist!")
                    }
                    arg.length >= 2 -> {
                        iterator.remove()
                        flags.addAll(arg.substring(1).toCharArray().toList())
                    }
                }
            } else break
        }
        if (flags.contains('s')) {
            type = ContentType.SCRIPT
            flags.remove('s')
        }
        val member = event.member.get()
        if (flags.isNotEmpty()) {
            if (member.basePermissions.block()?.contains(Permission.MANAGE_MESSAGES) != true) {
                throw IllegalStateException("Adding tricks with flags requires `${Permission.MANAGE_MESSAGES.name.toLowerCase(Locale.ROOT).capitalize()}` permission!")
            }
        }
        flags.forEach {
            if (!TrickFlags.flags.containsKey(it)) {
                throw IllegalStateException("Flag '$it' does not exist!")
            }
        }
        var content = args.joinToString(" ").trim { it == '\n' }
        if (content.startsWith("```")) content = content.substring(3)
        if (content.endsWith("```")) content = content.substring(0, content.length - 3)
        require(content.isNotBlank()) { "Empty Trick!" }
        val l = System.currentTimeMillis()
        TricksManager.addTrick(
            Trick(
                id = UUID.randomUUID(),
                author = user.id.asLong(),
                name = name,
                content = content,
                contentType = type,
                creation = l,
                modified = l,
                guildId = event.guildId.get().asLong(),
                flags = flags
            )
        )
        message.sendEmbed {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setTitle("Added Trick")
            description = "Successfully added trick: $name"
        }.subscribe()
    }
}