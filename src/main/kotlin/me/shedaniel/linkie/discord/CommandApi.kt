package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.Color

class CommandApi(val prefix: String) {
    private val commandMap: MutableMap<String, CommandBase> = mutableMapOf()
    internal val commands: MutableMap<CommandBase, MutableSet<String>> = mutableMapOf()

    fun registerCommand(command: CommandBase, vararg l: String): CommandApi {
        for (ll in l)
            commandMap[ll.toLowerCase()] = command
        commands.getOrPut(command, ::mutableSetOf).addAll(l)
        return this
    }

    fun onMessageCreate(event: MessageCreateEvent) {
        val channel = event.message.channel.block()
        val user: User? = event.message.author.orElse(null)
        val message: String? = event.message.content.orElse(null)
        if (user == null || user.isBot || message == null || channel == null)
            return
        GlobalScope.launch {
            runCatching {
                if (message.toLowerCase().startsWith(prefix)) {
                    val content = message.substring(prefix.length)
                    val split = if (content.contains(" ")) content.split(" ").dropLastWhile(String::isEmpty).toTypedArray() else arrayOf(content)
                    val cmd = split[0].toLowerCase()
                    val args = split.drop(1).toTypedArray()
                    if (cmd in commandMap)
                        commandMap[cmd]!!.execute(event, user, cmd, args, channel)
                }
            }.exceptionOrNull()?.also { throwable ->
                try {
                    channel.createEmbed { it.generateThrowable(throwable, user) }.subscribe()
                } catch (throwable2: Exception) {
                    throwable2.addSuppressed(throwable)
                    throwable2.printStackTrace()
                }
            }
        }
    }
}

fun EmbedCreateSpec.generateThrowable(throwable: Throwable, user: User? = null) {
    setTitle("Linkie Error")
    setColor(Color.red)
    user?.apply { setFooter("Requested by $discriminatedName", avatarUrl) }
    setTimestampToNow()
    addField("Error occurred while processing the command:", throwable.javaClass.simpleName + ": " + (throwable.localizedMessage ?: "Unknown Message"), false)
    if (isDebug)
        throwable.printStackTrace()
}