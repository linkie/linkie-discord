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
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.MappingsMember
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.Command
import me.shedaniel.linkie.discord.MappingsQueryUtils.query
import me.shedaniel.linkie.discord.scommands.CommandOptionMeta
import me.shedaniel.linkie.discord.scommands.OptionsGetter
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.WeakOptionsGetter
import me.shedaniel.linkie.discord.scommands.opt
import me.shedaniel.linkie.discord.scommands.optNullable
import me.shedaniel.linkie.discord.scommands.string
import me.shedaniel.linkie.discord.scommands.sub
import me.shedaniel.linkie.discord.scommands.subGroup
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.MessageCreator
import me.shedaniel.linkie.discord.utils.QueryMessageBuilder
import me.shedaniel.linkie.discord.utils.VersionNamespaceConfig
import me.shedaniel.linkie.discord.utils.acknowledge
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.getCatching
import me.shedaniel.linkie.discord.utils.initiate
import me.shedaniel.linkie.discord.utils.namespace
import me.shedaniel.linkie.discord.utils.optimumName
import me.shedaniel.linkie.discord.utils.sendPages
import me.shedaniel.linkie.discord.utils.suggestVersionsWithNs
import me.shedaniel.linkie.discord.utils.use
import me.shedaniel.linkie.discord.utils.validateDefaultVersionNotEmpty
import me.shedaniel.linkie.discord.utils.validateGuild
import me.shedaniel.linkie.discord.utils.validateNamespace
import me.shedaniel.linkie.discord.utils.validateUsage
import me.shedaniel.linkie.discord.utils.version
import me.shedaniel.linkie.optimumName
import me.shedaniel.linkie.utils.QueryResult
import me.shedaniel.linkie.utils.ResultHolder
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.valueKeeper
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.properties.Delegates

