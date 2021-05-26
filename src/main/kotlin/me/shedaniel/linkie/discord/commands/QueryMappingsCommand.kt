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

package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MappingsQueryUtils
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.ValueKeeper
import me.shedaniel.linkie.discord.basicEmbed
import me.shedaniel.linkie.discord.getCatching
import me.shedaniel.linkie.discord.sendPages
import me.shedaniel.linkie.discord.utils.QueryMessageBuilder
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.validateDefaultVersionNotEmpty
import me.shedaniel.linkie.discord.validateGuild
import me.shedaniel.linkie.discord.validateNamespace
import me.shedaniel.linkie.discord.validateUsage
import me.shedaniel.linkie.utils.QueryResult
import me.shedaniel.linkie.utils.ResultHolder
import me.shedaniel.linkie.utils.onlyClass
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

open class QueryMappingsCommand(
    private val namespace: Namespace?,
    private vararg val types: MappingsEntryType,
) : CommandBase {
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        val namespace = getNamespace(event, prefix, cmd, args)
        val mappingsVersion = getMappingsProvider(namespace, args).version!!

        val searchTerm = args.first().replace('.', '/')
        val maxPage = AtomicInteger(-1)
        val query = ValueKeeper(Duration.ofMinutes(2)) {
            query(searchTerm, namespace.getProvider(mappingsVersion), user, message, maxPage)
        }
        message.sendPages(0, maxPage.get()) { page ->
            QueryMessageBuilder.buildMessage(this, namespace, query.get().value, query.get().mappings, page, user, maxPage.get())
        }
    }

    protected suspend fun query(
        searchTerm: String,
        provider: MappingsProvider,
        user: User,
        message: MessageCreator,
        maxPage: AtomicInteger,
    ): QueryResult<MappingsContainer, MutableList<ResultHolder<*>>> {
        val hasWildcard: Boolean = searchTerm.substringBeforeLast('/').onlyClass() == "*" || searchTerm.onlyClass() == "*"
        if (!provider.cached!! || hasWildcard) message.reply {
            basicEmbed(user)
            var desc = "Searching up mappings for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        return message.getCatching(user) {
            val mappings = provider.get()
            QueryResult(mappings, MappingsQueryUtils.query(mappings, searchTerm, *types).also {
                maxPage.set(ceil(it.size / 4.0).toInt())
            })
        }
    }

    protected fun getNamespace(
        event: MessageCreateEvent,
        prefix: String,
        cmd: String,
        args: MutableList<String>,
        providedNamespace: Namespace? = this.namespace,
    ): Namespace {
        if (providedNamespace == null) {
            args.validateUsage(prefix, 2..3, "$cmd <namespace> <search> [version]\nDo !namespaces for list of namespaces.")
        } else {
            args.validateUsage(prefix, 1..2, "$cmd <search> [version]")
        }
        val namespace = providedNamespace ?: (Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
            ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", ")))
        if (providedNamespace == null) args.removeAt(0)
        namespace.validateNamespace()
        namespace.validateGuild(event)
        return namespace
    }

    private suspend fun getMappingsProvider(
        namespace: Namespace,
        args: MutableList<String>,
    ): MappingsProvider =
        getMappingsProvider(namespace, if (args.size == 2) args.last() else null)

    protected suspend fun getMappingsProvider(
        namespace: Namespace,
        version: String?,
        availableVersions: List<String> = namespace.getAllSortedVersions(),
    ): MappingsProvider {
        val mappingsProvider = if (version == null) MappingsProvider.empty(namespace) else namespace.getProvider(version)
        if (mappingsProvider.isEmpty() && version != null) {
            throw NullPointerException(
                "Invalid Version: $version\nVersions: " +
                        if (availableVersions.size > 20)
                            availableVersions.take(20).joinToString(", ") + ", etc"
                        else availableVersions.joinToString(", ")
            )
        }
        mappingsProvider.injectDefaultVersion(namespace.getDefaultProvider())
        mappingsProvider.validateDefaultVersionNotEmpty()
        return mappingsProvider
    }
}