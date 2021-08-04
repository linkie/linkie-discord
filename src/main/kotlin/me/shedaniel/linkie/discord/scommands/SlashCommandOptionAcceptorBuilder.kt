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

package me.shedaniel.linkie.discord.scommands

import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel

inline fun SlashCommandOptionAcceptor.sub(
    name: String,
    description: String,
    builder: SubCommandOption.() -> Unit = {},
) = arg(SubCommandOption(name, description, parents).also(builder))

inline fun SlashCommandOptionAcceptor.subGroup(
    name: String,
    description: String,
    builder: SubGroupCommandOption.() -> Unit = {},
) = arg(SubGroupCommandOption(name, description, parents).also(builder))

inline fun SlashCommandOptionAcceptor.string(
    name: String,
    description: String,
    required: Boolean = true,
    builder: StringCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<String> = StringCommandOption(name, description, parents).also(applyRequired(required)).also(builder).also(this::arg)

inline fun SlashCommandOptionAcceptor.stringUnlimited(
    name: String,
    description: String,
    required: Boolean = true,
    builder: StringCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<String> = StringCommandOption(name, description, parents).also(applyRequired(required)).also(builder).also(this::arg)

fun <T : AbstractSlashCommandOption<*>> applyRequired(required: Boolean): (T) -> Unit = {
    it.required = required
}

inline fun SlashCommandOptionAcceptor.int(
    name: String,
    description: String,
    required: Boolean = true,
    builder: IntegerCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<Long> = IntegerCommandOption(name, description, parents).also(applyRequired(required)).also(builder).also(this::arg)

inline fun SlashCommandOptionAcceptor.bool(
    name: String,
    description: String,
    required: Boolean = true,
    builder: BooleanCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<Boolean> = BooleanCommandOption(name, description, parents).also(applyRequired(required)).also(builder).also(this::arg)

inline fun SlashCommandOptionAcceptor.user(
    name: String,
    description: String,
    required: Boolean = true,
    builder: UserCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<User> = UserCommandOption(name, description, parents).also(applyRequired(required)).also(builder).also(this::arg)

inline fun SlashCommandOptionAcceptor.channel(
    name: String,
    description: String,
    required: Boolean = true,
    builder: ChannelCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<Channel> = ChannelCommandOption(name, description, parents).also(applyRequired(required)).also(builder).also(this::arg)

inline fun SlashCommandOptionAcceptor.role(
    name: String,
    description: String,
    required: Boolean = true,
    builder: RoleCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<Role> = RoleCommandOption(name, description, parents).also(applyRequired(required)).also(builder).also(this::arg)

//    fun mentionable(
//        name: String,
//        description: String,
//        builder: MentionableCommandOption.() -> Unit = {},
//    ): SlashCommandOptionMeta = MentionableCommandOption(name, description, parents).also(applyRequired(required)).also(builder).also(this::arg)
