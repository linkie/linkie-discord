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
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.sendPages
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.utils.buildSafeDescription
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setSafeDescription
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.discord.validateUsage
import me.shedaniel.linkie.discord.valueKeeper
import java.time.Duration
import kotlin.math.ceil

abstract class AbstractPlatformVersionCommand<R : PlatformVersion, T : PlatformData<R>> : CommandBase {
    private val dataKeeper = valueKeeper(Duration.ofMinutes(10)) { updateData() }
    protected val data by dataKeeper

    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateUsage(prefix, 0..1, "$cmd [version|list|first]")
        if (!dataKeeper.isInitialized()) {
            message.sendEmbed {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                buildSafeDescription {
                    append("Searching up version data.\n\nIf you are stuck with this message, please do the command again and report the issue on the issue tracker.")
                }
            }.subscribe()
        }
        if (args.isNotEmpty() && args[0] == "list") {
            val maxPage = ceil(data.versions.size / 20.0).toInt()
            message.sendPages(0, maxPage, user) { page ->
                setTitle("Available Versions (Page ${page + 1}/$maxPage)")
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
            return
        }
        val latestVersion = this.latestVersion
        val gameVersion = when {
            args.isEmpty() -> latestVersion
            args[0] == "first" -> data.versions.first()
            else -> args[0]
        }
        require(data.versions.contains(gameVersion)) { "Invalid Version Specified: $gameVersion\nYou may list the versions available by using `$prefix$cmd list`" }
        val version = data[gameVersion]
        message.sendEmbed {
            setTitle(getTitle(version.version))
            buildString {
                if (data.versions.first() != latestVersion) {
                    appendLine("Tip: You can use `$prefix$cmd list` to view the available versions, use `$prefix$cmd first` to view the first version, even if it is unstable, and use `$prefix$cmd [version]` to view the version info for that specific version.")
                } else {
                    appendLine("Tip: You can use `$prefix$cmd list` to view the available versions, and use `$prefix$cmd [version]` to view the version info for that specific version.")
                }
                when {
                    version.unstable -> addInlineField("Type", "Unstable")
                    version.version == latestVersion -> addInlineField("Type", "Release (Latest)")
                    else -> addInlineField("Type", "Release")
                }
                version.appendData()(this@sendEmbed, this)
            }.takeIf { it.isNotBlank() }?.also {
                setSafeDescription(it)
            }
        }.subscribe()
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

    fun appendData(): EmbedCreateSpec.(descriptionBuilder: StringBuilder) -> Unit
}
