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

package me.shedaniel.linkie.discord.utils

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.interaction.ComponentInteractEvent
import discord4j.core.event.domain.interaction.SlashCommandEvent
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec
import discord4j.discordjson.json.ImmutableWebhookMessageEditRequest
import discord4j.discordjson.possible.Possible
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.discord.utils.extensions.getOrNull
import me.shedaniel.linkie.discord.utils.extensions.possible
import reactor.core.publisher.Mono

interface FuturePossible<T> {
    val isPossible: Boolean
    fun block(): T?

    companion object {
        fun <T> notPossible(): FuturePossible<T> = object : FuturePossible<T> {
            override val isPossible: Boolean = false
            override fun block(): T? = null
        }
    }
}

interface MessageCreator {
    fun _acknowledge(ephemeral: Boolean?, content: MessageContent?)
    fun _reply(blockIfPossible: Boolean, ephemeral: Boolean?, spec: MessageCreatorComplex): FuturePossible<Message>
}

fun MessageCreator.ephemeral(ephemeral: Boolean = true): MessageCreator = object : MessageCreator {
    override fun _acknowledge(_ephemeral: Boolean?, content: MessageContent?) =
        this@ephemeral._acknowledge(ephemeral, content)

    override fun _reply(blockIfPossible: Boolean, _ephemeral: Boolean?, spec: MessageCreatorComplex): FuturePossible<Message> =
        this@ephemeral._reply(blockIfPossible, ephemeral, spec)
}

fun MessageCreator.acknowledge() = _acknowledge(null, null)
fun MessageCreator.acknowledge(content: String) = _acknowledge(null, TextContent(content))
fun MessageCreator.acknowledge(content: EmbedCreator) = _acknowledge(null, EmbedContent(content))
fun MessageCreator.reply(content: String): FuturePossible<Message> = _reply(false, null, MessageCreatorComplex(TextContent(content)))
fun MessageCreator.reply(content: EmbedCreator): FuturePossible<Message> = _reply(false, null, MessageCreatorComplex(EmbedContent(content)))
fun MessageCreator.replyComplex(spec: MessageCreatorComplex.() -> Unit): FuturePossible<Message> = _reply(false, null, spec.build())

interface InteractionMessageCreator : MessageCreator {
    fun markDeleted()
}

fun MessageChannel.msgCreator(ctx: CommandContext, previous: Message?) = msgCreator(ctx.client, ctx.user, previous)

fun MessageChannel.msgCreator(
    client: GatewayDiscordClient,
    user: User,
    previous: Message?,
) = MessageCreatorImpl(
    this,
    client,
    user,
    previous?.id,
    null
)

data class MessageCreatorImpl(
    val channel: MessageChannel,
    val client: GatewayDiscordClient,
    val user: User,
    val executorMessageId: Snowflake?,
    var message: Message?,
) : MessageCreator {
    override fun _acknowledge(ephemeral: Boolean?, content: MessageContent?) {
        if (content != null) {
            _reply(true, ephemeral, MessageCreatorComplex(content))
        }
    }

    override fun _reply(blockIfPossible: Boolean, ephemeral: Boolean?, spec: MessageCreatorComplex): FuturePossible<Message> {
        return if (message == null) {
            channel.sendMessage {
                it.embeds(listOf())
                it.content(Possible.absent())
                spec.text?.content?.also(it::content)
                spec.embed?.content?.also { creator ->
                    it.addEmbed {
                        val builder = this
                        runBlocking { creator(builder) }
                    }
                }
                executorMessageId?.also(it::messageReference)
                spec.compile(client, user)?.also(it::components)
            }
        } else {
            message!!.sendEdit {
                embeds(listOf())
                content(Possible.absent())
                spec.text?.content?.also(this::contentOrNull)
                spec.embed?.content?.also { creator ->
                    addEmbed(runBlocking { creator.build() })
                }
                spec.compile(client, user)?.also(this::components)
            }
        }.doOnSuccess { message = it }.cache().apply {
            if (blockIfPossible) block()
            else subscribe()
        }.toFuturePossible()
    }
}

