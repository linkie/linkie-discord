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

import discord4j.core.`object`.component.Button
import discord4j.core.`object`.component.SelectMenu
import discord4j.core.`object`.component.TextInput
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent
import me.shedaniel.linkie.discord.utils.extensions.getOrNull

data class ComponentFilterProvider(val filter: ComponentFilter)

fun ComponentFilterProvider?.fillDefault(): ComponentFilter =
    this?.filter ?: { id, event, _, user ->
        when {
            event.customId != id -> ComponentActionType.NOT_APPLICABLE
            event.user.id != user.id -> ComponentActionType.ACKNOWLEDGE
            else -> ComponentActionType.HANDLE
        }
    }

fun ActionComponentAccepter.primaryButton(label: String, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.primary(id, label).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.primaryButton(emoji: ReactionEmoji, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.primary(id, emoji).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.primaryButton(label: String, emoji: ReactionEmoji, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.primary(id, emoji, label).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.secondaryButton(label: String, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.secondary(id, label).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.secondaryButton(emoji: ReactionEmoji, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.secondary(id, emoji).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.secondaryButton(label: String, emoji: ReactionEmoji, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.secondary(id, emoji, label).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.successButton(label: String, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.success(id, label).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.successButton(emoji: ReactionEmoji, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.success(id, emoji).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.successButton(label: String, emoji: ReactionEmoji, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.success(id, emoji, label).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.dangerButton(label: String, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.danger(id, label).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.dangerButton(emoji: ReactionEmoji, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.danger(id, emoji).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.dangerButton(label: String, emoji: ReactionEmoji, disabled: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    add(Button.danger(id, emoji, label).disabled(disabled), filter.fillDefault(), action)
}

fun ActionComponentAccepter.linkButton(label: String, url: String, disabled: Boolean = false) =
    add(Button.link(url, label).disabled(disabled))


fun ActionComponentAccepter.linkButton(emoji: ReactionEmoji, url: String, disabled: Boolean = false) =
    add(Button.link(url, emoji).disabled(disabled))

fun ActionComponentAccepter.linkButton(label: String, emoji: ReactionEmoji, url: String, disabled: Boolean = false) =
    add(Button.link(url, emoji, label).disabled(disabled))

fun ActionComponentAccepter.dismissButton() = secondaryButton("Dismiss", "❌".discordEmote) {
    it.message.getOrNull()?.delete()?.subscribe()
    markDeleted()
}

fun ActionComponentAccepter.selectMenu(spec: SelectMenuBuilder.() -> Unit) = customId().also { id ->
    val (menu, builder) = spec.build(id)
    add(menu, builder.filter.fillDefault(), builder.action)
}

fun ActionComponentAccepter.smallText(label: String? = null, value: String? = null, placeholder: String? = null, required: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    var input = TextInput.small(id, label)
    placeholder?.let { input = input.placeholder(it) }
    value?.let { input = input.prefilled(it) }
    add(input.required(required), filter.fillDefault(), action)
}

fun ActionComponentAccepter.smallText(label: String? = null, minLength: Int, maxLength: Int, value: String? = null, placeholder: String? = null, required: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    var input = TextInput.small(id, label, minLength, maxLength)
    placeholder?.let { input = input.placeholder(it) }
    value?.let { input = input.prefilled(it) }
    add(input.required(required), filter.fillDefault(), action)
}

fun ActionComponentAccepter.paragraphText(label: String? = null, value: String? = null, placeholder: String? = null, required: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    var input = TextInput.paragraph(id, label)
    placeholder?.let { input = input.placeholder(it) }
    value?.let { input = input.prefilled(it) }
    add(input.required(required), filter.fillDefault(), action)
}

fun ActionComponentAccepter.paragraphText(label: String? = null, minLength: Int, maxLength: Int, value: String? = null, placeholder: String? = null, required: Boolean = false, filter: ComponentFilterProvider? = null, action: ComponentAction = {}) = customId().also { id ->
    var input = TextInput.paragraph(id, label, minLength, maxLength)
    placeholder?.let { input = input.placeholder(it) }
    value?.let { input = input.prefilled(it) }
    add(input.required(required), filter.fillDefault(), action)
}

class SelectMenuBuilder(
    var options: MutableList<SelectMenu.Option> = mutableListOf(),
    var disabled: Boolean = false,
    var placeholder: String? = null,
    var minValues: Int? = null,
    var maxValues: Int? = null,
    var action: ComponentAction = {},
    var filter: ComponentFilterProvider? = null,
) {
    fun build(customId: String): SelectMenu {
        var menu = SelectMenu.of(customId, options).disabled(disabled)
        if (placeholder != null) menu = menu.withPlaceholder(placeholder!!)
        if (minValues != null) menu = menu.withMinValues(minValues!!)
        if (maxValues != null) menu = menu.withMaxValues(maxValues!!)
        return menu
    }

    fun addOptions(options: Collection<SelectMenu.Option>) {
        this.options.addAll(options)
    }

    fun addOptions(vararg options: SelectMenu.Option) {
        this.options.addAll(options)
    }

    fun addDefaultOption(
        label: String,
        value: String,
        description: String? = null,
        emoji: ReactionEmoji? = null,
    ) {
        addOption(label, value, description, emoji, default = true)
    }

    fun addOption(
        label: String,
        value: String,
        description: String? = null,
        emoji: ReactionEmoji? = null,
        default: Boolean = false,
    ) {
        var option = if (default) SelectMenu.Option.ofDefault(label, value) else SelectMenu.Option.of(label, value)
        if (description != null) option = option.withDescription(description)
        if (emoji != null) option = option.withEmoji(emoji)
        this.options.add(option)
    }

    fun disabled(disabled: Boolean = true) {
        this.disabled = disabled
    }

    fun placeholder(placeholder: String?) {
        this.placeholder = placeholder
    }

    fun minValues(minValues: Int?) {
        this.minValues = minValues
    }

    fun maxValues(maxValues: Int?) {
        this.maxValues = maxValues
    }

    fun action(action: MessageCreator.(SelectMenuInteractionEvent, List<String>) -> Unit) {
        this.action = {
            it as SelectMenuInteractionEvent
            action(it, it.values)
        }
    }

    fun filter(filter: ComponentFilterProvider?) {
        this.filter = filter
    }
}
