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

import com.soywiz.korio.async.runBlockingNoJs
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.splitArgs
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.validateDefaultVersionNotEmpty

fun <T, R> SimpleCommandOptionMeta<T>.map(mapper: (T?) -> R?): SimpleCommandOptionMeta<R> = object : SimpleCommandOptionMeta<R> {
    override val parents: List<CommandOptionProperties>
        get() = this@map.parents
    override val description: String
        get() = this@map.description
    override val required: Boolean
        get() = this@map.required

    override fun name(ctx: CommandContext): String =
        this@map.name(ctx)

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

    override fun name(ctx: CommandContext): String =
        this@mapCompound.name(ctx)

    override fun mapValue(value: Any?, extra: E): R? =
        mapper(this@mapCompound.mapValue(value), extra)
}

inline fun SlashCommandOptionAcceptor.namespace(
    name: String,
    description: String,
    required: Boolean = true,
    builder: StringCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<Namespace> = string(name, description, required) {
    Namespaces.namespaces.keys.forEach(this::choice)
    builder()
}.map { namespaceName ->
    namespaceName?.let {
        Namespaces.namespaces[it] ?: throw IllegalArgumentException("Invalid Namespace: $it\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", "))
    }
}

data class VersionNamespaceConfig(
    val namespace: Namespace,
    val availableVersions: (Namespace) -> List<String> = Namespace::getAllSortedVersions,
)

fun SlashCommandOptionAcceptor.version(
    name: String,
    description: String,
    required: Boolean = true,
    builder: StringCommandOption.() -> Unit = {},
): CommandOptionMeta<MappingsProvider, VersionNamespaceConfig> = string(name, description, required, builder).mapCompound { version, config ->
    val namespace = config.namespace
    val provider = runBlockingNoJs { if (version != null) namespace.getProvider(version) else MappingsProvider.empty(namespace) }
    val versions = config.availableVersions(namespace)
    if (provider.isEmpty() && version != null) {
        throw NullPointerException(
            "Invalid Version: $version\nVersions: " +
                    if (versions.size > 20)
                        versions.take(20).joinToString(", ") + ", etc"
                    else versions.joinToString(", ")
        )
    }
    provider.injectDefaultVersion {
        runBlockingNoJs {
            val defaultProvider = namespace.getDefaultProvider()
            if (versions.contains(defaultProvider.version)) {
                defaultProvider
            } else {
                namespace.getProvider(versions.firstOrNull() ?: throw IllegalStateException("No available versions found!"))
            }
        }
    }
    provider.validateDefaultVersionNotEmpty()
    provider
}

inline fun SlashCommandOptionAcceptor.args(
    required: Boolean = true,
    builder: StringCommandOption.() -> Unit = {},
): SimpleCommandOptionMeta<MutableList<String>> = stringUnlimited("args", "The arguments for the command", required, builder).map { args -> args?.splitArgs() }
