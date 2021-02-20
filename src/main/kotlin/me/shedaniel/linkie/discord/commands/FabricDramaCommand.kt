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

package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.discord.CommandBase
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.discord.validateEmpty
import java.net.URL

object FabricDramaCommand : CommandBase {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun execute(event: MessageCreateEvent, message: MessageCreator, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        args.validateEmpty(prefix, cmd)
        val jsonText = URL("https://fabric-drama.herokuapp.com/json").readText()
        val jsonObject = json.parseToJsonElement(jsonText).jsonObject
        val text = jsonObject["drama"]!!.jsonPrimitive.content
        val permLink = "https://fabric-drama.herokuapp.com/${jsonObject["version"]!!.jsonPrimitive.content}/${jsonObject["seed"]!!.jsonPrimitive.content}"
        message.sendEmbed {
            setTitle("${user.username} starts a drama!")
            setUrl(permLink)
            setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
            setTimestampToNow()
            description = text
        }.subscribe()
    }
}