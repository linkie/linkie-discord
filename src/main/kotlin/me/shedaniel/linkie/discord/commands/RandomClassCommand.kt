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
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.Command
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.int
import me.shedaniel.linkie.discord.scommands.opt
import me.shedaniel.linkie.discord.scommands.optNullable
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.MessageCreator
import me.shedaniel.linkie.discord.utils.VersionNamespaceConfig
import me.shedaniel.linkie.discord.utils.acknowledge
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.discordEmote
import me.shedaniel.linkie.discord.utils.dismissButton
import me.shedaniel.linkie.discord.utils.embedCreator
import me.shedaniel.linkie.discord.utils.initiate
import me.shedaniel.linkie.discord.utils.namespace
import me.shedaniel.linkie.discord.utils.reply
import me.shedaniel.linkie.discord.utils.replyComplex
import me.shedaniel.linkie.discord.utils.secondaryButton
import me.shedaniel.linkie.discord.utils.suggestVersionsWithNs
import me.shedaniel.linkie.discord.utils.use
import me.shedaniel.linkie.discord.utils.validateGuild
import me.shedaniel.linkie.discord.utils.validateNamespace
import me.shedaniel.linkie.discord.utils.version
import me.shedaniel.linkie.utils.valueKeeper

object RandomClassCommand : Command {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        val namespace = namespace("namespace", "The namespace to query in")
        val version = version("version", "The version to query for", required = false) {
            suggestVersionsWithNs {
                it.optNullable(it.cmd, namespace)
            }
        }
        val count = int("count", "The number of classes to generate", required = false)
        executeCommandWithGetter { ctx, options ->
            val ns = options.opt(namespace)
            ns.validateNamespace()
            ns.validateGuild(ctx)
            val nsVersion = options.opt(version, VersionNamespaceConfig(ns))
            execute(ctx, ns, nsVersion, options.optNullable(count)?.toInt() ?: 10)
        }
    }

    suspend fun execute(ctx: CommandContext, namespace: Namespace, provider: MappingsProvider, count: Int) {
        ctx.use {
            namespace.validateNamespace()
            namespace.validateGuild(ctx)
            require(count in 1..20) { "Invalid Amount: $count" }
            val version = provider.version!!
            val mappingsContainer by valueKeeper { build(namespace.getProvider(version), user, message) }.initiate()
            val embedCreator = embedCreator { buildMessage(mappingsContainer, count, user) }
            message.replyComplex {
                layout {
                    row {
                        dismissButton()
                        secondaryButton("🔁".discordEmote) {
                            reply(embedCreator)
                        }
                    }
                }
                embed(embedCreator)
            }
        }
    }

    private suspend fun build(
        provider: MappingsProvider,
        user: User,
        message: MessageCreator,
    ): MappingsContainer {
        if (!provider.cached!!) message.acknowledge {
            basicEmbed(user)
            var desc = "Searching up classes for **${provider.namespace.id} ${provider.version}**.\nIf you are stuck with this message, please do the command again."
            if (!provider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
            description = desc
        }
        return provider.get()
    }

    private fun EmbedCreateSpec.Builder.buildMessage(mappingsContainer: MappingsContainer, count: Int, author: User) {
        val set = mutableSetOf<String>()
        for (i in 0 until count) randomIndex(mappingsContainer.classes, set)
        title("List of Random ${mappingsContainer.name} Classes")
        var desc = ""
        set.sorted().map { mappingsContainer.classes[it]!! }.forEach { mappingsClass ->
            if (desc.isNotEmpty())
                desc += "\n"
            desc += mappingsClass.mappedName ?: mappingsClass.intermediaryName
        }
        description = desc
        basicEmbed(author, mappingsContainer.mappingsSource?.toString())
    }

    private fun randomIndex(range: MutableMap<String, Class>, set: MutableSet<String>) {
        var random = range.keys.random()
        while (random in set) random = range.keys.random()
        set.add(random)
    }
}