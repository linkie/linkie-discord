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
import discord4j.core.`object`.reaction.ReactionEmoji

fun ActionComponentAccepter.primaryButton(label: String, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.primary(id, label).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.primaryButton(emoji: ReactionEmoji, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.primary(id, emoji).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.primaryButton(label: String, emoji: ReactionEmoji, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.primary(id, emoji, label).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.secondaryButton(label: String, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.secondary(id, label).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.secondaryButton(emoji: ReactionEmoji, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.secondary(id, emoji).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.secondaryButton(label: String, emoji: ReactionEmoji, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.secondary(id, emoji, label).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.successButton(label: String, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.success(id, label).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.successButton(emoji: ReactionEmoji, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.success(id, emoji).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.successButton(label: String, emoji: ReactionEmoji, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.success(id, emoji, label).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.dangerButton(label: String, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.danger(id, label).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.dangerButton(emoji: ReactionEmoji, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.danger(id, emoji).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.dangerButton(label: String, emoji: ReactionEmoji, disabled: Boolean = false, action: ComponentAction = {}) = customId().also { id ->
    add(Button.danger(id, emoji, label).disabled(disabled), id.componentFilter, action)
}

fun ActionComponentAccepter.linkButton(label: String, url: String, disabled: Boolean = false) =
    add(Button.link(url, label).disabled(disabled))


fun ActionComponentAccepter.linkButton(emoji: ReactionEmoji, url: String, disabled: Boolean = false) =
    add(Button.link(url, emoji).disabled(disabled))

fun ActionComponentAccepter.linkButton(label: String, emoji: ReactionEmoji, url: String, disabled: Boolean = false) =
    add(Button.link(url, emoji, label).disabled(disabled))

fun ActionComponentAccepter.dismissButton() = secondaryButton("Dismiss", "❌".discordEmote) {
    it?.delete()?.subscribe()
}
