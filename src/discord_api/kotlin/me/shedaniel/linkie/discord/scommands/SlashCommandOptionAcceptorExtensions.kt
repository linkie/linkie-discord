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

import me.shedaniel.linkie.discord.utils.CommandContext

fun <T, R> SimpleCommandOptionMeta<T>.map(mapper: (T?) -> R?): SimpleCommandOptionMeta<R> = object : SimpleCommandOptionMeta<R> {
    override val parents: List<CommandOptionProperties>
        get() = this@map.parents
    override val description: String
        get() = this@map.description
    override val required: Boolean
        get() = this@map.required

    override fun name(cmd: String): String =
        this@map.name(cmd)

    override fun mapValue(value: Any?): R? =
        mapper(this@map.mapValue(value))
}

fun <T, E, R> SimpleCommandOptionMeta<T>.mapCompound(mapper: (T?, E) -> R?): CommandOptionMeta<R, E> = object : CommandOptionMeta<R, E> {
    override val parents: List<CommandOptionProperties>
        get() = this@mapCompound.parents
    override val description: String
        get() = this@mapCompound.description
    override val required: Boolean
        get() = this@mapCompound.required

    override fun name(cmd: String): String =
        this@mapCompound.name(cmd)

    override fun mapValue(value: Any?, extra: E): R? =
        mapper(this@mapCompound.mapValue(value), extra)
}

inline fun SlashCommandOptionAcceptor.args(
    required: Boolean = true,
    builder: StringCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<MutableList<String>> = stringUnlimited("args", "The arguments for the command", required, builder).map { args -> args?.splitArgs() }