open class QueryMappingsCommand(
    private val namespace: Namespace?,
    private vararg val types: MappingsEntryType,
) : Command {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        if (slash) {
            if (namespace == null) {
                (sequenceOf("all") + MappingsEntryType.values().asSequence().map { it.name.toLowerCase() }).forEach { type ->
                    subGroup(type, "Queries mappings for the '$type' type") {
                        buildNamespaces(slash, if (type == "all") MappingsEntryType.values() else arrayOf(MappingsEntryType.valueOf(type.toUpperCase())))
                    }
                }
            } else {
                buildNamespaces(false, types)
            }
        } else {
            buildNamespaces(slash, types)
        }
    }

    suspend fun SlashCommandBuilderInterface.buildNamespaces(slash: Boolean, types: Array<out MappingsEntryType>) {
        if (slash) {
            Namespaces.namespaces.values.forEach { namespace ->
                sub(namespace.id, "Searches $namespace") {
                    buildExecutor({ namespace }, { _, _ -> namespace }, types)
                }
            }
        } else {
            val namespaceOpt = if (namespace == null) namespace("namespace", "The namespace to query in") else null
            buildExecutor({ namespace ?: it.opt(namespaceOpt!!) }, { cmd, getter -> namespace ?: getter.optNullable(cmd, namespaceOpt!!) }, types)
        }
    }

    suspend fun SlashCommandBuilderInterface.buildExecutor(namespaceGetter: (OptionsGetter) -> Namespace, weakNamespaceGetter: (String, WeakOptionsGetter) -> Namespace?, types: Array<out MappingsEntryType>) {
        var version by Delegates.notNull<CommandOptionMeta<MappingsProvider, VersionNamespaceConfig>>()
        val searchTerm = string("search_term", "The search term to filter with") {
            suggest { _, options, sink ->
                runBlocking {
                    val value = options.optNullable(this@string)?.replace('.', '/')?.replace('#', '/') ?: ""
                    val namespace = weakNamespaceGetter(options.cmd, options) ?: return@runBlocking
                    val provider = options.optNullable(version, VersionNamespaceConfig(namespace)) ?: namespace.getDefaultProvider()
                    val mappings = provider.get()
                    val result = query(mappings, value, *types)
                    val suggestions = result.results.asSequence().take(25).map { (value, _) ->
                        when {
                            value is Class -> {
//                                if (value.mappedName != null) {
//                                    sink.choice("Class: ${value.optimumSimpleName} (${value.intermediaryName})", value.intermediaryName)
//                                } else {
//                                    sink.choice("Class: ${value.intermediaryName}", value.intermediaryName)
//                                }
                                sink.choice(value.optimumName)
                            }
                            value is Pair<*, *> && value.second is MappingsMember -> {
                                val parent = value.first as Class
                                val member = value.second as MappingsMember
                                val type = if (member is Method) "Method" else "Field"
//                                if (member.mappedName != null) {
//                                    sink.choice("$type: ${parent.optimumSimpleName}.${member.optimumName} (${member.intermediaryName})", "${parent.intermediaryName}.${member.intermediaryName}")
//                                } else {
//                                    sink.choice("$type: ${parent.optimumSimpleName}.${member.intermediaryName}", "${parent.intermediaryName}.${member.intermediaryName}")
//                                }
                                sink.choice("${parent.optimumName}.${member.optimumName}")
                            }
                            else -> throw IllegalStateException("Unknown type: $value")
                        }
                    }.toList()
                    sink.suggest(suggestions)
                }
            }
        }
        version = version("version", "The version to query for", required = false) {
            suggestVersionsWithNs {
                weakNamespaceGetter(it.cmd, it)
            }
        }
        executeCommandWithGetter { ctx, options ->
            ctx.message.acknowledge()
            val ns = namespaceGetter(options)
            ns.validateNamespace()
            ns.validateGuild(ctx)
            val nsVersion = options.opt(version, VersionNamespaceConfig(ns))
            val searchTermStr = options.opt(searchTerm).replace('.', '/').replace('#', '/')
            execute(ctx, ns, nsVersion.version!!, searchTermStr, types)
        }
    }

    suspend fun execute(ctx: CommandContext, namespace: Namespace, version: String, searchTerm: String, types: Array<out MappingsEntryType>) = ctx.use {
        val fuzzy = AtomicBoolean(false)
        val maxPage = AtomicInteger(-1)
        val query by valueKeeper {
            QueryMappingsExtensions.query(searchTerm, namespace.getProvider(version), user, message, maxPage, fuzzy, types)
        }.initiate()
        message.sendPages(0, maxPage.get()) { page ->
            QueryMessageBuilder.buildMessage(this, searchTerm, namespace, query.value, query.mappings, page, user, maxPage.get(), fuzzy.get())
        }
    }

    protected fun getNamespace(
        ctx: CommandContext,
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
        namespace.validateGuild(ctx)
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
        mappingsProvider.injectDefaultVersion {
            runBlocking {
                val defaultProvider = namespace.getDefaultProvider()
                if (availableVersions.contains(defaultProvider.version)) {
                    defaultProvider
                } else {
                    namespace.getProvider(availableVersions.firstOrNull() ?: throw IllegalStateException("No available versions found!"))
                }
            }
        }
        mappingsProvider.validateDefaultVersionNotEmpty()
        return mappingsProvider
    }
}

object QueryMappingsExtensions {
    suspend fun query(
        searchTerm: String,
        provider: MappingsProvider,
        user: User,
        message: MessageCreator,
        maxPage: AtomicInteger,
        fuzzy: AtomicBoolean,
        types: Array<out MappingsEntryType>,
    ): QueryResult<MappingsContainer, MutableList<ResultHolder<*>>> {
        val hasWildcard: Boolean = searchTerm.substringBeforeLast('/').onlyClass() == "*" || searchTerm.onlyClass() == "*"
        if (!provider.cached!! || hasWildcard) message.acknowledge {
            basicEmbed(user)
            var desc = "Searching up mappings for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (hasWildcard) desc += "\nCurrently using wildcards, might take a while."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }
        return message.getCatching(user) {
            val mappings = provider.get()
            QueryResult(mappings, query(mappings, searchTerm, *types).let {
                maxPage.set(ceil(it.results.size / 4.0).toInt())
                fuzzy.set(it.fuzzy)
                it.results
            })
        }
    }
}
