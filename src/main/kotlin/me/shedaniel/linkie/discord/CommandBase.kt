package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace

interface CommandBase {
    fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel)

    fun getName(): String? = null
    fun getDescription(): String? = null

    fun getCategory(): CommandCategory = CommandCategory.NORMAL

}

inline fun CommandBase.runCatching(message: Message?, channel: MessageChannel, user: User, crossinline run: () -> Unit) {
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

inline fun <T> CommandBase.getCatching(message: Message?, channel: MessageChannel, user: User, crossinline run: () -> T): T {
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

fun MutableList<String>.validateEmpty(usage: String) {
    validateUsage(0, usage)
}

fun MutableList<String>.validateNotEmpty(usage: String) {
    validateUsage(1..Int.MAX_VALUE, usage)
}

fun MutableList<String>.validateUsage(length: Int, usage: String) {
    validateUsage(length..length, usage)
}

fun MutableList<String>.validateUsage(length: IntRange, usage: String) {
    if (size !in length) {
        throw InvalidUsageException("${commandApi.prefix}$usage")
    }
}

fun Namespace.validateNamespace() {
    if (reloading) {
        throw IllegalStateException("Namespace (ID: $id) is reloading now, please try again in 5 seconds.")
    }
}

fun MappingsProvider.validateDefaultVersionNotEmpty() {
    if (isEmpty()) {
        throw IllegalStateException("Invalid Default Version! Linkie may be reloading its cache right now.")
    }
}

enum class CommandCategory(val description: String?) {
    NORMAL(null);

    companion object {
        fun getValues(guildId: Snowflake?): Array<CommandCategory> {
            return arrayOf(NORMAL)
        }
    }
}