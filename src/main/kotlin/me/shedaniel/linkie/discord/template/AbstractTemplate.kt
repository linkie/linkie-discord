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

package me.shedaniel.linkie.discord.template

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.utils.tryToVersion
import org.dom4j.io.SAXReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

abstract class AbstractTemplate(
    val id: String,
) {
    companion object {
        private val cache: MutableMap<String, String> = mutableMapOf()
    }

    fun generate(output: Path, templatePath: Path, tokens: Map<String, JsonElement>) {
        val toTransform = transformTokens(tokens)
        ZipOutputStream(Files.newOutputStream(output)).use { zipOutputStream ->
            val entries = mutableMapOf<String, ByteArray>()
            Files.walk(templatePath).filter { Files.isRegularFile(it) }.forEach { path ->
                val pathName = templatePath.relativize(path).toString()
                if (pathName.isTextFile) {
                    entries[pathName] = Files.readString(path).let {
                        var out = it
                        toTransform.forEach { (from, to) ->
                            out = out.replace(from, to)
                        }
                        out
                    }.encodeToByteArray()
                } else {
                    entries[pathName] = Files.readAllBytes(path)
                }
            }
            entries.forEach { (path, bytes) ->
                zipOutputStream.putNextEntry(ZipEntry(path))
                zipOutputStream.write(bytes)
            }
        }
    }

    private val String.isTextFile: Boolean
        get() = listOf(".txt", ".gradle", ".java", ".kt", ".kts", ".gradle", ".groovy", ".properties", ".json", ".mcmeta", ".cfg", ".toml", ".yaml", ".yml", ".json5").any { ext ->
            endsWith(ext)
        }

    fun transformTokens(tokens: Map<String, JsonElement>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        tokens.forEach { (token, element) ->
            val replacement = element.findReplacement()
            println("Replacing @$token@ with $replacement")
            map["@$token@"] = replacement
        }
        return map
    }

    private fun JsonElement.findReplacement(): String {
        if (this is JsonPrimitive) {
            return this.content
        } else if (this is JsonObject) {
            val url = this["pom"]!!.jsonPrimitive.content
            val filter = this["filter"]!!.jsonPrimitive.content.toRegex()
            val content = cache.getOrPut(url) { URL(url).readText() }
            return SAXReader().read(content.reader()).rootElement
                .element("versioning")
                .element("versions")
                .elementIterator("version")
                .asSequence()
                .filter { it.text.matches(filter) }
                .mapNotNull { it.text.tryToVersion() }
                .maxOrNull()?.toString() ?: throw NullPointerException("Failed to find version, with filter $filter from $url")
        }
        throw IllegalArgumentException("Unknown element type: $this")
    }
}