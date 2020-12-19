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
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.getClassByObfName
import me.shedaniel.linkie.utils.dropAndTake
import me.shedaniel.linkie.utils.onlyClass
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

class QueryTranslateClassCommand(private val source: Namespace, private val target: Namespace) : CommandBase {
    override fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
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
        val searchTerm = args.first().replace('.', '/').onlyClass()
        val sourceVersion = sourceMappingsProvider.version!!
        val targetVersion = targetMappingsProvider.version!!
        val maxPage = AtomicInteger(-1)
        val remappedClasses = ValueKeeper(Duration.ofMinutes(2)) { build(searchTerm, source.getProvider(sourceVersion), target.getProvider(targetVersion), user, message, maxPage) }
        message.sendPages(0, maxPage.get()) { page ->
            buildMessage(remappedClasses.get(), sourceVersion, page, user, maxPage.get())
        }
    }

    private fun build(
        searchTerm: String,
        sourceProvider: MappingsProvider,
        targetProvider: MappingsProvider,
        user: User,
        message: MessageCreator,
        maxPage: AtomicInteger,
    ): MutableMap<String, String> {
        if (!sourceProvider.cached!!) message.sendEmbed {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up classes for **${sourceProvider.namespace.id} ${sourceProvider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!sourceProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        else if (!targetProvider.cached!!) message.sendEmbed {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up classes for **${targetProvider.namespace.id} ${targetProvider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!targetProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        return message.getCatching(user) {
            val sourceMappings = sourceProvider.mappingsContainer!!.invoke()
            val targetMappings = targetProvider.mappingsContainer!!.invoke()
            val remappedClasses = mutableMapOf<String, String>()
            sourceMappings.classes.asSequence().filter {
                it.intermediaryName.onlyClass().equals(searchTerm, false) ||
                        it.mappedName?.onlyClass()?.equals(searchTerm, false) == true
            }.forEach { yarnClass ->
                val obfName = yarnClass.obfName.merged!!
                val targetClass = targetMappings.getClassByObfName(obfName) ?: return@forEach
                remappedClasses[yarnClass.optimumName] = targetClass.optimumName
            }
            if (remappedClasses.isEmpty()) {
                if (!searchTerm.isValidIdentifier()) {
                    throw NullPointerException("No results found! `$searchTerm` is not a valid java identifier!")
                } else if (searchTerm.startsWith("func_") || searchTerm.startsWith("method_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a method!")
                } else if (searchTerm.startsWith("field_")) {
                    throw NullPointerException("No results found! `$searchTerm` looks like a field!")
                } else if ((!searchTerm.startsWith("class_") && searchTerm.firstOrNull()?.isLowerCase() == true) || searchTerm.firstOrNull()?.isDigit() == true) {
                    throw NullPointerException("No results found! `$searchTerm` doesn't look like a class!")
                }
                throw NullPointerException("No results found!")
            }

            maxPage.set(ceil(remappedClasses.size / 5.0).toInt())
            return@getCatching remappedClasses
        }
    }

    private fun EmbedCreateSpec.buildMessage(remappedClasses: MutableMap<String, String>, version: String, page: Int, author: User, maxPage: Int) {
        setFooter("Requested by " + author.discriminatedName, author.avatarUrl)
        setTimestampToNow()
        if (maxPage > 1) setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings (Page ${page + 1}/$maxPage)")
        else setTitle("List of ${source.id.capitalize()}->${target.id.capitalize()} Mappings")
        var desc = ""
        remappedClasses.entries.dropAndTake(5 * page, 5).forEach { (original, remapped) ->
            if (desc.isNotEmpty())
                desc += "\n"
            desc += "**MC $version: $original => `$remapped`**\n"
        }
        setSafeDescription(desc)
    }
}