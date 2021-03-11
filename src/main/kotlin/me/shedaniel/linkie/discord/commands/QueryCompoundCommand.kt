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
import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.*
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.*
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

class QueryCompoundCommand(private val namespace: Namespace?) : CommandBase {
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
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
        val methods = ValueKeeper(Duration.ofMinutes(2)) {
            build(searchKey, namespace.getProvider(version), user, message, maxPage)
        }
        message.sendPages(0, maxPage.get()) { page ->
            buildMessage(namespace, methods.get().value, methods.get().mappings, page, user, maxPage.get())
        }
    }

    private suspend fun build(
        searchKey: String,
        provider: MappingsProvider,
        user: User,
        message: MessageCreator,
        maxPage: AtomicInteger,
        hasClass: Boolean = searchKey.contains('/'),
        hasWildcard: Boolean = (hasClass && searchKey.substring(0, searchKey.lastIndexOf('/')).onlyClass() == "*") || searchKey.onlyClass('/') == "*",
    ): QueryResult<MappingsContainer, List<ResultHolder<*>>> {
        if (!provider.cached!! || hasWildcard) message.reply {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up entries for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        return message.getCatching(user) {
            val mappingsContainer = provider.get()
            val context = QueryContext(
                provider = MappingsProvider.of(provider.namespace, mappingsContainer.version, mappingsContainer),
                searchKey = searchKey,
            )
            val result: MutableList<ResultHolder<*>> = mutableListOf()
            var classes: ClassResultSequence? = null
            var methods: MethodResultSequence? = null
            var fields: FieldResultSequence? = null
            runBlocking {
                launch {
                    try {
                        classes = MappingsQuery.queryClasses(context).value
                    } catch (e: NullPointerException) {

                    }
                }
                launch {
                    try {
                        methods = MappingsQuery.queryMethods(context).value
                    } catch (e: NullPointerException) {

                    }
                }
                launch {
                    try {
                        fields = MappingsQuery.queryFields(context).value
                    } catch (e: NullPointerException) {

                    }
                }
            }
            classes?.also(result::addAll)
            methods?.also(result::addAll)
            fields?.also(result::addAll)
            result.sortByDescending { it.score }

            if (result.isEmpty()) {
                runBlocking {
                    launch {
                        try {
                            classes = MappingsQuery.queryClasses(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (e: NullPointerException) {

                        }
                    }
                    launch {
                        try {
                            methods = MappingsQuery.queryMethods(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (e: NullPointerException) {

                        }
                    }
                    launch {
                        try {
                            fields = MappingsQuery.queryFields(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (e: NullPointerException) {

                        }
                    }
                }
                classes?.also(result::addAll)
                methods?.also(result::addAll)
                fields?.also(result::addAll)
                result.sortByDescending { it.score }

                if (result.isEmpty()) {
                    if (searchKey.onlyClass().firstOrNull()?.isDigit() == true && !searchKey.onlyClass().isValidIdentifier()) {
                        throw NullPointerException("No results found! `${searchKey.onlyClass()}` is not a valid java identifier!")
                    }
                    throw NullPointerException("No results found!")
                }
            }

            maxPage.set(ceil(result.size / 5.0).toInt())
            return@getCatching QueryResult(mappingsContainer, result)
        }
    }

    private fun EmbedCreateSpec.buildMessage(namespace: Namespace, sortedResults: List<ResultHolder<*>>, mappings: MappingsContainer, page: Int, author: User, maxPage: Int) {
        QueryMessageBuilder.buildHeader(this, mappings, page, author, maxPage)
        buildSafeDescription {
            sortedResults.dropAndTake(3 * page, 3).forEach { (value, _) ->
                if (isNotEmpty())
                    appendLine().appendLine()
                when {
                    value is Class -> {
                        QueryMessageBuilder.buildClass(this, namespace, value, mappings)
                    }
                    value is Pair<*, *> && value.second is Field -> {
                        QueryMessageBuilder.buildField(this, namespace, value.second as Field, value.first as Class, mappings)
                    }
                    value is Pair<*, *> && value.second is Method -> {
                        QueryMessageBuilder.buildMethod(this, namespace, value.second as Method, value.first as Class, mappings)
                    }
                }
            }
        }
    }
}