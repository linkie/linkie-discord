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

object ShedanielVersionListener : MavenPomVersionListener() {
    init {
        listen("rei", "https://maven.shedaniel.me/me/shedaniel/RoughlyEnoughItems/maven-metadata.xml") { version, message ->
            message.reply {
                setTitle("REI Update")
                setDescription("New REI version has been added: $version")
                setColor(Color.YELLOW)
            }.subscribe()
        }

        listen("cloth-config", "https://maven.shedaniel.me/me/shedaniel/cloth/cloth-config/maven-metadata.xml") { version, message ->
            message.reply {
                setTitle("Cloth Config Update")
                setDescription("New Cloth Config version has been added: $version")
                setColor(Color.YELLOW)
            }.subscribe()
        }

        listen("cloth-api", "https://maven.shedaniel.me/me/shedaniel/cloth/api/cloth-api/maven-metadata.xml") { version, message ->
            message.reply {
                setTitle("Cloth API Update")
                setDescription("New Cloth API version has been added: $version")
                setColor(Color.YELLOW)
            }.subscribe()
        }
    }
}