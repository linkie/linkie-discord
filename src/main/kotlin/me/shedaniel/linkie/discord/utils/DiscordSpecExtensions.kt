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

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec
import discord4j.core.spec.MessageCreateSpec
import discord4j.core.spec.MessageEditSpec
import java.time.Instant
import kotlin.math.min

fun EmbedCreateSpec.Builder.setTimestampToNow(): EmbedCreateSpec.Builder =
    timestamp(Instant.now())

fun EmbedCreateSpec.Builder.addField(name: String, value: String): EmbedCreateSpec.Builder =
    addField(name, value, false)

fun EmbedCreateSpec.Builder.addInlineField(name: String, value: String): EmbedCreateSpec.Builder =
    addField(name, value, true)

fun InteractionApplicationCommandCallbackSpec.Builder.addEmbed(spec: EmbedCreateSpec.Builder.() -> Unit) {
    addEmbed(spec.build())
}

fun MessageCreateSpec.Builder.addEmbed(spec: EmbedCreateSpec.Builder.() -> Unit) {
    addEmbed(spec.build())
}

fun MessageEditSpec.Builder.addEmbed(spec: EmbedCreateSpec.Builder.() -> Unit) {
    addEmbed(spec.build())
}

var MessageCreateSpec.Builder.content: String
    set(value) {
        content(value.substring(0, min(value.length, 2000)))
    }
    get() = throw UnsupportedOperationException()

var EmbedCreateSpec.Builder.description: String
    set(value) {
        description(value.substring(0, min(value.length, 2000)))
    }
    get() = throw UnsupportedOperationException()

fun EmbedCreateSpec.Builder.setSafeDescription(description: String) {
    this.description = description.substring(0, min(description.length, 2000))
}

inline fun EmbedCreateSpec.Builder.buildSafeDescription(builderAction: StringBuilder.() -> Unit) {
    setSafeDescription(buildString(builderAction))
}

fun EmbedCreateSpec.Builder.basicEmbed(
    author: User?,
    footerSuffix: String? = null,
) {
    if (author != null) {
        if (footerSuffix == null) footer("Requested by ${author.discriminatedName}", author.avatarUrl)
        else footer("Requested by ${author.discriminatedName} • $footerSuffix", author.avatarUrl)
    }
    setTimestampToNow()
}
