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

package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.MessageBasedCommandContext
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.buildReactions
import me.shedaniel.linkie.discord.utils.dismissButton
import me.shedaniel.linkie.discord.utils.msgCreator
import me.shedaniel.linkie.discord.utils.sendEmbedMessage
import java.time.Duration

class CommandMap(private val commandAcceptor: CommandAcceptor, private val defaultPrefix: String) {
    val scope = CoroutineScope(Dispatchers.Default)

    fun onMessageCreate(event: MessageCreateEvent) {
        val channel = event.message.channel.block() ?: return
        val user = event.message.author.orElse(null)?.takeUnless { it.isBot } ?: return
        val message: String = event.message.content
        val prefix = commandAcceptor.getPrefix(event) ?: defaultPrefix
        scope.launch {
            try {
                if (message.toLowerCase().startsWith(prefix)) {
                    val content = message.substring(prefix.length)
                    val split = content.splitArgs()
                    if (split.isNotEmpty()) {
                        val cmd = split[0].toLowerCase()
                        val ctx = MessageBasedCommandContext(event, channel.msgCreator(event.message), prefix, cmd, channel)
                        val args = split.drop(1).toMutableList()
                        try {
                            commandAcceptor.execute(event, ctx, args)
                        } catch (throwable: Throwable) {
                            if (throwable is SuppressedException) return@launch
                            try {
                                ctx.message.reply(ctx, {
                                    dismissButton()
                                }) {
                                    generateThrowable(throwable, user)
                                }
                            } catch (throwable2: Exception) {
                                throwable2.addSuppressed(throwable)
                                throwable2.printStackTrace()
                            }
                        }
                    }
                }
            } catch (throwable: Throwable) {
                if (throwable is SuppressedException) return@launch
                try {
                    channel.sendEmbedMessage { generateThrowable(throwable, user) }.subscribe { message ->
                        buildReactions(Duration.ofMinutes(2)) {
                            registerB("❌") {
                                message.delete().subscribe()
                                event.message.delete().subscribe()
                                false
                            }
                        }.build(message) { it == user.id }
                    }
                } catch (throwable2: Exception) {
                    throwable2.addSuppressed(throwable)
                    throwable2.printStackTrace()
                }
            }
        }
    }
}

fun String.splitArgs(): MutableList<String> {
    val args = mutableListOf<String>()
    val stringBuilder = StringBuilder()
    forEach {
        val whitespace = it.isWhitespace()
        if (whitespace) {
            args.add(stringBuilder.toString())
            stringBuilder.clear()
        }
        if (it == '\n' || !whitespace) {
            stringBuilder.append(it)
        }
    }
    if (stringBuilder.isNotEmpty())
        args.add(stringBuilder.toString())
    return args.dropLastWhile(String::isEmpty).toMutableList()
}

interface CommandAcceptor {
    suspend fun execute(event: MessageCreateEvent, ctx: CommandContext, args: MutableList<String>)
    fun getPrefix(event: MessageCreateEvent): String?
}

fun EmbedCreateSpec.Builder.generateThrowable(throwable: Throwable, user: User? = null) {
    title("Linkie Error")
    color(Color.RED)
    basicEmbed(user)
    when {
        throwable is org.graalvm.polyglot.PolyglotException -> {
            val details = throwable.localizedMessage ?: ""
            addField("Error occurred while processing the command", "```$details```", false)
        }
        throwable.javaClass.name.startsWith("org.graalvm") -> {
            val details = throwable.localizedMessage ?: ""
            addField("Error occurred while processing the command", "```" + throwable.javaClass.name + (if (details.isEmpty()) "" else ":\n") + details + "```", false)
        }
        else -> {
            addField("Error occurred while processing the command:", throwable.javaClass.simpleName + ": " + (throwable.localizedMessage ?: "Unknown Message"), false)
        }
    }
    if (isDebug)
        throwable.printStackTrace()
}