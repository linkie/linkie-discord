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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

object PasteGGUploader {
    private val json = Json { }

    fun upload(
        content: String,
        name: String? = null,
        description: String? = null,
        visibility: PasteVisibility? = null,
    ): String {
        val con = URL("https://api.paste.gg/v1/pastes/").openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.setRequestProperty("Content-Type", "application/json")
        con.doOutput = true
        con.outputStream.use { stream ->
            val input: ByteArray = json.encodeToString(buildJsonObject {
                if (name != null) put("name", name)
                if (description != null) put("description", description)
                if (visibility != null) put("visibility", visibility.name.toLowerCase())
                putJsonArray("files") {
                    addJsonObject {
                        putJsonObject("content") {
                            put("format", "text")
                            put("value", content)
                        }
                    }
                }
            }).toByteArray()
            stream.write(input, 0, input.size)
        }
        return json.parseToJsonElement(con.inputStream.bufferedReader().readText()).let {
            require(it.jsonObject["status"]?.jsonPrimitive?.content == "success") { "Failed to upload paste!" }
            "https://paste.gg/${it.jsonObject["result"]!!.jsonObject["id"]!!.jsonPrimitive.content}"
        }
    }
}

enum class PasteVisibility {
    PUBLIC,
    UNLISTED,
    PRIVATE,
}
