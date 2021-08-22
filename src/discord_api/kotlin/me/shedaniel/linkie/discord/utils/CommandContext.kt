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

package me.shedaniel.linkie.discord.utils

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.interaction.SlashCommandEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.scommands.SlashCommand
import me.shedaniel.linkie.discord.utils.extensions.getOrNull
import reactor.core.publisher.Mono

interface CommandContext {
    val prefix: String
    val message: MessageCreator
    val interactionId: Snowflake
    val messageId: Snowflake?
    val guildId: Snowflake?
    val guild: Guild?
    val user: User
    val member: Member?
    val cmd: String
    val channel: MessageChannel
}

val CommandContext.client: GatewayDiscordClient
    get() = channel.client

val CommandContext.prefixedCmd: String
    get() = "$prefix$cmd"

inline fun CommandContext.use(spec: CommandContext.() -> Unit) =
    spec(this)

interface InGuildCommandContext : CommandContext {
    override val guildId: Snowflake
    override val guild: Guild
    override val member: Member
}

val CommandContext.inGuild: InGuildCommandContext
    get() = object : InGuildCommandContext {
        override val interactionId: Snowflake
            get() = this@inGuild.interactionId
        override val messageId: Snowflake?
            get() = this@inGuild.messageId
        override val guildId: Snowflake
            get() = this@inGuild.guildId!!
        override val user: User
            get() = this@inGuild.user
        override val prefix: String
            get() = this@inGuild.prefix
        override val message: MessageCreator
            get() = this@inGuild.message
        override val guild: Guild
            get() = this@inGuild.guild!!
        override val member: Member
            get() = this@inGuild.member!!
        override val cmd: String
            get() = this@inGuild.cmd
        override val channel: MessageChannel
            get() = this@inGuild.channel
    }

class SlashCommandBasedContext(
    val command: SlashCommand,
    override val cmd: String,
    override val message: MessageCreator,
    val event: SlashCommandEvent,
) : CommandContext {
    constructor(
        command: SlashCommand,
        cmd: String,
        event: SlashCommandEvent,
        send: (Mono<*>) -> Unit,
    ) : this(command, cmd, SlashCommandMessageCreator(event, send), event)

    override val prefix: String
        get() = "/"
    override val interactionId: Snowflake
        get() = event.interaction.id
    override val messageId: Snowflake?
        get() = null
    override val guildId: Snowflake?
        get() = event.interaction.guildId.getOrNull()
    override val guild: Guild? by lazy {
        event.interaction.guild.blockOptional().getOrNull()
    }
    override val user: User
        get() = event.user
    override val member: Member?
        get() = event.interaction.member.getOrNull()
    override val channel: MessageChannel by lazy {
        event.interaction.channel.block()
    }
}

class MessageBasedCommandContext(
    val event: MessageCreateEvent,
    override val message: MessageCreator,
    override val prefix: String,
    override val cmd: String,
    override val channel: MessageChannel,
) : CommandContext {
    override val interactionId: Snowflake
        get() = messageId
    override val messageId: Snowflake
        get() = event.message.id
    override val guildId: Snowflake?
        get() = event.guildId.getOrNull()
    override val guild: Guild? by lazy {
        event.guild.blockOptional().getOrNull()
    }
    override val user: User
        get() = event.message.author.get()
    override val member: Member?
        get() = event.member.getOrNull()
}
