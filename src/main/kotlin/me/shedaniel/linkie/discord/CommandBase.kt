package me.shedaniel.linkie.discord

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.config.ConfigManager
import java.util.*
import java.util.concurrent.atomic.AtomicReference

interface CommandBase {
    fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel)

    fun getName(): String? = null
    fun getDescription(): String? = null

    fun getCategory(): CommandCategory = CommandCategory.NORMAL
}

inline fun CommandBase.runCatching(message: AtomicReference<Message?>, channel: MessageChannel, user: User, crossinline run: () -> Unit) {
    try {
        run()
    } catch (t: Throwable) {
        try {
            if (t !is SuppressedException) message.editOrCreate(channel) { generateThrowable(t, user) }.subscribe()
        } catch (throwable2: Throwable) {
            throwable2.addSuppressed(t)
            throw throwable2
        }
    }
}

inline fun <T> CommandBase.getCatching(message: AtomicReference<Message?>, channel: MessageChannel, user: User, crossinline run: () -> T): T {
    try {
        return run()
    } catch (t: Throwable) {
        try {
            if (t !is SuppressedException) message.editOrCreate(channel) { generateThrowable(t, user) }.subscribe()
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

enum class CommandCategory(val description: String?) {
    NORMAL(null),
    TRICK("Scripting");

    companion object {
        fun getValues(guildId: Snowflake?): Array<CommandCategory> {
            return values()
        }
    }
}