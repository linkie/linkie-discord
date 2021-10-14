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

import discord4j.core.spec.EmbedCreateSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion
import org.dom4j.io.SAXReader
import java.io.StringReader
import java.net.URL

object ArchitecturyCommand : AbstractPlatformVersionCommand<ArchitecturyCommand.ArchitecturyVersion, ArchitecturyCommand.ArchitecturyData>() {
    private val json = Json {}
    override val latestVersion: String
        get() = data.versionsMap.asSequence().filter { (it, data) ->
            val version = it.tryToVersion()
            version != null && version.snapshot == null && data.stable
        }.maxWithOrNull(compareBy { it.key.tryToVersion() })!!.key

    override fun getTitle(version: String): String = "Architectury Version for Minecraft $version"

    override fun updateData(): ArchitecturyData {
        val data = ArchitecturyData()
        val pomCache = mutableMapOf<String, String>()
        val meta = json.parseToJsonElement(URL("https://gist.githubusercontent.com/shedaniel/4a37f350a6e49545347cb798dbfa72b3/raw/architectury.json").readText()).jsonObject
        val definitions = meta["definitions"]?.jsonObject ?: buildJsonObject {}
        meta["versions"]!!.jsonObject.entries.toMutableList().reversed().forEach { (key, value) ->
            val loom = resolveVersion(value.jsonObject["loom"], definitions, pomCache)
            val plugin = resolveVersion(value.jsonObject["plugin"], definitions, pomCache)
            val transformer = resolveVersion(value.jsonObject["transformer"], definitions, pomCache)
            val injectables = resolveVersion(value.jsonObject["injectables"], definitions, pomCache)
            val api = resolveVersion(value.jsonObject["api"], definitions, pomCache)
            data.versionsMap[key] = ArchitecturyVersion(
                version = key,
                stable = value.jsonObject["stable"]?.jsonPrimitive?.boolean ?: true,
                apiVersion = api,
                pluginVersion = plugin,
                loomVersion = loom,
                transformerVersion = transformer,
                injectablesVersion = injectables
            )
        }
        return data
    }

    private fun resolveVersion(element: JsonElement?, definitions: JsonObject, pomCache: MutableMap<String, String>): String? {
        if (element == null) return null
        if (element is JsonPrimitive) {
            val content = element.content
            if (content.startsWith("@")) {
                return resolveVersion(definitions[content.substring(1)], definitions, pomCache)
            }
            return content
        }
        return element.jsonObject.let { obj ->
            val regex = (obj["filter"]?.jsonPrimitive?.content ?: ".*").toRegex()
            val pom = pomCache.computeIfAbsent(obj["pom"]!!.jsonPrimitive.content) { URL(it).readText() }
            SAXReader().read(StringReader(pom)).rootElement
                .element("versioning")
                .element("versions")
                .elementIterator("version")
                .asSequence()
                .map { it.text }
                .filter { it.tryToVersion() != null && regex.matchEntire(it) != null }
                .maxByOrNull { it.toVersion() }
        }
    }

    data class ArchitecturyData(
        val versionsMap: MutableMap<String, ArchitecturyVersion> = mutableMapOf(),
    ) : PlatformData<ArchitecturyVersion> {
        override fun get(version: String): ArchitecturyVersion = versionsMap[version]!!
        override val versions: Set<String>
            get() = versionsMap.keys
    }

    data class ArchitecturyVersion(
        override val version: String,
        val stable: Boolean,
        val pluginVersion: String?,
        val loomVersion: String?,
        val transformerVersion: String?,
        val injectablesVersion: String?,
        val apiVersion: String?,
    ) : PlatformVersion {
        override val unstable: Boolean
            get() = !stable

        override fun appendData(): EmbedCreateSpec.Builder.(StringBuilder) -> Unit = {
            if (apiVersion != null) {
                addInlineField("Api Version", apiVersion)
            }
            if (pluginVersion != null) {
                addInlineField("Plugin Version", "${pluginVersion.snapshot} ($pluginVersion)")
            }
            if (loomVersion != null) {
                addInlineField("Loom Version", "${loomVersion.snapshot} ($loomVersion)")
            }
            if (transformerVersion != null) {
                addInlineField("Transformer Version", transformerVersion)
            }
            if (injectablesVersion != null) {
                addInlineField("Injectables Version", injectablesVersion)
            }
        }
    }

    private val String.snapshot: String
        get() = substringBeforeLast('.') + "-SNAPSHOT"
}