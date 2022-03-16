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

import com.soywiz.korio.async.runBlockingNoJs
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.scommands.CommandOptionMeta
import me.shedaniel.linkie.discord.scommands.SimpleCommandOptionMeta
import me.shedaniel.linkie.discord.scommands.SlashCommandOptionAcceptor
import me.shedaniel.linkie.discord.scommands.StringCommandOption
import me.shedaniel.linkie.discord.scommands.SuggestionOptionsGetter
import me.shedaniel.linkie.discord.scommands.map
import me.shedaniel.linkie.discord.scommands.mapCompound
import me.shedaniel.linkie.discord.scommands.optNullable
import me.shedaniel.linkie.discord.scommands.string
import me.shedaniel.linkie.utils.similarity

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
    val defaultVersion: String = namespace.defaultVersion,
    val availableVersions: (Namespace) -> List<String> = Namespace::getAllSortedVersions,
)

fun StringCommandOption.suggestVersionsWithNs(namespaceGetter: (SuggestionOptionsGetter) -> Namespace?) {
    suggestStrings {
        namespaceGetter(it)?.getAllSortedVersions()
    }
}

fun StringCommandOption.suggestStrings(versionsGetter: (SuggestionOptionsGetter) -> List<String>?) {
    suggest { _, options, sink ->
        runBlocking {
            val value = options.optNullable(this@suggestStrings) ?: ""
            val versions = versionsGetter(options) ?: return@runBlocking
            val suggestions = versions.asSequence()
                .sortedWith(
                    compareByDescending<String> { if (it.startsWith(value)) 1 else 0 }
                        .thenByDescending { it.similarity(value) }
                )
                .map { sink.choice(it, it) }
                .toList()
            sink.suggest(suggestions)
        }
    }
}

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
            val defaultProvider = namespace.getProvider(config.defaultVersion)
            if (!defaultProvider.isEmpty() && versions.contains(defaultProvider.version)) {
                defaultProvider
            } else {
                namespace.getProvider(versions.firstOrNull() ?: throw IllegalStateException("No available versions found!"))
            }
        }
    }
    provider.validateDefaultVersionNotEmpty()
    provider
}
