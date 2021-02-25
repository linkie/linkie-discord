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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.SubCommandHolder
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.listener.ChannelListeners
import me.shedaniel.linkie.discord.validateInGuild
import me.shedaniel.linkie.discord.validateUsage

object ListenerCommand : SubCommandHolder() {
    val listen = Listen

    object Listen : CommandBase {
        override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
            event.validateInGuild()
            args.validateUsage(prefix, 1, "$cmd <id>")
            ChannelListeners[args[0].toLowerCase()]
            val config = ConfigManager[event.guildId.get().asLong()]
            val channels = config.listenerChannels.getOrPut(args[0].toLowerCase(), ::mutableListOf)
            val channelId = event.message.channelId.asLong()
            if (channels.contains(channelId)) {
                throw IllegalStateException("You have already this listener to this channel!")
            } else {
                channels.add(channelId)
                ConfigManager.save()
                message.sendEmbed {
                    setTitle("Listener added")
                    setDescription("You have successfully added listener id `${args[0].toLowerCase()}`.\nThis message will self-destruct in 20 seconds to keep this channel clean.")
                }.subscribe {
                    GlobalScope.launch {
                        delay(20000)
                        it.delete()
                    }
                }
            }
        }
    }
}