package me.shedaniel.linkie

import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import java.awt.Color
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class CommandApi(private val prefix: String) {
    private val executors = Executors.newScheduledThreadPool(64)
    private val commandMap: MutableMap<String, CommandBase> = mutableMapOf()
    internal val commands: MutableMap<CommandBase, MutableSet<String>> = mutableMapOf()

    @Suppress("UNUSED_PARAMETER")
    fun getPrefix(isSpecial: Boolean = false): String =
//            (if (isSpecial) "+" else prefix).toLowerCase()
            prefix.toLowerCase()

    fun registerCommand(command: CommandBase, vararg l: String): CommandApi {
        for (ll in l)
            commandMap[ll.toLowerCase()] = command
        commands.getOrPut(command, ::mutableSetOf).addAll(l)
        return this
    }

    fun onMessageCreate(event: MessageCreateEvent) {
        CompletableFuture.runAsync(Runnable {
            val user: User? = event.message.author.orElse(null)
            val message: String? = event.message.content.orElse(null)
            val channel = event.message.channel.block()
            if (user == null || user.isBot || message == null || channel == null)
                return@Runnable
            val prefix = getPrefix(event.guildId.orElse(null)?.asLong() == 432055962233470986L)
            if (message.toLowerCase().startsWith(prefix)) {
                val content = message.substring(prefix.length)
                val split = if (content.contains(" ")) content.split(" ").dropLastWhile(String::isEmpty).toTypedArray() else arrayOf(content)
                val cmd = split[0].toLowerCase()
                val args = split.drop(1).toTypedArray()
                if (cmd in commandMap)
                    try {
                        commandMap[cmd]!!.execute(event, user, cmd, args, channel)
                    } catch (throwable: Throwable) {
                        try {
                            channel.createEmbed { it.generateThrowable(throwable, user) }.subscribe()
                        } catch (throwable2: Throwable) {
                            throwable2.addSuppressed(throwable)
                            throwable2.printStackTrace()
                        }
                    }

            }
        }, executors)
    }

}

fun EmbedCreateSpec.generateThrowable(throwable: Throwable, user: User? = null) {
    setTitle("Linkie Error")
    setColor(Color.red)
    user?.apply { setFooter("Requested by $discriminatedName", avatarUrl) }
    setTimestamp(Instant.now())
    addField("Error occurred while processing the command:", throwable.javaClass.simpleName + ": " + throwable.localizedMessage
            .replace(System.getenv("GOOGLEAPI"), "*")
            , false)
}