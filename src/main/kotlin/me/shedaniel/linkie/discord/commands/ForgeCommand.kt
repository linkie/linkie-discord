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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion
import org.dom4j.io.SAXReader
import java.net.URL
import java.util.*

object ForgeCommand : AbstractPlatformVersionCommand<ForgeCommand.ForgeVersion, ForgeCommand.ForgeData>() {
    private val json = Json {}
    override fun getTitle(version: String): String = "Forge Version for Minecraft $version"

    override val latestVersion: String
        get() = data.versions.first()

    override fun updateData(): ForgeData {
        val data = ForgeData()
        SAXReader().read(URL("https://files.minecraftforge.net/maven/net/minecraftforge/forge/maven-metadata.xml")).rootElement
            .element("versioning")
            .element("versions")
            .elementIterator("version")
            .asSequence()
            .map { it.text }
            .forEach {
                val mcVersion = it.substringBefore('-')
                val mcVersionSemVer = mcVersion.tryToVersion() ?: return@forEach
                val forgeVersion = it.substring(mcVersion.length + 1).substringBeforeLast('-')
                data.versionsMap.getOrPut(mcVersion) { ForgeVersion(mcVersion) }.also { version ->
                    version.forgeVersion.add(forgeVersion)
                }
            }
        json.parseToJsonElement(URL("http://export.mcpbot.bspk.rs/versions.json").readText()).jsonObject.forEach { mcVersion, mcpVersionsObj ->
            mcpVersionsObj.jsonObject["snapshot"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.maxByOrNull { it.toInt() }
                ?.also { snapshotVersion ->
                    data.versionsMap[mcVersion]?.also {
                        it.mcpSnapshot = "$snapshotVersion-$mcVersion"
                    }
                }
        }
        json.parseToJsonElement(URL("https://gist.githubusercontent.com/shedaniel/afc2748c6d5dd827d4cde161a49687ec/raw/mcp_versions.json").readText()).jsonObject.forEach { mcVersion, versionObj ->
            if (versionObj.jsonObject["mcp"]?.jsonPrimitive?.content?.contains(mcVersion) == true) {
                versionObj.jsonObject["name"]?.jsonPrimitive?.content?.substringAfterLast('-')
                    ?.also { snapshotVersion ->
                        data.versionsMap[mcVersion]?.also {
                            it.mcpSnapshot = "$snapshotVersion-$mcVersion"
                            it.tmp = true
                        }
                    }
            }
        }
        return data
    }

    data class ForgeData(
        val versionsMap: SortedMap<String, ForgeVersion> = TreeMap(compareByDescending { it.toVersion() }),
    ) : PlatformData<ForgeVersion> {
        override val versions: Set<String>
            get() = versionsMap.keys

        override fun get(version: String): ForgeVersion = versionsMap[version]!!
    }

    data class ForgeVersion(
        override val version: String,
        var forgeVersion: MutableList<String> = mutableListOf(),
        var mcpSnapshot: String? = null,
        var tmp: Boolean = false,
    ) : PlatformVersion {
        override val unstable: Boolean
            get() = false

        override fun appendData(): EmbedCreateSpec.Builder.(StringBuilder) -> Unit = {
            addInlineField("Forge Version", forgeVersion.sortedWith(nullsFirst(compareBy { it.tryToVersion() })).toList().asReversed().maxOrNull().toString())
            if (mcpSnapshot != null) {
                addInlineField("MCP Version", mcpSnapshot!!)
                if (tmp) {
                    it.insert(0, "1.16+ MCP versions are manually managed, as 1.16+ MCP versions are not handled by MCP bot.\n" +
                            "If the following data is outdated, please report it on our issue tracker!\n\n")
                }
            } else {
                val versions = data.versions.toMutableList()
                val ourIndex = versions.indexOf(version)
                versions.asSequence().drop(ourIndex + 1).map { data[it] }.firstOrNull { it.mcpSnapshot != null }?.also { usableVersion ->
                    addInlineField("MCP Version", usableVersion.mcpSnapshot!!)
                    if (usableVersion.tmp) {
                        it.insert(0, "1.16+ MCP versions are manually managed, as 1.16+ MCP versions are not handled by MCP bot.\n" +
                                "If the following data is outdated, please report it on our issue tracker!\n\n")
                    }
                }
            }
        }
    }
}
