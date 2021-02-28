/*
 * Copyright (c) 2019, 2020, 2021 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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