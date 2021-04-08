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
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.SubCommandHolder
import me.shedaniel.linkie.discord.basicEmbed
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.gateway
import me.shedaniel.linkie.discord.listener.ChannelListeners
import me.shedaniel.linkie.discord.sendPages
import me.shedaniel.linkie.discord.utils.buildReactions
import me.shedaniel.linkie.discord.utils.buildSafeDescription
import me.shedaniel.linkie.discord.validateAdmin
import me.shedaniel.linkie.discord.validateInGuild
import me.shedaniel.linkie.discord.validateUsage
import me.shedaniel.linkie.utils.dropAndTake
import java.time.Duration
import kotlin.math.ceil

object ListenerCommand : SubCommandHolder() {
    val list = subCmd(List)
    val listen = subCmd(Listen)
    val unlisten = subCmd(UnListen)

    object List : CommandBase {
        override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
            event.validateInGuild()
            event.member.get().validateAdmin()
            val config = ConfigManager[event.guildId.get().asLong()]
            val channelId = event.message.channelId.asLong()
            val listened = config.listenerChannels.filterValues { channelIds -> channelId in channelIds }.keys.toList().sorted()
            val maxPage = ceil(listened.size / 10.0).toInt()
            message.sendPages(0, listened.size / 10, user) { page ->
                basicEmbed(user)
                if (maxPage > 1) setTitle("List of Listeners (Page ${page + 1}/$maxPage)")
                else setTitle("List of Listeners")
                buildSafeDescription {
                    listened.dropAndTake(10 * page, 10).forEach { id ->
                        if (isNotEmpty())
                            append('\n')
                        append("- $id")
                    }
                }
            }
        }
    }

    object Listen : CommandBase {
        override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
            event.validateInGuild()
            event.member.get().validateAdmin()
            require((channel as GuildMessageChannel).getEffectivePermissions(gateway.selfId).block()?.contains(Permission.MANAGE_MESSAGES) == true) { "Linkie currently lacks the `MANAGE_MESSAGES` permission!" }
            args.validateUsage(prefix, 1, "$cmd <id>")
            ChannelListeners[args[0].toLowerCase()]
            val config = ConfigManager[event.guildId.get().asLong()]
            val channels = config.listenerChannels.getOrPut(args[0].toLowerCase(), ::mutableSetOf)
            val channelId = event.message.channelId.asLong()
            if (channels.contains(channelId)) {
                throw IllegalStateException("You have already added this listener to this channel!")
            } else {
                channels.add(channelId)
                ConfigManager.save()
                message.reply {
                    setTitle("Listener added")
                    setDescription("You have successfully added listener id `${args[0].toLowerCase()}`.\nThis message will self-destruct in 20 seconds to keep this channel clean.\n" +
                            "Or alternatively you can just click that ❌ emote.")
                }.subscribe {
                    var deleted = false
                    buildReactions(Duration.ofMinutes(2)) {
                        registerB("❌") {
                            deleted = true
                            it.delete().subscribe()
                            event.message.delete().subscribe()
                            false
                        }
                    }.build(it) { it == user.id }
                    GlobalScope.launch {
                        delay(20000)
                        if (!deleted) {
                            it.delete().subscribe()
                            event.message.delete().subscribe()
                        }
                    }
                }
            }
        }
    }

    object UnListen : CommandBase {
        override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
            event.validateInGuild()
            event.member.get().validateAdmin()
            args.validateUsage(prefix, 1, "$cmd <id>")
            ChannelListeners[args[0].toLowerCase()]
            val config = ConfigManager[event.guildId.get().asLong()]
            val channels = config.listenerChannels.getOrPut(args[0].toLowerCase(), ::mutableSetOf)
            val channelId = event.message.channelId.asLong()
            if (!channels.contains(channelId)) {
                throw IllegalStateException("You have not added this listener to this channel!")
            } else {
                channels.remove(channelId)
                ConfigManager.save()
                message.reply {
                    setTitle("Listener removed")
                    setDescription("You have successfully removed listener id `${args[0].toLowerCase()}`.\nThis message will self-destruct in 20 seconds to keep this channel clean.\n" +
                            "Or alternatively you can just click that ❌ emote.")
                }.subscribe {
                    var deleted = false
                    buildReactions(Duration.ofMinutes(2)) {
                        registerB("❌") {
                            deleted = true
                            it.delete().subscribe()
                            event.message.delete().subscribe()
                            false
                        }
                    }.build(it) { it == user.id }
                    GlobalScope.launch {
                        delay(20000)
                        if (!deleted) {
                            it.delete().subscribe()
                            event.message.delete().subscribe()
                        }
                    }
                }
            }
        }
    }
}