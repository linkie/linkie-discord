package me.shedaniel.linkie.discord.listener.listeners

import discord4j.rest.util.Color

object ShedanielVersionListener : MavenPomVersionListener() {
    init {
        listen("rei", "https://maven.shedaniel.me/me/shedaniel/RoughlyEnoughItems/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("REI Update")
                setDescription("New REI version has been added: $version")
                setColor(Color.YELLOW)
            }.subscribe()
        }

        listen("cloth-config", "https://maven.shedaniel.me/me/shedaniel/cloth/cloth-config/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Cloth Config Update")
                setDescription("New Cloth Config version has been added: $version")
                setColor(Color.YELLOW)
            }.subscribe()
        }

        listen("cloth-api", "https://maven.shedaniel.me/me/shedaniel/cloth/api/cloth-api/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Cloth API Update")
                setDescription("New Cloth API version has been added: $version")
                setColor(Color.YELLOW)
            }.subscribe()
        }
    }
}