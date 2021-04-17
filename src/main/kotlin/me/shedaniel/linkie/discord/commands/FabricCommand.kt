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
import kotlinx.serialization.json.*
import me.shedaniel.cursemetaapi.CurseMetaAPI
import me.shedaniel.linkie.discord.utils.addField
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.utils.tryToVersion
import java.net.URL

object FabricCommand : AbstractPlatformVersionCommand<FabricCommand.FabricVersion, FabricCommand.FabricData>() {
    private val json = Json {}
    override val latestVersion: String
        get() = data.versions.asSequence().filter {
            val version = it.tryToVersion()
            version != null && version.snapshot == null
        }.maxWithOrNull(compareBy { it.tryToVersion() })!!

    override fun getTitle(version: String): String = "Fabric Version for Minecraft $version"

    override fun updateData(): FabricData {
        val data = FabricData()
        val meta = json.parseToJsonElement(URL("https://meta.fabricmc.net/v2/versions").readText()).jsonObject
        val loaderVersion = meta["loader"]!!.jsonArray[0].jsonObject["version"]!!.jsonPrimitive.content
        val installerVersion = meta["installer"]!!.jsonArray[0].jsonObject["version"]!!.jsonPrimitive.content
        val mappings = meta["mappings"]!!.jsonArray
        meta["game"]!!.jsonArray.asSequence().map(JsonElement::jsonObject).forEach { obj ->
            val version = obj["version"]!!.jsonPrimitive.content
            val release = version.tryToVersion().let { it != null && it.snapshot == null }
            val mappingsObj = mappings.firstOrNull { it.jsonObject["gameVersion"]!!.jsonPrimitive.content == version }?.jsonObject ?: return@forEach
            val yarnVersion = mappingsObj["version"]!!.jsonPrimitive.content
            data.versionsMap[version] = FabricVersion(version, release, loaderVersion, installerVersion, yarnVersion)
        }
        fillFabricApi(data.versionsMap)
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
        val versionsMap: MutableMap<String, FabricVersion> = mutableMapOf(),
    ) : PlatformData<FabricVersion> {
        override fun get(version: String): FabricVersion = versionsMap[version]!!
        override val versions: Set<String>
            get() = versionsMap.keys
    }

    data class FabricVersion(
        override val version: String,
        val release: Boolean,
        val loaderVersion: String,
        val installerVersion: String,
        val yarnVersion: String,
        var apiVersion: FabricApiVersion? = null,
    ) : PlatformVersion {
        override val unstable: Boolean
            get() = !release

        override fun appendData(): EmbedCreateSpec.(StringBuilder) -> Unit = {
            addInlineField("Loader Version", loaderVersion)
            addInlineField("Installer Version", installerVersion)
            addInlineField("Yarn Version", yarnVersion)
            val fapiDescription: String
            if (apiVersion != null) {
                addInlineField("Api Version", apiVersion!!.version)
                fapiDescription = "\n\nfabric_version=${apiVersion!!.version}"
            } else {
                fapiDescription = ""
            }
            addField("gradle.properties", """```
                |minecraft_version=$version
                |yarn_mappings=$yarnVersion
                |loader_version=$loaderVersion$fapiDescription
                |```
            """.trimMargin())
        }
    }

    data class FabricApiVersion(
        val version: String,
        val confident: Boolean,
    )
}