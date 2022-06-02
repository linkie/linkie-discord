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
import me.shedaniel.linkie.discord.lang.i18n
import me.shedaniel.linkie.getMappedDesc
import me.shedaniel.linkie.utils.MemberEntry
import me.shedaniel.linkie.utils.ResultHolder
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.localiseFieldDesc

object QueryMessageBuilder {
    fun buildMessage(spec: EmbedCreateSpec.Builder, locale: String?, searchTerm: String, namespace: Namespace, results: List<ResultHolder<*>>, mappings: MappingsMetadata, page: Int, author: User, maxPage: Int, fuzzy: Boolean) {
        buildHeader(spec, locale, mappings, page, author, maxPage)
        spec.buildSafeDescription {
            append("text.mappings.query.new_site".i18n(locale)).appendLine().appendLine()

            if (fuzzy) {
                append("text.mappings.query.fuzzy_matched".i18n(locale, searchTerm)).appendLine().appendLine()
            }

            var isFirst = true
            results.dropAndTake(4 * page, 4).forEach { (value, _) ->
                if (isFirst) {
                    isFirst = false
                } else {
                    appendLine().appendLine()
                }
                when {
                    value is Class -> buildClass(this, locale, namespace, value)
                    value is MemberEntry<*> && value.member is Field ->
                        buildField(this, locale, namespace, value.member as Field, value.owner, mappings as? MappingsContainer)
                    value is MemberEntry<*> && value.member is Method ->
                        buildMethod(this, locale, namespace, value.member as Method, value.owner, mappings as? MappingsContainer)
                }
            }
        }
    }

    fun buildHeader(spec: EmbedCreateSpec.Builder, locale: String?, metadata: MappingsMetadata, page: Int, author: User, maxPage: Int) = spec.apply {
        basicEmbed(author, metadata.mappingsSource?.toString())
        if (maxPage > 1) title("text.mappings.query.title.paged".i18n(locale, metadata.name, metadata.version, page + 1, maxPage))
        else title("text.mappings.query.title".i18n(locale, metadata.name, metadata.version))
    }

    fun buildClass(builder: StringBuilder, locale: String?, namespace: Namespace, classEntry: Class) = builder.apply {
        appendLine("text.mappings.query.class".i18n(locale, classEntry.optimumName))
        append("text.mappings.query.name".i18n(locale))
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
        locale: String?,
        namespace: Namespace,
        field: Field,
        parent: Class,
        mappings: MappingsContainer?,
    ) = buildField(builder, locale, namespace, field, parent.optimumName, mappings)

    fun buildField(
        builder: StringBuilder,
        locale: String?,
        namespace: Namespace,
        field: Field,
        parent: String,
        mappings: MappingsContainer?,
    ) = builder.apply {
        appendLine("text.mappings.query.field".i18n(locale, parent, field.optimumName))
        append("text.mappings.query.name".i18n(locale))
        append(field.obfName.buildString(nonEmptySuffix = " => "))
        append("`${field.intermediaryName}`")
        append(field.mappedName.mapIfNotNullOrNotEquals(field.intermediaryName) { " => `$it`" } ?: "")

        if (mappings == null) return@apply
        val mappedDesc = field.getMappedDesc(mappings)
        if (namespace.supportsFieldDescription()) {
            appendLine().append("text.mappings.query.type".i18n(locale))
            append(mappedDesc.localiseFieldDesc())
        }
        if (namespace.supportsMixin()) {
            appendLine().append("text.mappings.query.mixin".i18n(locale))
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
        locale: String?,
        namespace: Namespace,
        method: Method,
        parent: Class,
        mappings: MappingsContainer?,
    ) = buildMethod(builder, locale, namespace, method, parent.optimumName, mappings)

    fun buildMethod(
        builder: StringBuilder,
        locale: String?,
        namespace: Namespace,
        method: Method,
        parent: String,
        mappings: MappingsContainer?,
    ) = builder.apply {
        appendLine("text.mappings.query.method".i18n(locale, parent, method.optimumName))
        append("text.mappings.query.name".i18n(locale))
        append(method.obfName.buildString(nonEmptySuffix = " => "))
        append("`${method.intermediaryName}`")
        append(method.mappedName.mapIfNotNullOrNotEquals(method.intermediaryName) { " => `$it`" } ?: "")

        if (mappings == null) return@apply
        val mappedDesc = method.getMappedDesc(mappings)
        if (namespace.supportsMixin()) {
            appendLine().append("text.mappings.query.mixin".i18n(locale))
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