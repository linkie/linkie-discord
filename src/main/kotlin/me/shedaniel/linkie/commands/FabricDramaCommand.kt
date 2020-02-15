package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.CommandBase
import me.shedaniel.linkie.discriminatedName
import me.shedaniel.linkie.setTimestampToNow
import java.net.URL

object FabricDramaCommand : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        val html = URL("http://fabric-drama.herokuapp.com/").readText()
        val text = html.substring(html.lastIndexOf("<div class='drama'>") + "<div class='drama'>".length, html.indexOf("</div>"))
//        val permaLink = "http://fabric-drama.herokuapp.com${html.let {
//            val cutBefore = it.substring(0, it.indexOf("'>Permalink</a> <br><br>"))
//            cutBefore.substring(cutBefore.lastIndexOf("<a href='") + "<a href='".length, cutBefore.lastIndex)
//        }}"
        val permaLink = "http://fabric-drama.herokuapp.com"
        channel.createEmbed {
            it.setTitle("Fabric Drama")
            it.setUrl(permaLink)
            it.setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            it.setTimestampToNow()
            it.setDescription(text)
        }.subscribe()
    }

    override fun getName(): String? = "Fabric Drama Command"
    override fun getDescription(): String? = "Generates fabric drama."
}