private fun <T> Mono<T>.toFuturePossible(): FuturePossible<T> = object : FuturePossible<T> {
    override val isPossible: Boolean = true
    override fun block(): T? = this@toFuturePossible.blockOptional().getOrNull()
}

class SlashCommandMessageCreator(
    val event: SlashCommandEvent,
    val ctx: CommandContext,
    val send: (Mono<*>) -> Unit,
) : MessageCreator {
    var sent: Boolean = false
    override fun _acknowledge(ephemeral: Boolean?, content: MessageContent?) {
        if (!sent) {
            sent = true
            send(if (ephemeral == true) event.acknowledgeEphemeral() else event.acknowledge())
        }
    }

    override fun _reply(blockIfPossible: Boolean, ephemeral: Boolean?, spec: MessageCreatorComplex): FuturePossible<Message> {
        if (!sent) {
            sent = true
            send(event.replyMessage {
                ephemeral(ephemeral.possible())
                embeds(listOf())
                content(Possible.absent())
                spec.text?.content?.also(this::content)
                spec.embed?.content?.also { creator ->
                    addEmbed {
                        val builder = this
                        runBlocking { creator(builder) }
                    }
                }
                spec.compile(ctx)?.also(this::components)
            })
        } else {
            send(event.sendOriginalEdit {
                embeds(listOf())
                content(Possible.absent())
                spec.text?.content?.also(this::contentOrNull)
                spec.embed?.content?.also { creator ->
                    addEmbed(runBlocking { creator.build() }.asRequest())
                }
                spec.compile(ctx)?.map { it.data }?.also(this::components)
            })
        }
        return FuturePossible.notPossible()
    }
}

class ComponentInteractMessageCreator(
    val event: ComponentInteractEvent,
    val client: GatewayDiscordClient,
    val user: User,
    val extraConfig: InteractionApplicationCommandCallbackSpec.Builder.(MessageCreatorComplex) -> Unit,
    val extraConfigEdit: ImmutableWebhookMessageEditRequest.Builder.(MessageCreatorComplex) -> Unit,
    val send: (Mono<*>) -> Unit,
    val markDeleted: () -> Unit,
) : InteractionMessageCreator {
    var sent: Boolean = false
    override fun _acknowledge(ephemeral: Boolean?, content: MessageContent?) {
        if (!sent) {
            sent = true
            send(if (ephemeral == true) event.acknowledgeEphemeral() else event.acknowledge())
        }
    }

    override fun _reply(blockIfPossible: Boolean, ephemeral: Boolean?, spec: MessageCreatorComplex): FuturePossible<Message> {
        if (!sent) {
            sent = true
            send(event.sendEdit {
                ephemeral(ephemeral.possible())
                if (spec.text != null || spec.embed != null) {
                    content(Possible.absent())
                    embeds(listOf())
                }
                spec.text?.content?.also(this::content)
                spec.embed?.content?.also { creator ->
                    addEmbed {
                        val builder = this
                        runBlocking { creator(builder) }
                    }
                }
                spec.compile(client, user)?.also(this::components)
                extraConfig(spec)
            })
        } else {
            send(event.sendOriginalEdit {
                if (spec.text != null || spec.embed != null) {
                    content(Possible.absent())
                    embeds(listOf())
                }
                spec.text?.content?.also(this::contentOrNull)
                spec.embed?.content?.also { creator ->
                    addEmbed(runBlocking { creator.build() }.asRequest())
                }
                spec.compile(client, user)?.map { it.data }?.also(this::components)
                extraConfigEdit(spec)
            })
        }
        return FuturePossible.notPossible()
    }

    override fun markDeleted() {
        markDeleted.invoke()
    }
}
