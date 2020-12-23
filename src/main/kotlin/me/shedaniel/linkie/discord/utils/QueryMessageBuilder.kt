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

package me.shedaniel.linkie.discord.utils

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsMetadata
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.utils.localiseFieldDesc
import me.shedaniel.linkie.utils.mapIntermediaryDescToNamed
import me.shedaniel.linkie.utils.mapObfDescToNamed

object QueryMessageBuilder {
    fun buildHeader(spec: EmbedCreateSpec, metadata: MappingsMetadata, page: Int, author: User, maxPage: Int) = spec.apply {
        if (metadata.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${metadata.mappingSource}", author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${metadata.name} Mappings for ${metadata.version} (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${metadata.name} Mappings for ${metadata.version}")
    }

    fun buildClass(builder: StringBuilder, namespace: Namespace, classEntry: Class, mappings: MappingsMetadata) = builder.apply {
        appendLine("**Class: __${classEntry.optimumName}__**")
        append("__Name__: ")
        append(classEntry.obfName.buildString(nonEmptySuffix = " => "))
        append("`${classEntry.intermediaryName}`")
        append(classEntry.mappedName.mapIfNotNullOrNotEquals(classEntry.intermediaryName) { " => `$it`" } ?: "")
        if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible class ${classEntry.optimumName}`")
        } else if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${classEntry.intermediaryName.replace('/', '.')}`")
        }
    }

    fun buildField(builder: StringBuilder, namespace: Namespace, field: Field, parent: Class, mappings: MappingsContainer) = builder.apply {
        appendLine("**Field: ${parent.optimumName}#__${field.optimumName}__**")
        append("__Name__: ")
        append(field.obfName.buildString(nonEmptySuffix = " => "))
        append("`${field.intermediaryName}`")
        append(field.mappedName.mapIfNotNullOrNotEquals(field.intermediaryName) { " => `$it`" } ?: "")
        if (namespace.supportsFieldDescription()) {
            appendLine().append("__Type__: ")
            append((field.mappedDesc ?: field.intermediaryDesc.mapIntermediaryDescToNamed(mappings)).localiseFieldDesc())
        }
        if (namespace.supportsMixin()) {
            appendLine().append("__Mixin Target__: `")
            append("L${parent.optimumName};")
            append(field.optimumName)
            append(':')
            append(field.mappedDesc ?: field.intermediaryDesc.mapIntermediaryDescToNamed(mappings))
            append('`')
        }
        if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${parent.optimumName.replace('/', '.')} ")
            append(field.intermediaryName)
            append(" # ")
            append(field.optimumName)
            append('`')
        } else if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible field ")
            append(parent.optimumName)
            append(' ')
            append(field.optimumName)
            append(' ')
            append(field.mappedDesc ?: field.intermediaryDesc.mapIntermediaryDescToNamed(mappings))
            append('`')
        }
    }

    fun buildMethod(builder: StringBuilder, namespace: Namespace, method: Method, parent: Class, mappings: MappingsContainer) = builder.apply {
        appendLine("**Method: ${parent.optimumName}#__${method.optimumName}__**")
        append("__Name__: ")
        append(method.obfName.buildString(nonEmptySuffix = " => "))
        append("`${method.intermediaryName}`")
        append(method.mappedName.mapIfNotNullOrNotEquals(method.intermediaryName) { " => `$it`" } ?: "")
        if (namespace.supportsMixin()) {
            appendLine().append("__Mixin Target__: `")
            append("L${parent.optimumName};")
            append(method.optimumName)
            append(method.mappedDesc ?: method.intermediaryDesc.mapIntermediaryDescToNamed(mappings))
            append('`')
        }
        if (namespace.supportsAT()) {
            appendLine().append("__AT__: `public ${parent.optimumName.replace('/', '.')} ")
            append(method.intermediaryName)
            append(method.obfDesc.merged!!.mapObfDescToNamed(mappings))
            append(" # ")
            append(method.optimumName)
            append('`')
        } else if (namespace.supportsAW()) {
            appendLine().append("__AW__: `accessible method ")
            append(parent.optimumName)
            append(' ')
            append(method.optimumName)
            append(' ')
            append(method.mappedDesc ?: method.intermediaryDesc.mapIntermediaryDescToNamed(mappings))
            append('`')
        }
    }
}