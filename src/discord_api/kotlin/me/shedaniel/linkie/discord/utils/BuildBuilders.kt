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

import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec
import discord4j.core.spec.MessageCreateSpec
import discord4j.core.spec.MessageEditSpec
import discord4j.discordjson.json.ImmutableWebhookMessageEditRequest
import discord4j.discordjson.json.WebhookMessageEditRequest

fun (MessageCreateSpec.Builder.() -> Unit).build(): MessageCreateSpec =
    MessageCreateSpec.builder().also(this).build()

fun (MessageEditSpec.Builder.() -> Unit).build(): MessageEditSpec =
    MessageEditSpec.builder().also(this).build()

fun (EmbedCreateSpec.Builder.() -> Unit).build(): EmbedCreateSpec =
    EmbedCreateSpec.builder().also(this).build()

suspend fun EmbedCreator.build(): EmbedCreateSpec =
    EmbedCreateSpec.builder().also { this(it) }.build()

fun (InteractionApplicationCommandCallbackSpec.Builder.() -> Unit).build(): InteractionApplicationCommandCallbackSpec =
    InteractionApplicationCommandCallbackSpec.builder().also(this).build()

fun (ImmutableWebhookMessageEditRequest.Builder.() -> Unit).build(): WebhookMessageEditRequest =
    WebhookMessageEditRequest.builder().also(this).build()

fun (MessageCreatorComplex.() -> Unit).build(): MessageCreatorComplex =
    MessageCreatorComplex().also { this(it) }

@JvmName("buildLayoutComponentsBuilderUnit")
fun (LayoutComponentsBuilder.() -> Unit).build(): LayoutComponentsBuilder =
    LayoutComponentsBuilder().also(this)

fun (RowBuilder.() -> Unit).build(): RowBuilder =
    RowBuilder().also(this)
