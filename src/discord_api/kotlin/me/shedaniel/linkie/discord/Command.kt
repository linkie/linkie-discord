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

package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.Message
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.discord.scommands.ArgReader
import me.shedaniel.linkie.discord.scommands.ExecuteResult
import me.shedaniel.linkie.discord.scommands.NestedSlashCommandOption
import me.shedaniel.linkie.discord.scommands.OptionsGetter
import me.shedaniel.linkie.discord.scommands.OptionsGetterBuilder
import me.shedaniel.linkie.discord.scommands.SlashCommand
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilder
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.SlashCommandExecutorAcceptor
import me.shedaniel.linkie.discord.scommands.SubCommandOption
import me.shedaniel.linkie.discord.scommands.SubGroupCommandOption
import me.shedaniel.linkie.discord.scommands.sub
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.property
import java.util.*

typealias CommandExecutorOptionGetter = suspend (ctx: CommandContext, options: OptionsGetter) -> Unit
typealias CommandExecutorOptionLess = suspend (ctx: CommandContext) -> Unit
typealias CommandExecutor<T> = suspend (ctx: CommandContext, options: T) -> Unit

data class BuiltCommand(
    val command: Command,
    val regularCommand: SlashCommand,
    val slashCommand: SlashCommand?,
)

interface Command {
    fun SlashCommandExecutorAcceptor.executeCommandWithNothing(
        executor: CommandExecutorOptionLess,
    ) {
        execute { _, ctx, options ->
            runBlocking {
                executor(ctx)
            }
            return@execute true
        }
    }

    fun SlashCommandExecutorAcceptor.executeCommandWithGetter(
        executor: CommandExecutorOptionGetter,
    ) {
        execute { _, ctx, options ->
            runBlocking {
                executor(ctx, options)
            }
            return@execute true
        }
    }

    suspend fun buildCommandPrivate(builder: SlashCommandBuilderInterface, slash: Boolean) = builder.buildCommand(slash)
    suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean)
    suspend fun execute(ctx: CommandContext, command: SlashCommand, args: MutableList<String>): Boolean {
        val isGroup = command.options.any { it is NestedSlashCommandOption }
        val root = if (isGroup) SubGroupCommandOption(ctx.cmd, "", listOf()) else SubCommandOption(ctx.cmd, "", listOf())
        command.options.forEach(root::arg)
        root.execute(command)
        val reader = ArgReader(if (isGroup) args else (sequenceOf(ctx.cmd) + args.asSequence()).toMutableList())
        return root.execute(ctx, command, reader.property, OptionsGetterBuilder(command, ctx)) == ExecuteResult.EXECUTED
    }

    fun postRegister() {}
}

interface SimpleCommand<T> : Command {
    fun SlashCommandExecutorAcceptor.executeCommandWith(
        executor: CommandExecutor<T> = this@SimpleCommand::execute,
        optionSpec: OptionsGetter.() -> T,
    ) {
        execute { _, ctx, optionsGetter ->
            val options = optionSpec.invoke(optionsGetter)
            runBlocking {
                executor(ctx, options)
            }
            return@execute true
        }
    }

    suspend fun execute(ctx: CommandContext, options: T)
}

fun Command.build(
    regular: List<String>,
    slashAlias: List<String>,
    slash: Boolean,
    description: (cmd: String) -> String,
): BuiltCommand = BuiltCommand(
    command = this,
    regularCommand = asSlashCommand(description, regular, false),
    slashCommand = if (slash) asSlashCommand(description, slashAlias, true) else null,
)

interface OptionlessCommand : Command {
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) =
        executeCommandWithNothing { execute(it) }

    override suspend fun execute(ctx: CommandContext, command: SlashCommand, args: MutableList<String>): Boolean {
        execute(ctx)
        return true
    }

    suspend fun execute(ctx: CommandContext)
}

@Deprecated("Legacy Command!")
interface LegacyCommand {
    suspend fun execute(ctx: CommandContext, trigger: Message, args: MutableList<String>)
    fun postRegister() {}
}

open class SubCommandHolder : Command {
    private val subcommands = mutableMapOf<String, Command>()
    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        this@SubCommandHolder.javaClass.declaredFields.forEach { field ->
            if (field.type.isAssignableFrom(SubCommandEntry::class.java)) {
                val name = field.name.toLowerCase(Locale.ROOT)
                field.isAccessible = true
                val command = field.get(this) as SubCommandEntry<*>
                subcommands[name] = command.command
            }
        }
        subcommands.forEach { (name, command) ->
            sub(name, "Sub command '$name'") {
                command.buildCommandPrivate(this@sub, slash)
            }
        }
    }

    fun <T : Command> subCmd(command: T): SubCommandEntry<T> = SubCommandEntry(command)
}

data class SubCommandEntry<T : Command>(val command: T)

fun Command.asSlashCommand(
    description: (cmd: String) -> String,
    aliases: List<String>,
    slash: Boolean = true,
): SlashCommand {
    return runBlocking { SlashCommandBuilder(description).cmd(*aliases.toTypedArray()).also { buildCommandPrivate(it, slash) } }
}

fun Command.asSlashCommand(
    description: String,
    aliases: List<String>,
    slash: Boolean = true,
): SlashCommand {
    return runBlocking { SlashCommandBuilder(description).cmd(*aliases.toTypedArray()).also { buildCommandPrivate(it, slash) } }
}
