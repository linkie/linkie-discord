package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.discord.*
import java.net.URL

object FabricDramaCommand : CommandBase {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateEmpty(cmd)
        val jsonText = URL("https://fabric-drama.herokuapp.com/json").readText()
        val jsonObject = json.parseToJsonElement(jsonText).jsonObject
        val text = jsonObject["drama"]!!.jsonPrimitive.content
        val permLink = "https://fabric-drama.herokuapp.com/${jsonObject["version"]!!.jsonPrimitive.content}/${jsonObject["seed"]!!.jsonPrimitive.content}"
        channel.createEmbedMessage {
            setTitle("${user.username} starts a drama!")
            setUrl(permLink)
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setDescription(text)
        }.subscribe()
    }

    override fun getName(): String? = "Fabric Drama Command"
    override fun getDescription(): String? = "Generates fabric drama."
}