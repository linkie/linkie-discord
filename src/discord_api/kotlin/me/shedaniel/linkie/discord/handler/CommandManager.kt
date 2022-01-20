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

package me.shedaniel.linkie.discord.handler

import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.discord.BuiltCommand
import me.shedaniel.linkie.discord.Command
import me.shedaniel.linkie.discord.LegacyCommand
import me.shedaniel.linkie.discord.build
import me.shedaniel.linkie.discord.scommands.SlashCommands
import me.shedaniel.linkie.discord.utils.CommandContext

open class CommandManager(
    private val prefix: String,
) : CommandAcceptor {
    protected val slashCommandMap: MutableMap<String, BuiltCommand> = mutableMapOf()
    protected val regularCommandMap: MutableMap<String, Any> = mutableMapOf()
    val slashCommands: MutableCollection<BuiltCommand>
        get() = slashCommandMap.values

    fun registerCommand(command: Command, vararg l: String): CommandManager =
        registerCommand(true, command, l.toList())

    fun registerCommand(slash: Boolean, command: Command, vararg l: String): CommandManager =
        registerCommand(slash, command, l.toList())

    fun registerCommand(
        command: Command,
        regular: List<String>,
        slashAlias: List<String> = regular,
    ): CommandManager =
        registerCommand(true, command, regular, slashAlias)

    fun registerCommand(
        slash: Boolean,
        command: Command,
        regular: List<String>,
        slashAlias: List<String> = regular,
    ): CommandManager {
        val builtCommand = runBlocking { command.build(regular, slashAlias, slash) { "Command '$it'" } }
        for (ll in regular)
            regularCommandMap[ll.lowercase()] = builtCommand
        for (ll in slashAlias)
            slashCommandMap[ll.lowercase()] = builtCommand
        command.postRegister()
        return this
    }

    fun registerCommand(command: LegacyCommand, vararg regular: String): CommandManager {
        for (ll in regular)
            regularCommandMap[ll.lowercase()] = command
        command.postRegister()
        return this
    }

    override fun getPrefix(event: MessageCreateEvent): String = prefix

    override suspend fun execute(event: MessageCreateEvent, ctx: CommandContext, args: MutableList<String>): Boolean {
        if (ctx.cmd in regularCommandMap) {
            val command = regularCommandMap[ctx.cmd]!!
            if (command is LegacyCommand) {
                command.execute(ctx, event.message, args)
                return true
            } else if (command is BuiltCommand) {
                executeCommand(command, args, ctx)
                return true
            }
        }
        return false
    }

    private suspend fun executeCommand(command: BuiltCommand, args: MutableList<String>, ctx: CommandContext) {
        if (!command.command.execute(ctx, command.regularCommand, args)) {
            throw IllegalArgumentException("Invalid Usage:\n${command.regularCommand.usage(ctx)}")
        }
    }

    fun registerToSlashCommands(slashCommands: SlashCommands) {
        val slashCommandsList = this.slashCommands.flatMap { it.slashCommand?.cmds ?: listOf() }
        if (slashCommandsList.size >= 100) {
            throw IllegalArgumentException("Too many slash commands registered!")
        }
        println("Registered ${slashCommandsList.count()} slash commands")
        this.slashCommands.forEach { cmd ->
            if (cmd.slashCommand != null) {
                slashCommands.globalCommand(cmd.slashCommand)
            }
        }
    }
}
