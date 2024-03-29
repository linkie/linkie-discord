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
import me.shedaniel.linkie.discord.OptionlessCommand
import me.shedaniel.linkie.discord.asSlashCommand
import me.shedaniel.linkie.discord.handler.CommandHandler
import me.shedaniel.linkie.discord.handler.CommandManager
import me.shedaniel.linkie.discord.handler.SimpleThrowableHandler
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.SlashCommands
import me.shedaniel.linkie.discord.scommands.optNullable
import me.shedaniel.linkie.discord.scommands.string
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.dangerButton
import me.shedaniel.linkie.discord.utils.discordEmote
import me.shedaniel.linkie.discord.utils.dismissButton
import me.shedaniel.linkie.discord.utils.primaryButton
import me.shedaniel.linkie.discord.utils.reply
import me.shedaniel.linkie.discord.utils.replyComplex
import me.shedaniel.linkie.discord.utils.selectMenu
import me.shedaniel.linkie.discord.utils.use
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
    slashCommands.globalCommand(ButtonCommand().asSlashCommand("Description", listOf("button")))
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

class ButtonCommand : OptionlessCommand {
    override suspend fun execute(ctx: CommandContext) = ctx.use {
        var count = 0
        var lastSelected = 0
        message.replyComplex {
            layout {
                row {
                    primaryButton("yes") {
                        count++
                        reply("This message is now edited $count times lol")
                    }
                    dangerButton("no") {
                        replyComplex {
                            layout {
                                // once again override the widgets
                                dismissButton()
                            }
                        }
                    }
                }
                row {
                    selectMenu {
                        addOption("Epic Entry 1", "entryOne", description = "Epic description", emoji = "❌".discordEmote)
                        addOption("Epic Entry 2", "entryTwo", description = "Epic description", emoji = "❌".discordEmote)
                        options = options.mapIndexed { index, option -> option.withDefault(index == lastSelected) }.toMutableList()
                        minValues(1)
                        maxValues(1)
                        action { _, options ->
                            lastSelected = this@selectMenu.options.indexOfFirst { it.value == options.first() }
                            reply("You have selected ${options.first()}")
                        }
                    }
                }
            }
            embed {
                description("hello")
            }
        }
    }
}