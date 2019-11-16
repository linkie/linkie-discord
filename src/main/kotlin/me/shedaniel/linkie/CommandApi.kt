package me.shedaniel.linkie

import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import java.awt.Color
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class CommandApi(private val prefix: String) {
    private val executors = Executors.newScheduledThreadPool(64)
    private val commandMap: MutableMap<String, CommandBase> = mutableMapOf()
    internal val commands: MutableMap<CommandBase, MutableSet<String>> = mutableMapOf()

    fun getPrefix(isSpecial: Boolean): String {
        return (if (isSpecial) "+" else prefix).toLowerCase()
    }

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
                            channel.createEmbed { emd ->
                                emd.setTitle("Linkie Error")
                                emd.setColor(Color.red)
                                emd.setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                                emd.setTimestamp(Instant.now())
                                emd.addField("Error occurred while processing the command:", throwable.javaClass.simpleName + ": " + throwable.localizedMessage, false)
                            }.subscribe()
                        } catch (throwable2: Throwable) {
                            throwable2.addSuppressed(throwable)
                            throwable2.printStackTrace()
                        }
                    }

            }
        }, executors)
    }

}