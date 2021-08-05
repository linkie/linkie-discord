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
import me.shedaniel.linkie.discord.SimpleCommand
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.optNullable
import me.shedaniel.linkie.discord.scommands.string
import me.shedaniel.linkie.discord.scommands.sub
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.buildSafeDescription
import me.shedaniel.linkie.discord.utils.prefixedCmd
import me.shedaniel.linkie.discord.utils.sendPages
import me.shedaniel.linkie.discord.utils.setSafeDescription
import me.shedaniel.linkie.discord.utils.use
import me.shedaniel.linkie.discord.valueKeeper
import java.time.Duration
import kotlin.math.ceil

abstract class AbstractPlatformVersionCommand<R : PlatformVersion, T : PlatformData<R>> : SimpleCommand<String> {
    private val dataKeeper = valueKeeper(Duration.ofMinutes(10)) { updateData() }
    protected val data by dataKeeper

    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        sub("list", "Lists the available versions") {
            executeCommandWithNothing(this@AbstractPlatformVersionCommand::executeList)
        }
        sub("first", "Returns the data for the first version") {
            executeCommandWith { data.versions.first() }
        }
        sub("latest", "Returns the data for the latest version") {
            executeCommandWith { latestVersion }
        }
        if (slash) {
            sub("get", "Returns the data for a selected version") {
                val version = string("version", "The version to return for", required = false) {}
                executeCommandWith {
                    optNullable(version) ?: latestVersion
                }
            }
        } else {
            val version = string("version", "The version to return for", required = false) {}
            executeCommandWith {
                optNullable(version) ?: latestVersion
            }
        }
    }

    fun executeList(ctx: CommandContext) {
        ctx.use {
            sendNotInitializedYet()
            val maxPage = ceil(data.versions.size / 20.0).toInt()
            message.sendPages(ctx, 0, maxPage) { page ->
                title("Available Versions (Page ${page + 1}/$maxPage)")
                buildSafeDescription {
                    data.versions.asSequence().drop(page * 20).take(20).forEach { versionString ->
                        val version = data[versionString]
                        appendLine("• $versionString" + when {
                            version.unstable -> " (Unstable)"
                            version.version == latestVersion -> " **(Latest)**"
                            else -> ""
                        })
                    }
                }
            }
        }
    }

    override suspend fun execute(ctx: CommandContext, options: String) {
        ctx.use {
            sendNotInitializedYet()
            require(data.versions.contains(options)) { "Invalid Version Specified: $options\nYou may list the versions available by using `$prefixedCmd list`" }
            val version = data[options]
            message.reply {
                title(getTitle(version.version))
                buildString {
                    if (data.versions.first() != latestVersion) {
                        appendLine("Tip: You can use `$prefixedCmd list` to view the available versions, use `$prefixedCmd first` to view the first version, even if it is unstable, and use `$prefixedCmd [version]` to view the version info for that specific version.")
                    } else {
                        appendLine("Tip: You can use `$prefixedCmd list` to view the available versions, and use `$prefixedCmd [version]` to view the version info for that specific version.")
                    }
                    when {
                        version.unstable -> addInlineField("Type", "Unstable")
                        version.version == latestVersion -> addInlineField("Type", "Release (Latest)")
                        else -> addInlineField("Type", "Release")
                    }
                    version.appendData()(this@reply, this)
                }.takeIf { it.isNotBlank() }?.also {
                    setSafeDescription(it)
                }
            }
        }
    }

    private fun CommandContext.sendNotInitializedYet() {
        if (!dataKeeper.isInitialized()) {
            message.acknowledge {
                basicEmbed(user)
                buildSafeDescription {
                    append("Searching up version data.\n\nIf you are stuck with this message, please do the command again and report the issue on the issue tracker.")
                }
            }
        }
    }

    abstract val latestVersion: String
    abstract fun getTitle(version: String): String
    abstract fun updateData(): T
}

interface PlatformData<R : PlatformVersion> {
    val versions: Set<String>

    operator fun get(version: String): R
}

interface PlatformVersion {
    val version: String
    val unstable: Boolean

    fun appendData(): EmbedCreateSpec.Builder.(descriptionBuilder: StringBuilder) -> Unit
}
