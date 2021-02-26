package me.shedaniel.linkie.discord.listener.listeners

import discord4j.rest.util.Color

object ArchitecturyVersionListener : MavenPomVersionListener() {
    init {
        listen("plugin", "https://maven.shedaniel.me/me/shedaniel/architectury-plugin/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Architectury Plugin Update")
                setDescription("New Architectury Plugin version has been added: $version")
                setColor(Color.ORANGE)
            }.subscribe()
        }

        listen("transformer", "https://maven.shedaniel.me/me/shedaniel/architectury-transformer/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Architectury Transformer Update")
                setDescription("New Architectury Transformer version has been added: $version")
                setColor(Color.ORANGE)
            }.subscribe()
        }

        listen("api", "https://maven.shedaniel.me/me/shedaniel/architectury/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Architectury API Update")
                setDescription("New Architectury API version has been added: $version")
                setColor(Color.ORANGE)
            }.subscribe()
        }

        listen("api-snapshot", "https://maven.shedaniel.me/me/shedaniel/architectury-snapshot/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Architectury API Update")
                setDescription("New Architectury API snapshot has been added: $version")
                setColor(Color.ORANGE)
            }.subscribe()
        }

        listen("loom", "https://maven.shedaniel.me/forgified-fabric-loom/forgified-fabric-loom.gradle.plugin/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Architectury Loom Update")
                setDescription("New Architectury Loom version has been added: $version")
                setColor(Color.ORANGE)
            }.subscribe()
        }
    }
}