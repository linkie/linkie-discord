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

package me.shedaniel.linkie.discord.commands.legacy

import discord4j.core.`object`.entity.Message
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.LegacyCommand
import me.shedaniel.linkie.discord.scommands.ArgReader
import me.shedaniel.linkie.discord.scommands.OptionsGetterBuilder
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilder
import me.shedaniel.linkie.discord.scommands.SubCommandOption
import me.shedaniel.linkie.discord.scommands.opt
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.PasteGGUploader
import me.shedaniel.linkie.discord.utils.VersionNamespaceConfig
import me.shedaniel.linkie.discord.utils.attachmentMessage
import me.shedaniel.linkie.discord.utils.property
import me.shedaniel.linkie.discord.utils.version
import java.net.URL

class RemapStackTraceCommand(private val namespace: Namespace) : LegacyCommand {
    override suspend fun execute(ctx: CommandContext, trigger: Message, args: MutableList<String>) {
        var content = URL(trigger.attachmentMessage.url).readText()
        val root = SubCommandOption("", "", listOf())
        val versionOption = root.version("version", "")
        val command = SlashCommandBuilder("")
        val optionsGetter = OptionsGetterBuilder(command, ctx)
        root.execute(ctx, command, ArgReader(args).property, optionsGetter)
        val version = optionsGetter.opt(versionOption, VersionNamespaceConfig(namespace))
        content = remap(content, version.get())
        ctx.message.reply {
            title("Remapped Access")
            url(PasteGGUploader.upload(content))
        }
    }

    private fun remap(_content: String, mappings: MappingsContainer): String {
        var content = _content
        mappings.allClasses.forEach {
            if (it.mappedName != null) {
                content = content.replace(it.intermediaryName, it.mappedName!!)
            }
            for (member in it.members) {
                if (member.mappedName != null) {
                    content = content.replace(member.intermediaryName, member.mappedName!!)
                }
            }
        }
        return content
    }
}