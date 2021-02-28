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

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.utils.buildSafeDescription
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.discord.validateUsage
import me.shedaniel.linkie.getClassByObfName
import me.shedaniel.linkie.getMappedDesc
import me.shedaniel.linkie.getObfMergedDesc
import me.shedaniel.linkie.obfMergedName
import me.shedaniel.linkie.optimumName
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.*

object RemapAWATCommand : CommandBase {
    private val json = Json { }
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateUsage(prefix, 2, "$cmd <source mappings> <target mappings>")
        val sourceMappings = readMappings(message, user, args[0])
        val source = Namespaces[sourceMappings.namespace]
        val targetMappings = readMappings(message, user, args[1])
        val target = Namespaces[targetMappings.namespace]
        val awToAt = when {
            source.supportsAT() && target.supportsAW() -> false
            source.supportsAW() && target.supportsAT() -> true
            else -> throw IllegalArgumentException("Illegal operation, mapping from ${source.id} to ${target.id}!")
        }
        require(event.message.attachments.size == 1) { "You must send 1 file!" }
        val content = URL(event.message.attachments.first().url).readText()
        var members = if (awToAt) readAW(sourceMappings, content) else readAT(sourceMappings, content)
        members = remapMembers(members, sourceMappings, targetMappings)
        upload(message, members, if (awToAt) writeAT(targetMappings, members) else writeAW(targetMappings, members))
    }

    private fun URL.readText(): String {
        val connection: URLConnection = openConnection()
        connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.11 Safari/537.36")
        connection.connect()
        val stream: InputStream = connection.getInputStream()
        return stream.use { it.readBytes().decodeToString() }
    }

    private fun upload(message: MessageCreator, members: Format, content: String) {
        val con = URL("https://api.paste.gg/v1/pastes/").openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.setRequestProperty("Content-Type", "application/json")
        con.doOutput = true
        con.outputStream.use { stream ->
            val input: ByteArray = json.encodeToString(buildJsonObject {
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
        json.parseToJsonElement(con.inputStream.bufferedReader().readText()).apply {
            require(jsonObject["status"]?.jsonPrimitive?.content == "success") { "Failed to upload paste!" }
            message.sendEmbed {
                setTitle("Remapped Access")
                setUrl("https://paste.gg/${jsonObject["result"]!!.jsonObject["id"]!!.jsonPrimitive.content}")
                setDescription("""Remapped ${members.members.count { it.memberType == MemberType.CLASS }} classes,
                    |${members.members.count { it.memberType == MemberType.METHOD }} methods,
                    |and ${members.members.count { it.memberType == MemberType.FIELD }} fields.
                """.trimMargin())
            }.subscribe()
        }
    }

    private fun remapMembers(members: Format, source: MappingsContainer, target: MappingsContainer): Format {
        return members.copy(members = members.members.asSequence().map {
            val sourceOwner = source.getClass(it.owner)!!
            val targetOwner = target.getClassByObfName(sourceOwner.obfMergedName!!)!!
            val copy = it.copy(owner = targetOwner.intermediaryName)
            if (it.memberType == MemberType.CLASS) {
                copy
            } else {
                val sourceMember = sourceOwner.members.first { member -> member.intermediaryName == it.member && member.intermediaryDesc == it.descriptor }
                val targetMember = targetOwner.members.firstOrNull { member -> member.obfMergedName == sourceMember.obfMergedName && (member.intermediaryDesc.isBlank() || member.getObfMergedDesc(target) == sourceMember.getObfMergedDesc(source)) }
                    ?: throw IllegalStateException("Failed to map $sourceMember to ${target.namespace}")
                copy.copy(
                    member = targetMember.intermediaryName,
                    descriptor = targetMember.intermediaryDesc
                )
            }
        }.toMutableSet())
    }

    private fun readAW(mappings: MappingsContainer, content: String): Format {
        val format = Format()
        val whitespace = "\\s+".toRegex()
        content.lineSequence().filter(String::isNotBlank).forEachIndexed { index, line ->
            try {
                val split = line.split(whitespace)
                if (index == 0) {
                    require(split[0] == "accessWidener" && split[1] == "v1") { "Invalid Header: Expected `accessWidener v1" }
                } else {
                    val formatType = when (split[0]) {
                        "accessible" -> FormatType.MAKE_PUBLIC
                        "extendable" -> FormatType.MAKE_EXTENDABLE
                        "mutable" -> FormatType.MAKE_NON_FINAL
                        else -> throw IllegalArgumentException("Invalid mappings type: ${split[0]}")
                    }
                    val memberType = MemberType.valueOf(split[1].toUpperCase(Locale.ROOT))
                    val owner = mappings.getClass(split[2]) ?: mappings.classes.values.firstOrNull { it.mappedName == split[2] } ?: throw IllegalArgumentException("Invalid owner class: ${split[2]}")
                    if (memberType == MemberType.CLASS) {
                        format.members += FormatMember(
                            memberType = memberType,
                            type = formatType,
                            owner = owner.intermediaryName,
                        )
                    } else {
                        val member = (if (memberType == MemberType.FIELD) owner.fields else owner.methods)
                            .firstOrNull { it.optimumName == split[3] && it.getMappedDesc(mappings) == split[4] }
                            ?: throw IllegalArgumentException("Invalid member: ${split[3]}${split[4]}")
                        format.members += FormatMember(
                            memberType = memberType,
                            type = formatType,
                            owner = owner.intermediaryName,
                            member = member.intermediaryName,
                            descriptor = member.intermediaryDesc,
                        )
                    }
                }
            } catch (throwable: Throwable) {
                throw IllegalStateException("Failed to parse mappings line ${index + 1}: $line\n${throwable.message}", throwable)
            }
        }
        return format
    }

    private fun readAT(mappings: MappingsContainer, content: String): Format {
        TODO("AT Reading has not been implemented")
    }

    private fun writeAT(mappings: MappingsContainer, format: Format): String = buildString {
        format.members.groupBy { "${it.memberType}:${it.owner}:${it.member}:${it.descriptor}" }.forEach { _, members ->
            val member = members.first()
            val types = members.asSequence().map { it.type }.distinct()
            if (FormatType.MAKE_PUBLIC in types) {
                append("public")
                if (FormatType.MAKE_NON_FINAL in types) append("-f")
            } else if (FormatType.MAKE_EXTENDABLE in types) {
                append("protected")
                if (FormatType.MAKE_NON_FINAL in types) append("-f")
            } else if (FormatType.MAKE_NON_FINAL in types) {
                append("public-f")
            }
            append(' ')
            append(member.owner.replace('/', '.'))
            if (member.memberType != MemberType.CLASS) {
                append(' ')
                append(member.member)
                if (member.memberType == MemberType.METHOD) {
                    append(member.descriptor)
                }
            }
            appendLine()
        }
    }

    private fun writeAW(mappings: MappingsContainer, format: Format): String {
        TODO("AW Writing has not been implemented")
    }

    private suspend fun readMappings(message: MessageCreator, user: User, id: String): MappingsContainer {
        if (id.indexOf(':') == -1) {
            throw IllegalArgumentException("Mappings format should be in <namespace>:<version>")
        }
        val namespace = Namespaces[id.substringBefore(':')]
        val version = id.substringAfter(':')
        val provider = namespace.getProvider(version)
        require(!provider.isEmpty()) {
            val list = namespace.getAllSortedVersions()
            "Invalid Version: $version\nVersions: " +
                    if (list.size > 20)
                        list.take(20).joinToString(", ") + ", etc"
                    else list.joinToString(", ")
        }
        if (!provider.cached!!) message.sendEmbed {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            buildSafeDescription {
                append("Searching up mappings for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again.")
                if (!provider.cached!!) append("\nThis mappings version is not yet cached, might take some time to download.")
            }
        }.block()
        return provider.get()
    }

    private data class Format(
        val members: MutableSet<FormatMember> = mutableSetOf(),
    )

    private data class FormatMember(
        val memberType: MemberType,
        val type: FormatType,
        val owner: String, // intermediary
        val member: String? = null, // intermediary
        val descriptor: String? = null, // intermediary
    )

    private enum class MemberType {
        CLASS,
        FIELD,
        METHOD,
    }

    private enum class FormatType {
        MAKE_PUBLIC,
        MAKE_EXTENDABLE,
        MAKE_NON_FINAL,
    }
}