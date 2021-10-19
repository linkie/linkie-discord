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
import discord4j.core.`object`.component.ActionComponent
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.LayoutComponent
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.interaction.ComponentInteractionEvent
import me.shedaniel.linkie.discord.utils.extensions.getOrNull
import java.util.*

typealias ComponentFilter = (componentId: String, ComponentInteractionEvent, GatewayDiscordClient, User) -> ComponentActionType
typealias ComponentAction = InteractionMessageCreator.(ComponentInteractionEvent) -> Unit
private typealias InternalComponentFilter = (ComponentInteractionEvent, GatewayDiscordClient, User) -> ComponentActionType

fun customId(): String = UUID.randomUUID().toString()

fun ComponentFilter(filter: ComponentFilter) = filter
private fun InternalComponentFilter(filter: InternalComponentFilter) = filter

enum class ComponentActionType {
    NOT_APPLICABLE,
    ACKNOWLEDGE,
    HANDLE
}

interface ActionComponentAccepter {
    fun add(component: ActionComponent, filter: ComponentFilter, action: ComponentAction)
    fun add(component: ActionComponent)
}

class LayoutComponentsBuilder(
    val components: MutableList<LayoutComponent> = mutableListOf(),
    val actions: MutableMap<InternalComponentFilter, ComponentAction> = mutableMapOf(),
) : ActionComponentAccepter {
    fun row(spec: RowBuilder.() -> Unit) {
        val row = spec.build()
        components.add(ActionRow.of(row.components.toList()))
        actions.putAll(row.actions)
    }

    override fun add(component: ActionComponent, filter: ComponentFilter, action: ComponentAction) = row {
        add(component, filter, action)
    }

    override fun add(component: ActionComponent) = row {
        add(component)
    }
}

class RowBuilder(
    val components: MutableList<ActionComponent> = mutableListOf(),
    val actions: MutableMap<InternalComponentFilter, ComponentAction> = mutableMapOf(),
) : ActionComponentAccepter {
    override fun add(component: ActionComponent, filter: ComponentFilter, action: ComponentAction) {
        components.add(component)
        component.data.customId().toOptional().getOrNull()?.also { id ->
            val internalFilter = InternalComponentFilter { event, client, user -> filter(id, event, client, user) }
            actions[internalFilter] = action
        }
    }

    override fun add(component: ActionComponent) {
        components.add(component)
    }
}