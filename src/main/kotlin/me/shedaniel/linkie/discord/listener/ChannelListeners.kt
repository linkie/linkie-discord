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

package me.shedaniel.linkie.discord.listener

import com.soywiz.klock.minutes
import com.soywiz.korio.async.runBlockingNoJs
import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.NewsChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.util.Permission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.shedaniel.linkie.discord.EmbedCreator
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.gateway
import me.shedaniel.linkie.discord.utils.content
import me.shedaniel.linkie.discord.utils.getOrNull
import me.shedaniel.linkie.discord.utils.sendEmbedMessage
import me.shedaniel.linkie.discord.utils.sendMessage
import me.shedaniel.linkie.utils.getMillis
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File

object ChannelListeners {
    private val listeners = mutableMapOf<String, ChannelListener<*>>()
    private val dir = File(File(System.getProperty("user.dir")), "listeners")
    private val json = Json { }

    operator fun <T> set(id: String, listener: ChannelListener<T>) {
        listeners[id] = listener
    }

    operator fun get(id: String): ChannelListener<*> = listeners[id] ?: throw NullPointerException("Unknown listener: $id\nKnown Listeners: " + listeners.keys.joinToString(", "))

    fun init() {
        val cycleMs = 1.minutes.millisecondsLong
        var nextDelay = getMillis() - cycleMs
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (getMillis() > nextDelay + cycleMs) {
                    reload()
                    nextDelay = getMillis()
                }
                delay(1000)
            }
        }
    }

    private suspend fun reload() {
        coroutineScope {
            listeners.map { (id, listener) ->
                launch {
                    try {
                        reloadListener(id, listener)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }.forEach { it.join() }
            ConfigManager.save()
        }
    }

    private suspend fun <T> reloadListener(id: String, listener: ChannelListener<T>) {
        coroutineScope {
            val dataFile = File(dir, "$id.json")
            val simpleMessages = mutableListOf<String>()
            val embedMessages = mutableListOf<EmbedCreator>()
            val message = object : MessageCreator {
                override val executorId: Snowflake? = null
                override val executorMessage: Message? = null

                override fun reply(content: String): Mono<Message> {
                    simpleMessages.add(content)
                    return Mono.empty()
                }

                override fun reply(content: EmbedCreator): Mono<Message> {
                    embedMessages.add(content)
                    return Mono.empty()
                }
            }

            val data = listener.updateData(dataFile.takeIf(File::exists)?.let { json.decodeFromString(listener.serializer, it.readText()) }, message)
            dataFile.parentFile.mkdirs()
            dataFile.writeText(json.encodeToString(listener.serializer, data))

            if (simpleMessages.isNotEmpty() || embedMessages.isNotEmpty()) {
                ConfigManager.configs.forEach { (guildId, config) ->
                    gateway.getGuildById(Snowflake.of(guildId)).subscribe { guild ->
                        config.listenerChannels[id]?.takeIf { it.isNotEmpty() }?.forEach { channelId ->
                            guild.getChannelById(Snowflake.of(channelId)).subscribe { channel ->
                                simpleMessages.forEach { messageContent ->
                                    (channel as TextChannel).sendMessage {
                                        it.content = messageContent
                                    }.flatMap {
                                        if (channel is NewsChannel && channel.getEffectivePermissions(gateway.selfId).block()?.contains(Permission.MANAGE_MESSAGES) == true)
                                            it.publish()
                                        else Mono.just(it)
                                    }.subscribe()
                                }
                                embedMessages.forEach { messageContent ->
                                    (channel as TextChannel).sendEmbedMessage { runBlockingNoJs { messageContent() } }.flatMap {
                                        if (channel is NewsChannel && channel.getEffectivePermissions(gateway.selfId).block()?.contains(Permission.MANAGE_MESSAGES) == true)
                                            it.publish()
                                        else Mono.just(it)
                                    }.subscribe()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}