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

import discord4j.core.`object`.component.ActionComponent
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.LayoutComponent
import discord4j.core.`object`.entity.Message
import discord4j.core.event.domain.interaction.ComponentInteractEvent
import java.util.*

typealias ComponentFilter = (ComponentInteractEvent) -> Boolean
typealias ComponentAction = MessageCreator.(Message?) -> Unit

fun customId(): String = UUID.randomUUID().toString()

val String.componentFilter: ComponentFilter
    get() = { it.customId == this }

interface ActionComponentAccepter {
    fun add(component: ActionComponent, filter: ComponentFilter, action: ComponentAction)
    fun add(component: ActionComponent)
}

class LayoutComponentsBuilder(
    val components: MutableList<LayoutComponent> = mutableListOf(),
    val actions: MutableMap<ComponentFilter, ComponentAction> = mutableMapOf(),
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
    val actions: MutableMap<ComponentFilter, ComponentAction> = mutableMapOf(),
) : ActionComponentAccepter {
    override fun add(component: ActionComponent, filter: ComponentFilter, action: ComponentAction) {
        components.add(component)
        actions[filter] = action
    }

    override fun add(component: ActionComponent) {
        components.add(component)
    }
}