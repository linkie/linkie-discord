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

package me.shedaniel.linkie.discord.tricks

import kotlinx.serialization.json.Json
import me.shedaniel.linkie.utils.error
import java.io.File
import java.util.*

object TricksManager {
    val tricks = mutableMapOf<UUID, Trick>()
    private val tricksFolder get() = File(File(System.getProperty("user.dir")), "tricks").also { it.mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load() {
        val tempTricks = mutableMapOf<UUID, Trick>()
        tricksFolder.listFiles { _, name -> name.endsWith(".json") }?.forEach { trickFile ->
            val trick = json.decodeFromString(Trick.serializer(), trickFile.readText())
            if (trick.id.toString() == trickFile.nameWithoutExtension) tempTricks[trick.id] = trick
            else {
                error("Invalid tricks file: " + trickFile.name)
            }
        }
        tricks.clear()
        tricks.putAll(tempTricks)
        save()
    }

    fun save() {
        tricks.forEach { (uuid, trick) ->
            val trickFile = File(tricksFolder, "$uuid.json")
            if (trickFile.exists().not()) {
                trickFile.writeText(json.encodeToString(Trick.serializer(), trick))
            }
        }
    }

    fun addTrick(trick: Trick) {
        require(tricks.none { it.value.name == trick.name && it.value.guildId == trick.guildId }) { "Trick with name \"${trick.name}\" already exists!" }
        tricks[trick.id] = trick
        save()
    }

    fun removeTrick(trick: Trick) {
        tricks.remove(trick.id)
        val trickFile = File(tricksFolder, "${trick.id}.json")
        trickFile.delete()
        save()
    }

    operator fun get(pair: Pair<String, Long>): Trick? = tricks.values.firstOrNull { it.name == pair.first && it.guildId == pair.second }
}