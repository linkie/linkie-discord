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
    fun acknowledge()
    fun acknowledge(content: EmbedCreator)
    fun acknowledge(content: String)

    //    fun _reply(blockIfPossible: Boolean, content: String, components: List<LayoutComponent>): FuturePossible<Message>
//    fun _reply(blockIfPossible: Boolean, content: EmbedCreator, components: List<LayoutComponent>): FuturePossible<Message>
    fun _reply(blockIfPossible: Boolean, spec: MessageCreatorComplex): FuturePossible<Message>

    fun reply(content: String): FuturePossible<Message> = _reply(false, MessageCreatorComplex(TextContent(content)))
    fun reply(content: EmbedCreator): FuturePossible<Message> = _reply(false, MessageCreatorComplex(EmbedContent(content)))

    fun replyComplex(spec: MessageCreatorComplex.() -> Unit) = _reply(false, spec.build())
}

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
    override fun acknowledge() {
    }

    override fun acknowledge(content: EmbedCreator) {
        _reply(true, MessageCreatorComplex(EmbedContent(content)))
    }

    override fun acknowledge(content: String) {
        _reply(true, MessageCreatorComplex(TextContent(content)))
    }

    override fun _reply(blockIfPossible: Boolean, spec: MessageCreatorComplex): FuturePossible<Message> {
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
                spec.layout?.compile(client, user)?.also(it::components)
            }
        } else {
            message!!.sendEdit {
                embeds(listOf())
                content(Possible.absent())
                spec.text?.content?.also(this::contentOrNull)
                spec.embed?.content?.also { creator ->
                    addEmbed(runBlocking { creator.build() })
                }
                spec.layout?.compile(client, user)?.also(this::components)
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
    override fun acknowledge() {
        if (!sent) {
            sent = true
            send(event.acknowledge())
        }
    }

    override fun acknowledge(content: EmbedCreator) = acknowledge()
    override fun acknowledge(content: String) = acknowledge()

    override fun _reply(blockIfPossible: Boolean, spec: MessageCreatorComplex): FuturePossible<Message> {
        if (!sent) {
            sent = true
            send(event.replyMessage {
                embeds(listOf())
                content(Possible.absent())
                spec.text?.content?.also(this::content)
                spec.embed?.content?.also { creator ->
                    addEmbed {
                        val builder = this
                        runBlocking { creator(builder) }
                    }
                }
                spec.layout?.compile(ctx)?.also(this::components)
            })
        } else {
            send(event.sendOriginalEdit {
                embeds(listOf())
                content(Possible.absent())
                spec.text?.content?.also(this::contentOrNull)
                spec.embed?.content?.also { creator ->
                    addEmbed(runBlocking { creator.build() }.asRequest())
                }
                spec.layout?.compile(ctx)?.map { it.data }?.also(this::components)
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
    override fun acknowledge() {
        if (!sent) {
            sent = true
            send(event.acknowledge())
        }
    }

    override fun acknowledge(content: EmbedCreator) = acknowledge()
    override fun acknowledge(content: String) = acknowledge()

    override fun _reply(blockIfPossible: Boolean, spec: MessageCreatorComplex): FuturePossible<Message> {
        if (!sent) {
            sent = true
            send(event.sendEdit {
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
                spec.layout?.compile(client, user)?.also(this::components)
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
                spec.layout?.compile(client, user)?.map { it.data }?.also(this::components)
                extraConfigEdit(spec)
            })
        }
        return FuturePossible.notPossible()
    }

    override fun markDeleted() {
        markDeleted()
    }
}
