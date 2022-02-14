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

package me.shedaniel.linkie.discord.tricks

import discord4j.common.util.Snowflake
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.shedaniel.linkie.discord.asSlashCommand
import me.shedaniel.linkie.discord.scommands.SlashCommands
import me.shedaniel.linkie.utils.ZipFile
import me.shedaniel.linkie.utils.error
import me.shedaniel.linkie.utils.info
import java.io.File
import java.util.*

object TricksManager {
    val globalTricks = mutableMapOf<String, GlobalTrick>()
    val tricks = mutableMapOf<UUID, Trick>()
    var slashCommands: SlashCommands? = null

    private val tricksFolder get() = File(File(System.getProperty("user.dir")), "tricks").also { it.mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun get(trickName: String, guildId: Snowflake?): TrickBase {
        if (guildId != null) {
            val trick = get(trickName to guildId.asLong())
            if (trick != null) return trick
        }
        return globalTricks[trickName.toLowerCase()] ?: throw NullPointerException("Cannot find trick named `$trickName`")
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
        val tempGlobalTricks = mutableMapOf<String, GlobalTrick>()
        runBlocking {
            readGlobalTrick {
                tempGlobalTricks[it.name] = it
            }
        }
        info("Loaded ${tempGlobalTricks.size} global tricks")
        tricks.clear()
        tricks.putAll(tempTricks)
        globalTricks.clear()
        globalTricks.putAll(tempGlobalTricks)
        save()
        checkCommands(listOf())
    }

    fun checkCommands(extraChecks: List<Trick>) {
        val slashCommands = this.slashCommands ?: return
        val tricks = (tricks.values.asSequence() + extraChecks.asSequence()).toMutableList()
        TricksManager.tricks.values.forEach { trick ->
            runCatching {
                slashCommands.guildCommand(trick.guildId, TrickBasedCommand(trick)
                    .asSlashCommand("Run trick ${trick.name}", listOf(trick.name)))
            }.exceptionOrNull()?.printStackTrace()
        }
        val availableTricks = TricksManager.tricks.values.map { it.name }
        tricks.groupBy { it.guildId }.mapValues { it.value.map { it.name }.toSet() }.forEach { (guildId, tricks) ->
            ArrayList(slashCommands.getGuildCommands(Snowflake.of(guildId)) + slashCommands.registeredGuildCommands.filterKeys { it.guildId.asLong() == guildId }.values).distinctBy { it.name() }.forEach { commands ->
                if (commands.description().startsWith("Run trick ") && commands.name() !in availableTricks) {
                    slashCommands.removeGuildCommand(Snowflake.of(guildId), commands.name())
                }
            }
        }
    }

    private suspend fun readGlobalTrick(function: (trick: GlobalTrick) -> Unit) {
        val stream = javaClass.getResourceAsStream("/global-tricks.zip")
        if (stream != null) {
            ZipFile(stream.readBytes()).forEachEntry { path, entry ->
                if (path.endsWith(".js")) {
                    function(GlobalTrick(
                        path.substringAfterLast('/').substringBeforeLast(".js"),
                        entry.bytes.decodeToString()
                    ))
                }
            }
        } else {
            File(System.getProperty("user.dir"), "tricks").walkTopDown().forEach { file ->
                if (file.extension == "js") {
                    function(GlobalTrick(
                        file.nameWithoutExtension,
                        file.readText()
                    ))
                }
            }
        }
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
        checkCommands(listOf())
    }

    fun removeTrick(trick: Trick) {
        tricks.remove(trick.id)
        val trickFile = File(tricksFolder, "${trick.id}.json")
        trickFile.delete()
        save()
        checkCommands(listOf(trick))
    }

    operator fun get(pair: Pair<String, Long>): Trick? = tricks.values.firstOrNull { it.name == pair.first && it.guildId == pair.second }

    fun listen(slashCommands: SlashCommands) {
        this.slashCommands = slashCommands
        checkCommands(listOf())
    }
}