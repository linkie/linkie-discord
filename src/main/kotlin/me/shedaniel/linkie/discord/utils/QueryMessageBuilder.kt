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

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsMetadata
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.basicEmbed
import me.shedaniel.linkie.getMappedDesc
import me.shedaniel.linkie.utils.ResultHolder
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.localiseFieldDesc

object QueryMessageBuilder {
    fun buildMessage(spec: EmbedCreateSpec, namespace: Namespace, results: List<ResultHolder<*>>, mappings: MappingsMetadata, page: Int, author: User, maxPage: Int) {
        buildHeader(spec, mappings, page, author, maxPage)
        spec.buildSafeDescription {
            var isFirst = true
            results.dropAndTake(4 * page, 4).forEach { (value, _) ->
                if (isFirst) {
                    isFirst = false
                } else {
                    appendLine().appendLine()
                }
                when {
                    value is Class -> buildClass(this, namespace, value)
                    value is Pair<*, *> && value.second is Field ->
                        buildField(this, namespace, value.second as Field, value.first as Class, mappings as? MappingsContainer)
                    value is Pair<*, *> && value.second is Method ->
                        buildMethod(this, namespace, value.second as Method, value.first as Class, mappings as? MappingsContainer)
                }
            }
        }
    }

    fun buildHeader(spec: EmbedCreateSpec, metadata: MappingsMetadata, page: Int, author: User, maxPage: Int) = spec.apply {
        basicEmbed(author, metadata.mappingsSource?.toString())
        if (maxPage > 1) setTitle("List of ${metadata.name} Mappings for ${metadata.version} (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${metadata.name} Mappings for ${metadata.version}")
    }

    fun buildClass(builder: StringBuilder, namespace: Namespace, classEntry: Class) = builder.apply {
        appendLine("**Class: __${classEntry.optimumName}__**")
        append("__Name__: ")
        append(classEntry.obfName.buildString(nonEmptySuffix = " => "))
        append("`${classEntry.intermediaryName}`")
        append(classEntry.mappedName.mapIfNotNullOrNotEquals(classEntry.intermediaryName) { " => `$it`" } ?: "")
        if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible class ${classEntry.optimumName}`")
        } else if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${classEntry.optimumName.replace('/', '.')}`")
        }
    }

    fun buildField(
        builder: StringBuilder,
        namespace: Namespace,
        field: Field,
        parent: Class,
        mappings: MappingsContainer?,
    ) = buildField(builder, namespace, field, parent.optimumName, mappings)

    fun buildField(
        builder: StringBuilder,
        namespace: Namespace,
        field: Field,
        parent: String,
        mappings: MappingsContainer?,
    ) = builder.apply {
        appendLine("**Field: $parent#__${field.optimumName}__**")
        append("__Name__: ")
        append(field.obfName.buildString(nonEmptySuffix = " => "))
        append("`${field.intermediaryName}`")
        append(field.mappedName.mapIfNotNullOrNotEquals(field.intermediaryName) { " => `$it`" } ?: "")

        if (mappings == null) return@apply
        val mappedDesc = field.getMappedDesc(mappings)
        if (namespace.supportsFieldDescription()) {
            appendLine().append("__Type__: ")
            append(mappedDesc.localiseFieldDesc())
        }
        if (namespace.supportsMixin()) {
            appendLine().append("__Mixin Target__: `")
            append("L$parent;")
            append(field.optimumName)
            append(':')
            append(mappedDesc)
            append('`')
        }
        if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${parent.replace('/', '.')} ")
            append(field.intermediaryName)
            append(" # ")
            append(field.optimumName)
            append('`')
        } else if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible field ")
            append(parent)
            append(' ')
            append(field.optimumName)
            append(' ')
            append(mappedDesc)
            append('`')
        }
    }

    fun buildMethod(
        builder: StringBuilder,
        namespace: Namespace,
        method: Method,
        parent: Class,
        mappings: MappingsContainer?,
    ) = buildMethod(builder, namespace, method, parent.optimumName, mappings)

    fun buildMethod(
        builder: StringBuilder,
        namespace: Namespace,
        method: Method,
        parent: String,
        mappings: MappingsContainer?,
    ) = builder.apply {
        appendLine("**Method: $parent#__${method.optimumName}__**")
        append("__Name__: ")
        append(method.obfName.buildString(nonEmptySuffix = " => "))
        append("`${method.intermediaryName}`")
        append(method.mappedName.mapIfNotNullOrNotEquals(method.intermediaryName) { " => `$it`" } ?: "")

        if (mappings == null) return@apply
        val mappedDesc = method.getMappedDesc(mappings)
        if (namespace.supportsMixin()) {
            appendLine().append("__Mixin Target__: `")
            append("L$parent;")
            append(method.optimumName)
            append(mappedDesc)
            append('`')
        }
        if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${parent.replace('/', '.')} ")
            append(method.intermediaryName)
            append(mappedDesc)
            append(" # ")
            append(method.optimumName)
            append('`')
        } else if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible method ")
            append(parent)
            append(' ')
            append(method.optimumName)
            append(' ')
            append(mappedDesc)
            append('`')
        }
    }
}