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

package me.shedaniel.linkie.discord.handler

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.discord.scommands.splitArgs
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.MessageBasedCommandContext
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.buildReactions
import me.shedaniel.linkie.discord.utils.dismissButton
import me.shedaniel.linkie.discord.utils.event
import me.shedaniel.linkie.discord.utils.replyComplex
import me.shedaniel.linkie.discord.utils.sendEmbedMessage
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CommandHandler(
    private val client: GatewayDiscordClient,
    private val commandAcceptor: CommandAcceptor,
    private val throwableHandler: ThrowableHandler,
    val rateLimiter: RateLimiter = RateLimiter(Int.MAX_VALUE),
) {
    val executor: ExecutorService = Executors.newCachedThreadPool()
    fun register() {
        client.event(this::onMessageCreate)
    }

    fun onMessageCreate(event: MessageCreateEvent) {
        val channel = event.message.channel.block() ?: return
        val user = event.message.author.orElse(null)?.takeUnless { it.isBot } ?: return
        val message: String = event.message.content
        val prefix = commandAcceptor.getPrefix(event)
        try {
            executor.submit {
                if (message.lowercase().startsWith(prefix)) {
                    val content = message.substring(prefix.length)
                    if (!rateLimiter.allow(user.id.asLong())) {
                        throwableHandler.generateErrorMessage(event.message, RateLimitException(rateLimiter.maxRequestPer10Sec), channel, user)
                        return@submit
                    }
                    val split = content.splitArgs()
                    if (split.isNotEmpty()) {
                        val cmd = split[0].lowercase()
                        val ctx = MessageBasedCommandContext(event, prefix, cmd, channel)
                        val args = split.drop(1).toMutableList()
                        try {
                            runBlocking {
                                commandAcceptor.execute(event, ctx, args)
                            }
                        } catch (throwable: Throwable) {
                            if (throwableHandler.shouldError(throwable)) {
                                try {
                                    ctx.message.replyComplex {
                                        layout { dismissButton() }
                                        embed { throwableHandler.generateThrowable(this, throwable, user) }
                                    }
                                } catch (throwable2: Exception) {
                                    throwable2.addSuppressed(throwable)
                                    throwable2.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }.get(10, TimeUnit.SECONDS)
        } catch (throwable: TimeoutException) {
            val newThrowable = TimeoutException("The command took too long to execute, the maximum execution time is 10 seconds.")
            throwableHandler.generateErrorMessage(event.message, newThrowable, channel, user)
        } catch (throwable: ExecutionException) {
            throwableHandler.generateErrorMessage(event.message, throwable.cause ?: throwable, channel, user)
        } catch (throwable: Throwable) {
            throwableHandler.generateErrorMessage(event.message, throwable, channel, user)
        }
    }
}

interface CommandAcceptor {
    suspend fun execute(event: MessageCreateEvent, ctx: CommandContext, args: MutableList<String>): Boolean
    fun getPrefix(event: MessageCreateEvent): String
}

interface ThrowableHandler {
    fun shouldError(throwable: Throwable): Boolean = true
    fun generateErrorMessage(original: Message?, throwable: Throwable, channel: MessageChannel, user: User)
    fun generateThrowable(builder: EmbedCreateSpec.Builder, throwable: Throwable, user: User)
}

open class SimpleThrowableHandler : ThrowableHandler {
    override fun generateErrorMessage(original: Message?, throwable: Throwable, channel: MessageChannel, user: User) {
        if (!shouldError(throwable)) return
        try {
            channel.sendEmbedMessage { generateThrowable(this, throwable, user) }.subscribe { message ->
                buildReactions(Duration.ofMinutes(2)) {
                    registerB("❌") {
                        message.delete().subscribe()
                        original?.delete()?.subscribe()
                        false
                    }
                }.build(message) { it == user.id }
            }
        } catch (throwable2: Exception) {
            throwable2.addSuppressed(throwable)
            throwable2.printStackTrace()
        }
    }

    override fun generateThrowable(builder: EmbedCreateSpec.Builder, throwable: Throwable, user: User) {
        builder.apply {
            title("Error Occurred")
            color(Color.RED)
            basicEmbed(user)
            addField("Error occurred while processing the command:", throwable.javaClass.simpleName + ": " + (throwable.localizedMessage ?: "Unknown Message"), false)
        }
    }
}
