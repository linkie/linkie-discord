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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.discord.OptionlessCommand
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.linkButton
import me.shedaniel.linkie.discord.utils.use
import java.net.URL

object FabricDramaCommand : OptionlessCommand {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun execute(ctx: CommandContext) {
        ctx.use {
            message.acknowledge()
            val jsonText = URL("https://fabric-drama.herokuapp.com/json").readText()
            val jsonObject = json.parseToJsonElement(jsonText).jsonObject
            val text = jsonObject["drama"]!!.jsonPrimitive.content
            val permLink = "https://fabric-drama.herokuapp.com/${jsonObject["version"]!!.jsonPrimitive.content}/${jsonObject["seed"]!!.jsonPrimitive.content}"
            message.reply(ctx, {
                linkButton("Permanent Link", permLink)
            }) {
                title("${user.username} starts a drama!")
                url(permLink)
                basicEmbed(user)
                description = text
            }
        }
    }
}