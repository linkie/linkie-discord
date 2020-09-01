package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CommandMap(private val commandAcceptor: CommandAcceptor, val defaultPrefix: String) {
    private val splitRegex = "\\s".toRegex()
    fun onMessageCreate(event: MessageCreateEvent) {
        val channel = event.message.channel.block()
        val user: User? = event.message.author.orElse(null)
        val message: String = event.message.content
        val prefix = commandAcceptor.getPrefix(event) ?: defaultPrefix
        if (user == null || user.isBot || channel == null)
            return
        GlobalScope.launch {
            runCatching {
                if (message.toLowerCase().startsWith(prefix)) {
                    val content = message.substring(prefix.length)
                    val split = if (content.contains(splitRegex)) content.split(splitRegex).dropLastWhile(String::isEmpty) else listOf(content)
                    val cmd = split[0].toLowerCase()
                    val args = split.drop(1).toMutableList()
                    commandAcceptor.execute(event, prefix, user, cmd, args, channel)
                }
            }.exceptionOrNull()?.also { throwable ->
                if (throwable is SuppressedException) return@also
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

interface CommandAcceptor {
    fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel)
    fun getPrefix(event: MessageCreateEvent): String?
}

fun EmbedCreateSpec.generateThrowable(throwable: Throwable, user: User? = null) {
    setTitle("Linkie Error")
    setColor(Color.RED)
    user?.apply { setFooter("Requested by $discriminatedName", avatarUrl) }
    setTimestampToNow()
    addField("Error occurred while processing the command:", throwable.javaClass.simpleName + ": " + (throwable.localizedMessage ?: "Unknown Message"), false)
    if (isDebug)
        throwable.printStackTrace()
}