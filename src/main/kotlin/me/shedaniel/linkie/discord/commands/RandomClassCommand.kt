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

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.*
import me.shedaniel.linkie.discord.utils.*
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference

object RandomClassCommand : CommandBase {
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateUsage(prefix, 3, "$cmd <namespace> <version> <amount>\nDo !namespaces for list of namespaces.")
        val namespace = Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
            ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", "))
        namespace.validateNamespace()
        namespace.validateGuild(event)
        val mappingsProvider = namespace.getProvider(args[1])
        if (mappingsProvider.isEmpty()) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException(
                "Invalid Version: " + args.last() + "\nVersions: " +
                        if (list.size > 20)
                            list.take(20).joinToString(", ") + ", etc"
                        else list.joinToString(", ")
            )
        }
        val count = args[2].toIntOrNull()
        require(count in 1..20) { "Invalid Amount: ${args[2]}" }
        val version = mappingsProvider.version!!
        val mappingsContainer = ValueKeeper(Duration.ofMinutes(2)) { build(namespace.getProvider(version), user, message) }
        message.sendEmbed { buildMessage(mappingsContainer.get(), count!!, user) }.subscribe { msg ->
            msg.tryRemoveAllReactions().block()
            buildReactions(mappingsContainer.timeToKeep) {
                registerB("❌") {
                    msg.delete().subscribe()
                    false
                }
                register("🔁") {
                    message.sendEmbed { buildMessage(mappingsContainer.get(), count!!, user) }.subscribe()
                }
            }.build(msg, user)
        }
    }

    private suspend fun build(
        provider: MappingsProvider,
        user: User,
        message: MessageCreator,
    ): MappingsContainer {
        if (!provider.cached!!) message.sendEmbed {
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            var desc = "Searching up classes for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }.block()
        return provider.get()
    }

    private fun EmbedCreateSpec.buildMessage(mappingsContainer: MappingsContainer, count: Int, author: User) {
        val set = mutableSetOf<String>()
        for (i in 0 until count) randomIndex(mappingsContainer.classes, set)
        if (mappingsContainer.mappingSource == null) setFooter("Requested by ${author.discriminatedName}", author.avatarUrl)
        else setFooter("Requested by ${author.discriminatedName} • ${mappingsContainer.mappingSource}", author.avatarUrl)
        setTitle("List of Random ${mappingsContainer.name} Classes")
        var desc = ""
        set.sorted().map { mappingsContainer.classes[it]!! }.forEach { mappingsClass ->
            if (desc.isNotEmpty())
                desc += "\n"
            desc += mappingsClass.mappedName ?: mappingsClass.intermediaryName
        }
        description = desc
        setTimestampToNow()
    }

    private fun randomIndex(range: MutableMap<String, Class>, set: MutableSet<String>) {
        var random = range.keys.random()
        while (random in set) random = range.keys.random()
        set.add(random)
    }
}