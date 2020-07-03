package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import me.shedaniel.linkie.discord.*
import java.net.URL

object FTBDramaCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateEmpty(cmd)
        val jsonText = URL("https://ftb-drama.herokuapp.com/json").readText()
        val jsonObject = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true)).parseJson(jsonText).jsonObject
        val text = jsonObject["drama"]!!.primitive.content
        val permLink = "https://ftb-drama.herokuapp.com/${jsonObject["version"]!!.primitive.content}/${jsonObject["seed"]!!.primitive.content}"
        channel.createEmbedMessage {
            setTitle("${user.username} starts a drama!")
            setUrl(permLink)
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            setDescription(text)
        }.subscribe()
    }

    override fun getName(): String? = "FTB Drama Command"
    override fun getDescription(): String? = "Generates ftb drama."
}