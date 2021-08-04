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

import com.soywiz.korio.async.runBlockingNoJs
import discord4j.common.util.Snowflake
import discord4j.core.`object`.component.LayoutComponent
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.interaction.ComponentInteractEvent
import discord4j.core.event.domain.interaction.SlashCommandEvent
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec
import discord4j.discordjson.json.ImmutableWebhookMessageEditRequest
import reactor.core.publisher.Mono
import java.time.Duration

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

    fun reply(blockIfPossible: Boolean, content: String, components: List<LayoutComponent>): FuturePossible<Message>
    fun reply(blockIfPossible: Boolean, content: EmbedCreator, components: List<LayoutComponent>): FuturePossible<Message>

    fun reply(blockIfPossible: Boolean, content: String): FuturePossible<Message> = reply(blockIfPossible, content, listOf())
    fun reply(blockIfPossible: Boolean, content: EmbedCreator): FuturePossible<Message> = reply(blockIfPossible, content, listOf())

    fun reply(content: String): FuturePossible<Message> = reply(false, content, listOf())
    fun reply(content: EmbedCreator): FuturePossible<Message> = reply(false, content, listOf())

    fun reply(ctx: CommandContext, spec: LayoutComponentsBuilder.() -> Unit, content: String): FuturePossible<Message> =
        reply(false, content, listenAndBuild(ctx, spec))

    fun reply(ctx: CommandContext, spec: LayoutComponentsBuilder.() -> Unit, content: EmbedCreator): FuturePossible<Message> =
        reply(false, content, listenAndBuild(ctx, spec))
}

fun listenAndBuild(ctx: CommandContext, spec: LayoutComponentsBuilder.() -> Unit): List<LayoutComponent> {
    val builder = spec.build()
    var actions = builder.actions
    event<ComponentInteractEvent>().take(Duration.ofMinutes(10)).subscribe { event ->
        actions.any { (filter, action) ->
            if (filter(event)) {
                if (event.user.id == ctx.user.id) {
                    var sentAny = false
                    val msgCreator = ComponentInteractMessageCreator(event, extraConfig = {
                        val newBuilder = spec.build()
                        actions = newBuilder.actions
                        components(newBuilder.components.toList())
                    }, {
                        val newBuilder = spec.build()
                        actions = newBuilder.actions
                        components(newBuilder.components.toList().map { it.data })
                    }) {
                        it.subscribe()
                        sentAny = true
                    }
                    action.invoke(msgCreator, event.message.getOrNull())
                    if (!sentAny) {
                        event.acknowledge().subscribe()
                    }
                } else {
                    event.acknowledge().subscribe()
                }
                return@any true
            } else {
                return@any false
            }
        }

    }
    return builder.components.toList()
}

fun MessageChannel.msgCreator(previous: Message?) = MessageCreatorImpl(
    this,
    previous?.id,
    null
)

data class MessageCreatorImpl(
    val channel: MessageChannel,
    val executorMessageId: Snowflake?,
    var message: Message?,
) : MessageCreator {
    override fun acknowledge() {
    }

    override fun acknowledge(content: EmbedCreator) {
        reply(true, content)
    }

    override fun acknowledge(content: String) {
        reply(true, content)
    }

    override fun reply(blockIfPossible: Boolean, content: String, components: List<LayoutComponent>): FuturePossible<Message> {
        return if (message == null) {
            channel.sendMessage {
                it.content = content
                executorMessageId?.also(it::messageReference)
                it.components(components)
            }
        } else {
            message!!.sendEdit {
                contentOrNull(content)
                components(components)
            }
        }.doOnSuccess { message = it }.cache().apply {
            if (blockIfPossible) block()
            else subscribe()
        }.toFuturePossible()
    }

    override fun reply(blockIfPossible: Boolean, content: EmbedCreator, components: List<LayoutComponent>): FuturePossible<Message> {
        return if (message == null) {
            channel.sendMessage {
                it.addEmbed {
                    runBlockingNoJs { content() }
                }
                it.components(components)
            }
        } else {
            message!!.sendEdit {
                addEmbed {
                    runBlockingNoJs { content() }
                }
                components(components)
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
    val send: (Mono<*>) -> Unit,
) : MessageCreator {
    var sent: Boolean = false
    override fun acknowledge() {
        if (!sent) {
            sent = true
            send(event.acknowledge())
        }
    }

    override fun acknowledge(content: EmbedCreator) =
        acknowledge()

    override fun acknowledge(content: String) =
        acknowledge()

    override fun reply(blockIfPossible: Boolean, content: String, components: List<LayoutComponent>): FuturePossible<Message> {
        if (!sent) {
            sent = true
            send(event.replyMessage {
                content(content)
                addAllComponents(components)
            })
        } else {
            send(event.sendOriginalEdit {
                contentOrNull(content)
                addAllComponents(components.map { it.data })
            })
        }
        return FuturePossible.notPossible()
    }

    override fun reply(blockIfPossible: Boolean, content: EmbedCreator, components: List<LayoutComponent>): FuturePossible<Message> {
        if (!sent) {
            sent = true
            send(event.replyMessage {
                addEmbed {
                    runBlockingNoJs {
                        content(this@addEmbed)
                    }
                }
                addAllComponents(components)
            })
        } else {
            send(event.sendOriginalEdit {
                addEmbed(runBlockingNoJs { content.build().asRequest() })
                addAllComponents(components.map { it.data })
            })
        }
        return FuturePossible.notPossible()
    }
}

class ComponentInteractMessageCreator(
    val event: ComponentInteractEvent,
    val extraConfig: InteractionApplicationCommandCallbackSpec.Builder.() -> Unit,
    val extraConfigEdit: ImmutableWebhookMessageEditRequest.Builder.() -> Unit,
    val send: (Mono<*>) -> Unit,
) : MessageCreator {
    var sent: Boolean = false
    override fun acknowledge() {
        if (!sent) {
            sent = true
            send(event.acknowledge())
        }
    }

    override fun acknowledge(content: EmbedCreator) =
        acknowledge()

    override fun acknowledge(content: String) =
        acknowledge()

    override fun reply(blockIfPossible: Boolean, content: String, components: List<LayoutComponent>): FuturePossible<Message> {
        if (!sent) {
            sent = true
            send(event.sendEdit {
                content(content)
                addAllComponents(components)
                extraConfig()
            })
        } else {
            send(event.sendOriginalEdit {
                contentOrNull(content)
                addAllComponents(components.map { it.data })
                extraConfigEdit()
            })
        }
        return FuturePossible.notPossible()
    }

    override fun reply(blockIfPossible: Boolean, content: EmbedCreator, components: List<LayoutComponent>): FuturePossible<Message> {
        if (!sent) {
            sent = true
            send(event.sendEdit {
                addEmbed {
                    runBlockingNoJs {
                        content(this@addEmbed)
                    }
                }
                addAllComponents(components)
                extraConfig()
            })
        } else {
            send(event.sendOriginalEdit {
                addEmbed(runBlockingNoJs { content.build() }.asRequest())
                addAllComponents(components.map { it.data })
                extraConfigEdit()
            })
        }
        return FuturePossible.notPossible()
    }
}
