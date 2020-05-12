package me.shedaniel.linkie

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.Color
import java.util.concurrent.Executors

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
        if ((event.message.roleMentions.any { role ->
                    role.name == "Linkie"
                }.block() == true || event.message.userMentionIds.contains(api.selfId.get())) && event.guildId.isPresent) {
            api.getUserById(Snowflake.of(430615025066049538)).subscribe { dmUser ->
                dmUser.privateChannel.subscribe { dmChannel ->
                    event.guild.subscribe { guild ->
                        dmChannel.createEmbed {
                            it.setTitle("${user.discriminatedName} pinged Linkie in ${guild.name}")
                            it.setDescription(message + "\nhttps://discordapp.com/channels/${guild.id.asString()}/${channel.id.asString()}/${event.message.id.asString()}")
                        }.subscribe()
                    }
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
    addField("Error occurred while processing the command:", throwable.javaClass.simpleName + ": " + (throwable.localizedMessage ?: "Unknown Message")
            .replace(System.getenv("GOOGLEAPI"), "*")
            .replace(System.getenv("PASTEEE"), "*")
            , false)
    if (isDebug)
        throwable.printStackTrace()
}