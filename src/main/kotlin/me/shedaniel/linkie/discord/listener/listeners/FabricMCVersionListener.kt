package me.shedaniel.linkie.discord.listener.listeners

import discord4j.rest.util.Color
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion

object FabricMCVersionListener : MavenPomVersionListener() {
    init {
        listen("intermediary", "https://maven.fabricmc.net/net/fabricmc/intermediary/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                val isUnstable = version.tryToVersion() == null || version.toVersion().snapshot == null
                setTitle("Fabric Intermediary Update")
                setDescription("New Intermediary ${if (isUnstable) "snapshot" else "release"} has been added: $version")
                setColor(Color.GRAY)
            }.subscribe()
        }

        listen("installer", "https://maven.fabricmc.net/net/fabricmc/fabric-installer/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Fabric Installer Update")
                setDescription("New Fabric Installer version has been added: $version")
                setColor(Color.GRAY)
            }.subscribe()
        }

        listen("loom", "https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml") { version, message ->
            if (version.endsWith("SNAPSHOT")) return@listen
            message.sendEmbed {
                setTitle("Fabric Loom Update")
                setDescription("New Fabric Loom version has been added: $version")
                setColor(Color.GRAY)
            }.subscribe()
        }

        listen("loader", "https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Fabric Loader Update")
                setDescription("New Fabric Loader version has been added: $version")
                setColor(Color.GRAY)
            }.subscribe()
        }

        listen("yarn", "https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Yarn Update")
                setDescription("New Yarn version has been added: $version")
                setColor(Color.GRAY)
            }.subscribe()
        }

        listen("api", "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml") { version, message ->
            message.sendEmbed {
                setTitle("Fabric API Update")
                setDescription("New Fabric API version has been added: $version")
                setColor(Color.GRAY)
            }.subscribe()
        }
    }
}