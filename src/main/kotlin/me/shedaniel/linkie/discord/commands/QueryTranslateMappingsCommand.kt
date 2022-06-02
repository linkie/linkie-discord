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

import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.MappingsMember
import me.shedaniel.linkie.MappingsMetadata
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.Command
import me.shedaniel.linkie.discord.MappingsQueryUtils
import me.shedaniel.linkie.discord.lang.i18n
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
import me.shedaniel.linkie.discord.utils.VersionNamespaceConfig
import me.shedaniel.linkie.discord.utils.acknowledge
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.buildSafeDescription
import me.shedaniel.linkie.discord.utils.buildString
import me.shedaniel.linkie.discord.utils.initiate
import me.shedaniel.linkie.discord.utils.mapIfNotNullOrNotEquals
import me.shedaniel.linkie.discord.utils.namespace
import me.shedaniel.linkie.discord.utils.optimumName
import me.shedaniel.linkie.discord.utils.sendPages
import me.shedaniel.linkie.discord.utils.suggestStrings
import me.shedaniel.linkie.discord.utils.use
import me.shedaniel.linkie.discord.utils.validateGuild
import me.shedaniel.linkie.discord.utils.validateNamespace
import me.shedaniel.linkie.discord.utils.version
import me.shedaniel.linkie.getClassByObfName
import me.shedaniel.linkie.getObfMergedDesc
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.MojangNamespace
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.obfMergedName
import me.shedaniel.linkie.optimumName
import me.shedaniel.linkie.utils.MemberEntry
import me.shedaniel.linkie.utils.QueryResult
import me.shedaniel.linkie.utils.ResultHolder
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.valueKeeper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

