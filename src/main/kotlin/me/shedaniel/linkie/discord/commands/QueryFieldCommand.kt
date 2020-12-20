/*
 * Copyright (c) 2019, 2020 shedaniel
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
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.*
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

class QueryFieldCommand(private val namespace: Namespace?) : CommandBase {
    override fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        if (this.namespace == null) {
            args.validateUsage(prefix, 2..3, "$cmd <namespace> <search> [version]\nDo !namespaces for list of namespaces.")
        } else args.validateUsage(prefix, 1..2, "$cmd <search> [version]")
        val namespace = this.namespace ?: (Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
            ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", ")))
        if (this.namespace == null) args.removeAt(0)
        namespace.validateNamespace()
        namespace.validateGuild(event)

        val mappingsProvider = if (args.size == 1) MappingsProvider.empty(namespace) else namespace.getProvider(args.last())
        if (mappingsProvider.isEmpty() && args.size == 2) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException(
                "Invalid Version: " + args.last() + "\nVersions: " +
                        if (list.size > 20)
                            list.take(20).joinToString(", ") + ", etc"
                        else list.joinToString(", ")
            )
        }
        mappingsProvider.injectDefaultVersion(namespace.getDefaultProvider {
            if (namespace == YarnNamespace) when (channel.id.asLong()) {
                602959845842485258 -> "legacy"
                661088839464386571 -> "patchwork"
                else -> namespace.getDefaultMappingChannel()
            } else namespace.getDefaultMappingChannel()
        })
        mappingsProvider.validateDefaultVersionNotEmpty()

        val searchKey = args.first().replace('.', '/')
        val version = mappingsProvider.version!!
        val maxPage = AtomicInteger(-1)
        val fields = ValueKeeper(Duration.ofMinutes(2)) { build(searchKey, namespace.getProvider(version), user, message, maxPage) }
        message.sendPages(0, maxPage.get()) { page ->
            buildMessage(namespace, fields.get().value, fields.get().mappings, page, user, maxPage.get())
        }
    }

    private fun build(
        searchKey: String,
        provider: MappingsProvider,
        user: User,
        message: MessageCreator,
        maxPage: AtomicInteger,
        hasClass: Boolean = searchKey.contains('/'),
        hasWildcard: Boolean = (hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") || searchKey.onlyClass() == "*",
    ): QueryResult<MappingsContainer, List<Pair<Class, Field>>> {
        if (!provider.cached!! || hasWildcard) message.sendEmbed {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up fields for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        return message.getCatching(user) {
            val result = MappingsQuery.queryFields(QueryContext(
                provider = provider,
                searchKey = searchKey,
            )).map { it.map { it.value }.toList() }
            if (result.value.isEmpty()) {
                MappingsQuery.errorNoResultsFound(MappingsEntryType.FIELD, searchKey)
            }
            maxPage.set(ceil(result.value.size / 5.0).toInt())
            return@getCatching result
        }
    }

    private fun EmbedCreateSpec.buildMessage(namespace: Namespace, sortedFields: List<Pair<Class, Field>>, mappings: MappingsContainer, page: Int, author: User, maxPage: Int) {
        QueryMessageBuilder.buildHeader(this, mappings, page, author, maxPage)
        buildSafeDescription {
            sortedFields.dropAndTake(5 * page, 5).forEach { (parent, field) ->
                if (isNotEmpty())
                    appendLine().appendLine()
                QueryMessageBuilder.buildField(this, namespace, field, parent, mappings)
            }
        }
    }
}