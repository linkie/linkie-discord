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
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.*
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

class QueryTranslateFieldCommand(private val source: Namespace, private val target: Namespace) : CommandBase {
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        source.validateNamespace()
        source.validateGuild(event)
        target.validateNamespace()
        target.validateGuild(event)
        args.validateUsage(prefix, 1..2, "$cmd <search> [version]")
        val sourceMappingsProvider = if (args.size == 1) MappingsProvider.empty(source) else source.getProvider(args.last())
        val allVersions = source.getAllSortedVersions().toMutableList()
        allVersions.retainAll(target.getAllSortedVersions())
        if (sourceMappingsProvider.isEmpty() && args.size == 2) {
            throw NullPointerException(
                "Invalid Version: " + args.last() + "\nVersions: " +
                        if (allVersions.size > 20)
                            allVersions.take(20).joinToString(", ") + ", etc"
                        else allVersions.joinToString(", ")
            )
        }
        sourceMappingsProvider.injectDefaultVersion(source.getProvider(allVersions.first()))
        sourceMappingsProvider.validateDefaultVersionNotEmpty()
        val targetMappingsProvider = target.getProvider(sourceMappingsProvider.version!!)
        if (targetMappingsProvider.isEmpty()) {
            throw NullPointerException(
                "Invalid Version: " + args.last() + "\nVersions: " +
                        if (allVersions.size > 20)
                            allVersions.take(20).joinToString(", ") + ", etc"
                        else allVersions.joinToString(", ")
            )
        }
        require(!args.first().replace('.', '/').contains('/')) { "Query with classes are not available with translating queries." }
        val searchTerm = args.first().replace('.', '/').onlyClass()
        val sourceVersion = sourceMappingsProvider.version!!
        val targetVersion = targetMappingsProvider.version!!
        val maxPage = AtomicInteger(-1)
        val remappedFields = ValueKeeper(Duration.ofMinutes(2)) { build(searchTerm, source.getProvider(sourceVersion), target.getProvider(targetVersion), user, message, maxPage) }
        message.sendPages(0, maxPage.get()) { page ->
            buildMessage(remappedFields.get(), sourceVersion, page, user, maxPage.get())
        }
    }

    private suspend fun build(
        searchTerm: String,
        sourceProvider: MappingsProvider,
        targetProvider: MappingsProvider,
        user: User,
        message: MessageCreator,
        maxPage: AtomicInteger,
    ): MutableMap<FieldCompound, String> {
        if (!sourceProvider.cached!!) message.sendEmbed {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up fields for **${sourceProvider.namespace.id} ${sourceProvider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!sourceProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        else if (!targetProvider.cached!!) message.sendEmbed {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up fields for **${targetProvider.namespace.id} ${targetProvider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!targetProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        return message.getCatching(user) {
            val sourceMappings = sourceProvider.get()
            val targetMappings = targetProvider.get()
            val remappedFields = mutableMapOf<FieldCompound, String>()
            sourceMappings.classes.values.forEach { sourceClassParent ->
                sourceClassParent.fields.forEach inner@{ sourceField ->
                    if (sourceField.intermediaryName.onlyClass().equals(searchTerm, true) || sourceField.mappedName?.onlyClass()?.equals(searchTerm, true) == true) {
                        val obfName = sourceField.obfName.merged!!
                        val parentObfName = sourceClassParent.obfName.merged!!
                        val targetClass = targetMappings.getClassByObfName(parentObfName) ?: return@inner
                        val targetField = targetClass.fields.firstOrNull { it.obfName.merged == obfName } ?: return@inner
                        remappedFields[FieldCompound(
                            sourceClassParent.optimumName.onlyClass() + "#" + sourceField.optimumName,
                            sourceField.getObfMergedDesc(sourceMappings)
                        )] =
                            targetClass.optimumName.onlyClass() + "#" + targetField.optimumName
                    }
                }
            }
            if (remappedFields.isEmpty()) {
                if (!searchTerm.isValidIdentifier()) {
                    throw NullPointerException("No results found! `$searchTerm` is not a valid java identifier!")
                } else if (searchTerm.startsWith("method_") || searchTerm.startsWith("func_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a method!")
                } else if (searchTerm.startsWith("class_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a class!")
                }
                throw NullPointerException("No results found!")
            }

            maxPage.set(ceil(remappedFields.size / 5.0).toInt())
            return@getCatching remappedFields
        }
    }
    
    private data class FieldCompound(
        val optimumName: String,
        val obfDesc: String
    ) 

    private fun EmbedCreateSpec.buildMessage(remappedFields: MutableMap<FieldCompound, String>, version: String, page: Int, author: User, maxPage: Int) {
        setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings")
        var desc = ""
        remappedFields.entries.dropAndTake(5 * page, 5).forEach { (original, remapped) ->
            if (desc.isNotEmpty())
                desc += "\n"
            desc += "**MC $version: ${original.optimumName} => `$remapped`**\n"
        }
        setSafeDescription(desc)
    }
}