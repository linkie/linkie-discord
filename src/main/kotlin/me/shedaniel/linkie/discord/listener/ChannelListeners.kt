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
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildMessageChannel
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
import me.shedaniel.linkie.discord.utils.sendEmbedMessage
import me.shedaniel.linkie.discord.utils.sendMessage
import me.shedaniel.linkie.utils.getMillis
import me.shedaniel.linkie.utils.info
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File

object ChannelListeners {
    private val listeners = mutableMapOf<String, ChannelListener<*>>()
    private val dir = File(File(System.getProperty("user.dir")), "listeners")
    private val json = Json {  }
    
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
        }
    }
    
    private suspend fun <T> reloadListener(id: String, listener: ChannelListener<T>) {
        coroutineScope {
            val dataFile = File(dir, "$id.json")
            val flux: Flux<MutableList<GuildMessageChannel>> = ConfigManager.configs.mapNotNull { (guildId, config) ->
                config.listenerChannels[id]?.takeIf { it.isNotEmpty() }?.let { channelIds ->
                    val guild = gateway.getGuildById(Snowflake.of(guildId)).blockOptional().get()
                    Flux.fromIterable(channelIds)
                        .flatMap { guild.getChannelById(Snowflake.of(it)) }
                        .filter { it is GuildMessageChannel }
                        .map { it as GuildMessageChannel }
                        .collectList()
                }
            }.let { Flux.fromIterable(it) }.flatMap { it }
            val channels: List<GuildMessageChannel> = flux.collectList().block()?.flatten() ?: emptyList()
            val message = object : MessageCreator {
                override val executor: User? = null

                override fun send(content: String): Mono<Message> {
                    var mono: Mono<Message>? = null
                    channels.forEach { channel ->
                        val message = channel.sendMessage {
                            it.content = content
                        }.flatMap { 
                            if (channel.getEffectivePermissions(gateway.selfId).block()?.contains(Permission.MANAGE_MESSAGES) == true)
                                it.publish()
                            else Mono.just(it)
                        }
                        mono = if (mono == null) message
                        else mono!!.then(message)
                    }
                    return mono ?: Mono.empty()
                }

                override fun sendEmbed(content: EmbedCreator): Mono<Message> {
                    var mono: Mono<Message>? = null
                    channels.forEach { channel ->
                        val message = channel.sendEmbedMessage { runBlockingNoJs { content() } }.flatMap {
                            if (channel.getEffectivePermissions(gateway.selfId).block()?.contains(Permission.MANAGE_MESSAGES) == true)
                                it.publish()
                            else Mono.just(it)
                        }
                        mono = if (mono == null) message
                        else mono!!.then(message)
                    }
                    return mono ?: Mono.empty()
                }
            }
            info("Updating data for $id")
            val data = listener.updateData(dataFile.takeIf(File::exists)?.let { json.decodeFromString(listener.serializer, it.readText()) }, message)
            info("Updated data for $id")
            dataFile.parentFile.mkdirs()
            dataFile.writeText(json.encodeToString(listener.serializer, data))
        }
    }
}