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

package me.shedaniel.linkie.discord

import com.soywiz.korio.async.runBlockingNoJs
import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.utils.buildReactions
import me.shedaniel.linkie.discord.utils.content
import me.shedaniel.linkie.discord.utils.getOrNull
import me.shedaniel.linkie.discord.utils.sendEdit
import me.shedaniel.linkie.discord.utils.sendEditEmbed
import me.shedaniel.linkie.discord.utils.sendEmbedMessage
import me.shedaniel.linkie.discord.utils.sendMessage
import me.shedaniel.linkie.discord.utils.tryRemoveAllReactions
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

typealias EmbedCreator = suspend EmbedCreateSpec.() -> Unit

fun embedCreator(creator: EmbedCreator) = creator

fun MessageCreator.sendPages(
    initialPage: Int,
    maxPages: Int,
    creator: suspend EmbedCreateSpec.(page: Int) -> Unit,
) = sendPages(initialPage, maxPages, executorId, creator)

fun MessageCreator.sendPages(
    initialPage: Int,
    maxPages: Int,
    user: User?,
    creator: suspend EmbedCreateSpec.(page: Int) -> Unit,
) = sendPages(initialPage, maxPages, user?.id, creator)

fun MessageCreator.sendPages(
    initialPage: Int,
    maxPages: Int,
    userId: Snowflake?,
    creator: suspend EmbedCreateSpec.(page: Int) -> Unit,
) {
    var page = initialPage
    val builder = embedCreator { creator(this, page) }
    reply(builder).subscribe { msg ->
        msg.tryRemoveAllReactions().block()
        buildReactions(Duration.ofMinutes(2)) {
            if (maxPages > 1) register("⬅") {
                if (page > 0) {
                    page--
                    reply(builder).subscribe()
                }
            }
            registerB("❌") {
                msg.delete().subscribe()
                executorMessage?.delete()?.subscribe()
                false
            }
            if (maxPages > 1) register("➡") {
                if (page < maxPages - 1) {
                    page++
                    reply(builder).subscribe()
                }
            }
        }.build(msg) { it == userId }
    }
}

interface CommandBase {
    suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel)

    fun postRegister() {}
}

fun MessageChannel.deferMessage(previous: Message) = MessageCreatorImpl(
    this,
    previous,
    null
)

interface MessageCreator {
    val executorMessage: Message?
    val executorId: Snowflake?
    
    fun reply(content: String): Mono<Message>
    fun reply(content: EmbedCreator): Mono<Message>
}

data class MessageCreatorImpl(
    val channel: MessageChannel,
    override var executorMessage: Message,
    var message: Message?,
) : MessageCreator {
    override val executorId: Snowflake?
        get() = executorMessage.author.getOrNull()?.id

    override fun reply(content: String): Mono<Message> {
        return if (message == null) {
            channel.sendMessage {
                it.content = content
                it.setMessageReference(executorMessage.id)
            }
        } else {
            message!!.sendEdit {
                it.content = content
            }
        }.doOnSuccess { message = it }
    }

    override fun reply(content: EmbedCreator): Mono<Message> {
        return if (message == null) {
            channel.sendEmbedMessage(executorMessage) { runBlockingNoJs { content() } }
        } else {
            message!!.sendEditEmbed { runBlockingNoJs { content() } }
        }.doOnSuccess { message = it }
    }
}

open class SubCommandHolder : CommandBase {
    private val subcommands = mutableMapOf<String, SubCommandBase>()
    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateNotEmpty(prefix, "$cmd help")

        when (val subcommand = args[0].toLowerCase(Locale.ROOT)) {
            "help" -> {
                channel.sendEmbedMessage(event.message) {
                    setTitle("Help for $cmd")
                    subcommands.forEach { (key, subcommand) ->
                        addInlineField(subcommand.name, "Command: $prefix$cmd $key [...]")
                    }
                }.subscribe()
            }
            in subcommands -> {
                subcommands[subcommand]!!.execute(event, message, prefix, user, "$cmd $subcommand", args.drop(1).toMutableList(), channel)
            }
            else -> {
                throw InvalidUsageException("$prefix help")
            }
        }
    }

    override fun postRegister() {
        javaClass.declaredFields.forEach { field ->
            if (field.type.isAssignableFrom(SubCommandReactor::class.java)) {
                val name = field.name.toLowerCase(Locale.ROOT)
                field.isAccessible = true
                val reactor = field.get(this) as SubCommandReactor
                subcommands[name] = object : SubCommandBase {
                    override val name: String = name
                    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) =
                        reactor.execute(event, message, prefix, user, cmd, args, channel)

                }
            }
        }
    }

    fun subCmd(reactor: (event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) -> Unit): SubCommandReactor = object : SubCommandReactor {
        override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
            reactor(event, message, prefix, user, cmd, args, channel)
        }
    }

    fun subCmd(reactor: CommandBase): SubCommandReactor = object : SubCommandReactor {
        override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
            reactor.execute(event, message, prefix, user, cmd, args, channel)
        }
    }
}

interface SubCommandBase : SubCommandReactor {
    val name: String
}

interface SubCommandReactor {
    suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel)
}

inline suspend fun <T> MessageCreator.getCatching(user: User, run: suspend () -> T): T {
    try {
        return run()
    } catch (t: Throwable) {
        try {
            if (t !is SuppressedException) reply { generateThrowable(t, user) }.subscribe()
            throw SuppressedException()
        } catch (throwable2: Throwable) {
            throwable2.addSuppressed(t)
            throw throwable2
        }
    }
}

fun MutableList<String>.validateEmpty(prefix: String, usage: String) {
    validateUsage(prefix, 0, usage)
}

fun MutableList<String>.validateNotEmpty(prefix: String, usage: String) {
    validateUsage(prefix, 1..Int.MAX_VALUE, usage)
}

fun MutableList<String>.validateUsage(prefix: String, length: Int, usage: String) {
    validateUsage(prefix, length..length, usage)
}

fun MutableList<String>.validateUsage(prefix: String, length: IntRange, usage: String) {
    if (size !in length) {
        throw InvalidUsageException("$prefix$usage")
    }
}

fun Member.validateAdmin() = validatePermissions(Permission.ADMINISTRATOR)

fun Member.validatePermissions(permission: Permission) {
    if (basePermissions.block()?.contains(permission) != true) {
        throw IllegalStateException("This command requires `${permission.name.toLowerCase(Locale.ROOT).capitalize()}` permission!")
    }
}

fun MessageCreateEvent.validateInGuild() {
    if (!guildId.isPresent) {
        throw IllegalStateException("This command is only available in servers.")
    }
}

fun Namespace.validateNamespace() {
    if (reloading) {
        throw IllegalStateException("Namespace (ID: $id) is reloading now, please try again in 5 seconds.")
    }
}

fun Namespace.validateGuild(event: MessageCreateEvent) {
    if (event.guildId.isPresent) {
        if (!ConfigManager[event.guildId.get().asLong()].isMappingsEnabled(id)) {
            throw IllegalStateException("Namespace (ID: $id) is disabled on this server.")
        }
    }
}

fun MappingsProvider.validateDefaultVersionNotEmpty() {
    if (isEmpty()) {
        throw IllegalStateException("Invalid Default Version! Linkie may be reloading its cache right now.")
    }
}