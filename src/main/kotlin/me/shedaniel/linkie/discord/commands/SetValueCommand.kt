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
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow

object SetValueCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        event.validateInGuild()
        event.member.get().validateAdmin()
        args.validateUsage(prefix, 1..Int.MAX_VALUE, "$cmd <property> <value>")
        val config = ConfigManager[event.guildId.get().asLong()]
        val property = args[0].toLowerCase()
        val value = args.asSequence().drop(1).joinToString(" ")
        ConfigManager.setValueOf(config, property, value)
        ConfigManager.save()
        message.sendEmbed {
            setTitle("Successfully Set!")
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            description = "The value of property `$property` is now set to `$value`."
        }.subscribe()
    }

    override fun getName(): String? = "Set Value Command"
    override fun getDescription(): String? = "Set a value of the config for a server."
}