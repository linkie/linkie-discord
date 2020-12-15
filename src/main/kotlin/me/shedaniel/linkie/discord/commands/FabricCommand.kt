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

package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.serialization.json.*
import me.shedaniel.cursemetaapi.CurseMetaAPI
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.validateUsage
import me.shedaniel.linkie.discord.valueKeeper
import me.shedaniel.linkie.utils.tryToVersion
import java.net.URL
import java.time.Duration

object FabricCommand : CommandBase {
    private val json = Json {

    }
    private val fabricData by valueKeeper(Duration.ofMinutes(10)) { updateData() }
    private val latestVersion: String
        get() = fabricData.versions.keys.asSequence().filter {
            val version = it.tryToVersion()
            version != null && version.snapshot == null
        }.maxWithOrNull(compareBy { it.tryToVersion() })!!

    override fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateUsage(prefix, 0..1, "$cmd [version]")
        val latestVersion = this.latestVersion
        val gameVersion = if (args.isEmpty()) latestVersion else args[0]
        require(fabricData.versions.containsKey(gameVersion)) { "Invalid Version Specified: $gameVersion" }
        val version = fabricData.versions[gameVersion]!!
        message.sendEmbed {
            setTitle("Fabric Version for Minecraft $gameVersion")
            addInlineField("Type", when (version.release) {
                true -> when (version.version) {
                    latestVersion -> "Release (Latest)"
                    else -> "Release"
                }
                false -> "Unstable"
            })
            addInlineField("Loader Version", version.loaderVersion)
            addInlineField("Yarn Version", version.yarnVersion)
            if (version.apiVersion != null) {
                addInlineField("Api Version", version.apiVersion!!.version)
            }
        }.subscribe()
    }

    private fun updateData(): FabricData {
        val data = FabricData()
        val meta = json.parseToJsonElement(URL("https://meta.fabricmc.net/v2/versions").readText()).jsonObject
        val loaderVersion = meta["loader"]!!.jsonArray[0].jsonObject["version"]!!.jsonPrimitive.content
        val mappings = meta["mappings"]!!.jsonArray
        meta["game"]!!.jsonArray.asSequence().map(JsonElement::jsonObject).forEach { obj ->
            val version = obj["version"]!!.jsonPrimitive.content
            val release = version.tryToVersion().let { it != null && it.snapshot == null }
            val mappingsObj = mappings.firstOrNull { it.jsonObject["gameVersion"]!!.jsonPrimitive.content == version }?.jsonObject ?: return@forEach
            val yarnVersion = mappingsObj["version"]!!.jsonPrimitive.content
            data.versions[version] = FabricVersion(version, release, loaderVersion, yarnVersion)
        }
        fillFabricApi(data.versions)
        return data
    }

    private fun fillFabricApi(versions: MutableMap<String, FabricVersion>) {
        val displayNameRegex = "\\[(.*)].*".toRegex()
        val fileNameRegex = "fabric(?:-api)?-(.*).jar".toRegex()
        val versionSplitRegex = "([^/]+)(./.*)".toRegex()
        val addonFiles: MutableMap<String?, MutableList<CurseMetaAPI.AddonFile>> = mutableMapOf()
        CurseMetaAPI.getAddonFiles(306612).forEach {
            val displayedVersion = displayNameRegex.matchEntire(it.displayName)?.groupValues?.get(1) ?: return@forEach
            val splitMatchResult = versionSplitRegex.matchEntire(displayedVersion)
            if (splitMatchResult == null) {
                addonFiles.computeIfAbsent(displayedVersion) { mutableListOf() }.add(it)
            } else {
                val allVersions = splitMatchResult.groupValues[2].split('/').map {
                    if (it.length == 1) splitMatchResult.groupValues[1] + it else it
                }
                allVersions.forEach { version ->
                    addonFiles.computeIfAbsent(version) { mutableListOf() }.add(it)
                }
            }
        }
        versions.forEach versionLoop@{ mcVersion, fabricVersion ->
            if (fabricVersion.apiVersion != null) return@versionLoop
            val match = addonFiles[mcVersion]?.toMutableList()?.also { it.sortByDescending { it.fileId } }?.first()
            if (match != null) {
                fabricVersion.apiVersion = FabricApiVersion(
                    version = fileNameRegex.matchEntire(match.fileName)!!.groupValues[1],
                    confident = true
                )
            }
        }
    }

    data class FabricData(
        val versions: MutableMap<String, FabricVersion> = mutableMapOf(),
    )

    data class FabricVersion(
        val version: String,
        val release: Boolean,
        val loaderVersion: String,
        val yarnVersion: String,
        var apiVersion: FabricApiVersion? = null,
    )

    data class FabricApiVersion(
        val version: String,
        val confident: Boolean,
    )
}