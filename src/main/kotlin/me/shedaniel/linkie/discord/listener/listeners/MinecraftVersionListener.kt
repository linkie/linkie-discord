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

package me.shedaniel.linkie.discord.listener.listeners

import discord4j.rest.util.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.listener.ChannelListener
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion
import java.net.URL

object MinecraftVersionListener : ChannelListener<MinecraftVersionListener.MinecraftVersionInfo> {
    private val json = Json { }
    override val serializer: KSerializer<MinecraftVersionInfo> = MinecraftVersionInfo.serializer()

    override suspend fun updateData(data: MinecraftVersionInfo?, message: MessageCreator): MinecraftVersionInfo {
        val newData = data ?: MinecraftVersionInfo()

        runBlocking {
            launch {
                val versions = json.parseToJsonElement(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").readText()).jsonObject["versions"]!!.jsonArray
                val reportVersion = mutableListOf<Pair<String, Boolean>>()
                versions.forEach { versionElement ->
                    val id = versionElement.jsonObject["id"]!!.jsonPrimitive.content
                    val type = versionElement.jsonObject["type"]!!.jsonPrimitive.content
                    val url = versionElement.jsonObject["url"]!!.jsonPrimitive.content
                    val isUnstable = type != "release" || id.tryToVersion() == null || id.toVersion().snapshot != null

                    if (newData.versions.put(id, url) != url && data != null) {
                        reportVersion.add(id to isUnstable)
                    }
                }
                reportVersion.mapNotNull { it.first.tryToVersion()?.to(it) }.maxByOrNull { it.first }?.second?.also { (version, isUnstable) ->
                    message.reply {
                        setTitle("Minecraft Update")
                        setDescription("New Minecraft ${if (isUnstable) "snapshot" else "release"} has been released: $version")

                        setColor(Color.GREEN)
                    }.subscribe()
                }
            }

            launch {
                val versions = json.parseToJsonElement(URL("https://bugs.mojang.com/rest/api/latest/project/MC/versions").readText()).jsonArray
                val reportVersion = mutableListOf<Pair<String, Pair<String, Boolean>>>()
                versions.forEach { versionElement ->
                    if (!versionElement.jsonObject["released"]!!.jsonPrimitive.boolean) return@forEach
                    val name = versionElement.jsonObject["name"]!!.jsonPrimitive.content.removePrefix("Minecraft ")
                    if (name.startsWith("Future Version")) return@forEach
                    val id = versionElement.jsonObject["id"]!!.jsonPrimitive.content
                    val isUnstable = name.tryToVersion() == null || name.toVersion().snapshot != null

                    if (newData.issueTrackerVersions.put(name, id) != id && data != null) {
                        reportVersion.add(id to (name to isUnstable))
                    }
                }
                reportVersion.mapNotNull { it.first.toIntOrNull()?.to(it) }.maxByOrNull { it.first }?.second?.second?.also { (version, isUnstable) ->
                    message.reply {
                        setTitle("Minecraft Update")
                        setDescription("New Minecraft ${if (isUnstable) "snapshot" else "release"} has been added to the issue tracker: $version")

                        setColor(Color.GREEN)
                    }.subscribe()
                }
            }
        }

        return newData
    }

    @Serializable
    data class MinecraftVersionInfo(
        val versions: MutableMap<String, String> = mutableMapOf(),
        val issueTrackerVersions: MutableMap<String, String> = mutableMapOf(),
    )
}