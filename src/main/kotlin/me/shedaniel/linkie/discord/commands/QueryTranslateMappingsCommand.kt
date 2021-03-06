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
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.MappingsMetadata
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.ValueKeeper
import me.shedaniel.linkie.discord.basicEmbed
import me.shedaniel.linkie.discord.sendPages
import me.shedaniel.linkie.discord.utils.buildSafeDescription
import me.shedaniel.linkie.discord.utils.buildString
import me.shedaniel.linkie.discord.utils.mapIfNotNullOrNotEquals
import me.shedaniel.linkie.discord.utils.optimumName
import me.shedaniel.linkie.getClassByObfName
import me.shedaniel.linkie.getObfMergedDesc
import me.shedaniel.linkie.obfMergedName
import me.shedaniel.linkie.utils.QueryResult
import me.shedaniel.linkie.utils.ResultHolder
import me.shedaniel.linkie.utils.dropAndTake
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class QueryTranslateMappingsCommand(
    private val source: Namespace,
    private val target: Namespace,
    vararg types: MappingsEntryType,
) : QueryMappingsCommand(source, *types) {
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        val sourceNamespace = getNamespace(event, prefix, cmd, args, source)
        val targetNamespace = getNamespace(event, prefix, cmd, args, target)

        val allVersions = source.getAllSortedVersions().toMutableList()
        allVersions.retainAll(target.getAllSortedVersions())
        val providedVersion = if (args.size == 2) args.last() else null

        val sourceMappingsVersion = getMappingsProvider(sourceNamespace, providedVersion, allVersions).version!!
        val targetMappingsVersion = getMappingsProvider(targetNamespace, providedVersion, allVersions).version!!

        require(sourceMappingsVersion == targetMappingsVersion) {
            "Unmatched versions: $sourceMappingsVersion, $targetMappingsVersion! Please report this!"
        }

        val searchTerm = args.first().replace('.', '/')
        val maxPage = AtomicInteger(-1)
        val query = ValueKeeper(Duration.ofMinutes(2)) {
            translate(query(searchTerm, sourceNamespace.getProvider(sourceMappingsVersion), user, message, maxPage), targetNamespace.getProvider(targetMappingsVersion))
        }
        message.sendPages(0, maxPage.get()) { page ->
            basicEmbed(user)
            val result = query.get()
            if (maxPage.get() > 1) setTitle("List of ${result.source.name}->${result.target.name} Mappings (Page ${page + 1}/${maxPage.get()})")
            else setTitle("List of ${result.source.name}->${result.target.name} Mappings")
            buildSafeDescription {
                var isFirst = true
                result.value.dropAndTake(4 * page, 4).forEach { (original, translated) ->
                    if (!isFirst) {
                        isFirst = false
                    } else {
                        appendLine().appendLine()
                    }
                    when (original) {
                        is Class -> buildClass(original, translated as Class)
                        is FieldResult -> buildField(original, translated as FieldResult)
                        is MethodResult -> buildMethod(original, translated as MethodResult)
                    }
                }
            }
        }
    }

    private fun StringBuilder.buildClass(original: Class, translated: Class) {
        appendLine("**Class: ${original.optimumName} => __${translated.optimumName}__**")
        append("__Name__: ")
        append(original.mappedName.mapIfNotNullOrNotEquals(original.intermediaryName) { "`$it` => " } ?: "")
        append("`${original.intermediaryName}`")
        if (original.intermediaryName != translated.intermediaryName) {
            append(" => ")
            append(original.obfName.buildString(nonEmptySuffix = " => "))
            append("`${translated.intermediaryName}`")
        }
        append(translated.mappedName.mapIfNotNullOrNotEquals(translated.intermediaryName) { " => `$it`" } ?: "")
    }

    private fun StringBuilder.buildField(original: FieldResult, translated: FieldResult) {
        appendLine("**Field: ${original.parent}#${original.field.optimumName} => __${translated.parent}#${translated.field.optimumName}__**")
        append("__Name__: ")
        append(original.field.mappedName.mapIfNotNullOrNotEquals(original.field.intermediaryName) { "`$it` => " } ?: "")
        append("`${original.field.intermediaryName}`")
        if (original.field.intermediaryName != translated.field.intermediaryName) {
            append(" => ")
            append(original.field.obfName.buildString(nonEmptySuffix = " => "))
            append("`${translated.field.intermediaryName}`")
        }
        append(translated.field.mappedName.mapIfNotNullOrNotEquals(translated.field.intermediaryName) { " => `$it`" } ?: "")
    }

    private fun StringBuilder.buildMethod(original: MethodResult, translated: MethodResult) {
        appendLine("**Method: ${original.parent}#${original.method.optimumName} => __${translated.parent}#${translated.method.optimumName}__**")
        append("__Name__: ")
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
                value is Pair<*, *> && value.second is Field -> {
                    val parent = value.first as Class
                    val field = value.second as Field

                    val obfName = field.obfMergedName!!
                    val parentObfName = parent.obfMergedName!!
                    val targetParent = target.getClassByObfName(parentObfName) ?: return@forEach
                    val targetField = targetParent.fields.firstOrNull { it.obfMergedName == obfName } ?: return@forEach

                    newResult.add(FieldResult(parent.optimumName, field) to FieldResult(targetParent.optimumName, targetField))
                }
                value is Pair<*, *> && value.second is Method -> {
                    val parent = value.first as Class
                    val method = value.second as Method

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
