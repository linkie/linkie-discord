package me.shedaniel.linkie

import discord4j.core.event.domain.message.MessageCreateEvent
import java.awt.Color
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors

class CommandApi(private val prefix: String) {
    private val executors = Executors.newScheduledThreadPool(16)
    private val commandMap: MutableMap<String, CommandBase> = mutableMapOf()
    internal val commands: MutableMap<CommandBase, MutableSet<String>> = mutableMapOf()

    fun getPrefix(isSpecial: Boolean): String {
        return if (isSpecial) "!" else prefix
    }

    fun registerCommand(command: CommandBase, vararg l: String): CommandApi {
        for (ll in l)
            commandMap[ll.toLowerCase()] = command
        commands.getOrPut(command, ::mutableSetOf).addAll(l)
        return this
    }

    fun onMessageCreate(event: MessageCreateEvent) {
        val member = event.member.orElse(null)
        if (!event.member.isPresent || member.isBot || !event.message.content.isPresent)
            return
        executors.submit {
            val message = event.message.content.get()
            val channel = event.message.channel.block()
            val prefix = getPrefix(!channel!!.type.name.startsWith("GUILD_") || event.guildId.isPresent && event.guildId.get().asLong() != 432055962233470986L)
            if (message.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                val content = message.substring(prefix.length)
                val split = if (content.contains(" ")) content.split(" ").dropLastWhile(String::isEmpty).toTypedArray() else arrayOf(content)
                val cmd = split[0].toLowerCase(Locale.ROOT)
                val args = split.drop(1).toTypedArray()
                if (commandMap.containsKey(cmd))
                    try {
                        commandMap[cmd]!!.execute(executors, event, member, cmd, args, channel)
                    } catch (throwable: Throwable) {
                        channel.createEmbed { emd ->
                            emd.setTitle("Linkie Error")
                            emd.setColor(Color.red)
                            emd.setFooter("Requested by " + member.username + "#" + member.discriminator, member.avatarUrl)
                            emd.setTimestamp(Instant.now())
                            emd.addField("Error occurred while processing the command:", throwable.javaClass.simpleName + ": " + throwable.localizedMessage, false)
                        }.subscribe()
                    }

            }
        }
    }

}