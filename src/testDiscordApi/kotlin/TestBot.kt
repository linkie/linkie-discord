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

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import me.shedaniel.linkie.discord.Command
import me.shedaniel.linkie.discord.handler.CommandHandler
import me.shedaniel.linkie.discord.handler.CommandManager
import me.shedaniel.linkie.discord.handler.SimpleThrowableHandler
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.SlashCommands
import me.shedaniel.linkie.discord.scommands.optNullable
import me.shedaniel.linkie.discord.scommands.string
import kotlin.test.Test

fun client(): DiscordClient = DiscordClientBuilder.create(System.getenv("TOKEN") ?: throw NullPointerException("Invalid Token: null")).build()
fun login(): GatewayDiscordClient = client().login().block()!!

@Test
fun testBot() {
    val client = login()
    val throwableHandler = SimpleThrowableHandler()
    val slashCommands = SlashCommands(client, throwableHandler)
    val commandManager = CommandManager(prefix = "!")
    CommandHandler(client, commandManager, throwableHandler).register()

    // register commands
    commandManager.registerCommand(HelloCommand(), "hello")

    commandManager.registerToSlashCommands(slashCommands)
}

class HelloCommand : Command {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        val nameOption = string("name", "Your name", required = false)
        executeCommandWithGetter { ctx, options ->
            val name = options.optNullable(nameOption) ?: ctx.user.username
            ctx.message.reply("Hello, $name!")
        }
    }
}
