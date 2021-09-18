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

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.component.LayoutComponent
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.interaction.ComponentInteractEvent
import java.time.Duration

sealed class MessageContent
data class TextContent(val content: String) : MessageContent()
data class EmbedContent(val content: EmbedCreator) : MessageContent()

class MessageCreatorComplex() {
    constructor(content: TextContent) : this() {
        this.text = content
    }

    constructor(content: EmbedContent) : this() {
        this.embed = content
    }

    var layout: (LayoutComponentsBuilder.() -> Unit)? = null
    var text: TextContent? = null
    var embed: EmbedContent? = null

    fun layout(creator: LayoutComponentsBuilder.() -> Unit) {
        layout = creator
    }

    fun text(content: String) {
        text = TextContent(content)
    }

    fun embed(content: EmbedCreator) {
        embed = EmbedContent(content)
    }
}

fun MessageCreatorComplex.compile(ctx: CommandContext): List<LayoutComponent>? {
    return compile(ctx.client, ctx.user)
}

fun MessageCreatorComplex.compile(client: GatewayDiscordClient, user: User): List<LayoutComponent>? {
    if (layout == null) return null
    val builder = layout!!.build()
    var actions = builder.actions
    var reacted = false
    client.event<ComponentInteractEvent>().take(Duration.ofMinutes(10)).subscribe { event ->
        if (reacted) return@subscribe
        actions.any { (filter, action) ->
            when (filter(event, client, user)) {
                ComponentActionType.NOT_APPLICABLE -> return@any false
                ComponentActionType.ACKNOWLEDGE -> {
                    event.acknowledge().subscribe()
                    return@any true
                }
                ComponentActionType.HANDLE -> {
                    var sentAny = false
                    val msgCreator = ComponentInteractMessageCreator(event, client, user, extraConfig = { new ->
                        if (new.layout == null) {
                            val newBuilder = layout!!.build()
                            actions = newBuilder.actions
                            components(newBuilder.components.toList())
                        } else reacted = true
                    }, { new ->
                        if (new.layout == null) {
                            val newBuilder = layout!!.build()
                            actions = newBuilder.actions
                            components(newBuilder.components.toList().map { it.data })
                        } else reacted = true
                    }, {
                        it.subscribe()
                        sentAny = true
                    }, {
                        sentAny = true
                    })
                    action.invoke(msgCreator, event)
                    if (!sentAny) {
                        event.acknowledge().subscribe()
                    }
                    return@any true
                }
            }
        }
    }
    return builder.components.toList()
}
