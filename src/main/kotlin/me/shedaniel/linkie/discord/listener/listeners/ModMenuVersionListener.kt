package me.shedaniel.linkie.discord.listener.listeners

import discord4j.rest.util.Color

object ModMenuVersionListener : MavenPomVersionListener() {
    init {
        listen("modmenu", "https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Mod Menu Update")
                setDescription("New Mod Menu has been added: $version")
                setColor(Color.BLUE)
            }.subscribe()
        }
    }
}