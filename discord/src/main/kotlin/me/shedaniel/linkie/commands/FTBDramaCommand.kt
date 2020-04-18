package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import me.shedaniel.linkie.CommandBase
import me.shedaniel.linkie.discriminatedName
import me.shedaniel.linkie.setTimestampToNow
import java.net.URL

object FTBDramaCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        val jsonText = URL("https://ftb-drama.herokuapp.com/json").readText()
        val jsonObject = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true)).parseJson(jsonText).jsonObject
        val text = jsonObject["drama"]!!.primitive.content
        val permLink = "https://ftb-drama.herokuapp.com/${jsonObject["version"]!!.primitive.content}/${jsonObject["seed"]!!.primitive.content}"
        channel.createEmbed {
            it.setTitle("${user.username} starts a drama!")
            it.setUrl(permLink)
            it.setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            it.setTimestampToNow()
            it.setDescription(text)
        }.subscribe()
    }

    override fun getName(): String? = "FTB Drama Command"
    override fun getDescription(): String? = "Generates ftb drama."
}