class QueryTranslateMappingsCommand(
    private val source: Namespace?,
    private val target: Namespace?,
    private vararg val types: MappingsEntryType,
) : Command {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        if (slash) {
            if (source == null && target == null) {
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
            val bridges: List<Pair<Namespace, Namespace>> = listOf(
                YarnNamespace to MCPNamespace,
                YarnNamespace to MojangNamespace,
                MCPNamespace to MojangNamespace,
            )
            bridges.forEach { (src, dst) ->
                val srcId = src.id.substringBeforeLast("_srg")
                val dstId = dst.id.substringBeforeLast("_srg")
                sub(srcId + "_to_" + dstId, "Translates from $srcId to $dstId") {
                    buildExecutor({ src }, { _, _ -> src }, { dst }, { _, _ -> dst }, types)
                }
                sub(dstId + "_to_" + srcId, "Translates from $dstId to $srcId") {
                    buildExecutor({ dst }, { _, _ -> src }, { src }, { _, _ -> dst }, types)
                }
            }
        } else {
            val srcNamespaceOpt = if (source == null) namespace("source", "The source namespace to query in") else null
            val dstNamespaceOpt = if (target == null) namespace("target", "The target namespace to query in") else null
            buildExecutor(
                { source ?: it.opt(srcNamespaceOpt!!) },
                { cmd, getter -> source ?: getter.optNullable(cmd, srcNamespaceOpt!!) },
                { target ?: it.opt(dstNamespaceOpt!!) },
                { cmd, getter -> target ?: getter.optNullable(cmd, dstNamespaceOpt!!) },
                types
            )
        }
    }

    suspend fun SlashCommandBuilderInterface.buildExecutor(
        srcNamespaceGetter: (OptionsGetter) -> Namespace,
        weakSrcNamespaceGetter: (String, WeakOptionsGetter) -> Namespace?,
        dstNamespaceGetter: (OptionsGetter) -> Namespace,
        weakDstNamespaceGetter: (String, WeakOptionsGetter) -> Namespace?,
        types: Array<out MappingsEntryType>,
    ) {
        var version by Delegates.notNull<CommandOptionMeta<MappingsProvider, VersionNamespaceConfig>>()
        val searchTerm = string("search_term", "The search term to filter with") {
            suggest { _, options, sink ->
                runBlocking {
                    val rawValue = options.optNullable(this@string) ?: ""
                    val value = rawValue.replace('.', '/').replace('#', '/')
                    val src = weakSrcNamespaceGetter(options.cmd, options) ?: return@runBlocking
                    val dst = weakDstNamespaceGetter(options.cmd, options) ?: return@runBlocking

                    val allVersions = src.getAllSortedVersions().toMutableList()
                    allVersions.retainAll(dst.getAllSortedVersions())

                    val defaultVersion = src.defaultVersion.takeIf { it in allVersions } ?: dst.defaultVersion.takeIf { it in allVersions } ?: allVersions.first()

                    val provider = options.optNullable(version, VersionNamespaceConfig(src, defaultVersion) { allVersions }) ?: src.getProvider(defaultVersion)
                    val mappings = provider.get()
                    val result = MappingsQueryUtils.query(mappings, value, *types)
                    val suggestions = result.results.asSequence().sortedByDescending { it.score }.map { (value, _) ->
                        when {
                            value is Class -> {
                                sink.choice(value.optimumName)
                            }
                            value is MemberEntry<*> -> {
                                val (parent, member) = value
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
            suggestStrings {
                val src = weakSrcNamespaceGetter(it.cmd, it) ?: return@suggestStrings emptyList()
                val dst = weakDstNamespaceGetter(it.cmd, it) ?: return@suggestStrings emptyList()
                val allVersions = src.getAllSortedVersions().toMutableList()
                allVersions.retainAll(dst.getAllSortedVersions())
                allVersions
            }
        }
        executeCommandWithGetter { ctx, options ->
            ctx.message.acknowledge()
            val src = srcNamespaceGetter(options)
            val dst = dstNamespaceGetter(options)

            src.validateNamespace()
            src.validateGuild(ctx)
            dst.validateNamespace()
            dst.validateGuild(ctx)

            val allVersions = src.getAllSortedVersions().toMutableList()
            allVersions.retainAll(dst.getAllSortedVersions())

            val defaultVersion = src.defaultVersion.takeIf { it in allVersions } ?: dst.defaultVersion.takeIf { it in allVersions } ?: allVersions.first()

            val srcVersion = options.opt(version, VersionNamespaceConfig(src, defaultVersion) { allVersions })
            val dstVersion = options.opt(version, VersionNamespaceConfig(dst, defaultVersion) { allVersions })

            require(srcVersion.version == dstVersion.version) {
                "Unmatched versions: ${srcVersion.version}, ${dstVersion.version}! Please report this!"
            }

            val searchTermStr = options.opt(searchTerm).replace('.', '/').replace('#', '/')
            execute(ctx, src, dst, srcVersion.version!!, searchTermStr, types)
        }
    }

    suspend fun execute(ctx: CommandContext, src: Namespace, dst: Namespace, version: String, searchTerm: String, types: Array<out MappingsEntryType>) = ctx.use {
        val fuzzy = AtomicBoolean(false)
        val maxPage = AtomicInteger(-1)
        val result by valueKeeper {
            translate(QueryMappingsExtensions.query(searchTerm, src.getProvider(version), user, message, maxPage, fuzzy, types), dst.getProvider(version))
        }.initiate()
        message.sendPages(0, maxPage.get()) { page ->
            basicEmbed(user)
            if (maxPage.get() > 1) title("List of ${result.source.name}->${result.target.name} Mappings (Page ${page + 1}/${maxPage.get()})")
            else title("List of ${result.source.name}->${result.target.name} Mappings")
            buildSafeDescription {
                append("text.mappings.query.new_site".i18n(ctx.locale)).appendLine().appendLine()

                if (fuzzy.get()) {
                    append("text.mappings.query.fuzzy_matched".i18n(ctx, searchTerm)).appendLine()
                }

                var isFirst = true
                result.value.dropAndTake(4 * page, 4).forEach { (original, translated) ->
                    if (!isFirst) {
                        isFirst = false
                    } else {
                        appendLine().appendLine()
                    }
                    when (original) {
                        is Class -> buildClass(ctx.locale, original, translated as Class)
                        is FieldResult -> buildField(ctx.locale, original, translated as FieldResult)
                        is MethodResult -> buildMethod(ctx.locale, original, translated as MethodResult)
                    }
                }
            }
        }
    }

    private fun StringBuilder.buildClass(locale: String?, original: Class, translated: Class) {
        appendLine("text.mappings.query.class.translate".i18n(locale, original.optimumName, translated.optimumName))
        append("text.mappings.query.name".i18n(locale))
        append(original.mappedName.mapIfNotNullOrNotEquals(original.intermediaryName) { "`$it` => " } ?: "")
        append("`${original.intermediaryName}`")
        if (original.intermediaryName != translated.intermediaryName) {
            append(" => ")
            append(original.obfName.buildString(nonEmptySuffix = " => "))
            append("`${translated.intermediaryName}`")
        }
        append(translated.mappedName.mapIfNotNullOrNotEquals(translated.intermediaryName) { " => `$it`" } ?: "")
    }

    private fun StringBuilder.buildField(locale: String?, original: FieldResult, translated: FieldResult) {
        appendLine("text.mappings.query.field.translate".i18n(locale, original.parent, original.field.optimumName, translated.parent, translated.field.optimumName))
        append("text.mappings.query.name".i18n(locale))
        append(original.field.mappedName.mapIfNotNullOrNotEquals(original.field.intermediaryName) { "`$it` => " } ?: "")
        append("`${original.field.intermediaryName}`")
        if (original.field.intermediaryName != translated.field.intermediaryName) {
            append(" => ")
            append(original.field.obfName.buildString(nonEmptySuffix = " => "))
            append("`${translated.field.intermediaryName}`")
        }
        append(translated.field.mappedName.mapIfNotNullOrNotEquals(translated.field.intermediaryName) { " => `$it`" } ?: "")
    }

    private fun StringBuilder.buildMethod(locale: String?, original: MethodResult, translated: MethodResult) {
        appendLine("text.mappings.query.method.translate".i18n(locale, original.parent, original.method.optimumName, translated.parent, translated.method.optimumName))
        append("text.mappings.query.name".i18n(locale))
        append(original.method.mappedName.mapIfNotNullOrNotEquals(original.method.intermediaryName) { "`$it` => " } ?: "")
        append("`${original.method.intermediaryName}`")
        if (original.method.intermediaryName != translated.method.intermediaryName) {
            append(" => ")
            append(original.method.obfName.buildString(nonEmptySuffix = " => "))
            append("`${translated.method.intermediaryName}`")
        }
        append(translated.method.mappedName.mapIfNotNullOrNotEquals(translated.method.intermediaryName) { " => `$it`" } ?: "")
    }

    private suspend fun translate(
        result: QueryResult<MappingsContainer, MutableList<ResultHolder<*>>>,
        targetProvider: MappingsProvider,
    ): TranslationResult<MutableList<Pair<*, *>>> {
        val source = result.mappings
        val target = targetProvider.get()
        val newResult = mutableListOf<Pair<*, *>>()

        result.value.forEach { (value, _) ->
            when {
                value is Class -> {
                    val obfName = value.obfMergedName!!
                    val targetClass = target.getClassByObfName(obfName) ?: return@forEach
                    newResult.add(value to targetClass)
                }
                value is MemberEntry<*> && value.member is Field -> {
                    val parent = value.owner
                    val field = value.member as Field

                    val obfName = field.obfMergedName!!
                    val parentObfName = parent.obfMergedName!!
                    val targetParent = target.getClassByObfName(parentObfName) ?: return@forEach
                    val targetField = targetParent.fields.firstOrNull { it.obfMergedName == obfName } ?: return@forEach

                    newResult.add(FieldResult(parent.optimumName, field) to FieldResult(targetParent.optimumName, targetField))
                }
                value is MemberEntry<*> && value.member is Method -> {
                    val parent = value.owner
                    val method = value.member as Method

                    val obfName = method.obfMergedName!!
                    val obfDesc = method.getObfMergedDesc(source)
                    val parentObfName = parent.obfMergedName!!
                    val targetParent = target.getClassByObfName(parentObfName) ?: return@forEach
                    val targetMethod = targetParent.methods.firstOrNull { it.obfMergedName == obfName && it.getObfMergedDesc(target) == obfDesc } ?: return@forEach

                    newResult.add(MethodResult(parent.optimumName, method) to MethodResult(targetParent.optimumName, targetMethod))
                }
            }
        }

        if (newResult.isEmpty()) {
            throw NullPointerException("Some results were found, but was unable to translate to the target namespace! Please report this issue!")
        }

        return TranslationResult(
            source.toSimpleMappingsMetadata(),
            target.toSimpleMappingsMetadata(),
            newResult
        )
    }

}

private data class TranslationResult<T>(
    val source: MappingsMetadata,
    val target: MappingsMetadata,
    val value: T,
)

private data class MethodResult(
    val parent: String,
    val method: Method,
)

private data class FieldResult(
    val parent: String,
    val field: Field,
)
