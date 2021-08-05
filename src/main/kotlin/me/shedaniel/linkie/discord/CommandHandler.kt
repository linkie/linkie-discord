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

import com.soywiz.korio.async.runBlockingNoJs
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.CommandContext

object CommandHandler : CommandAcceptor {
    private val slashCommandMap: MutableMap<String, BuiltCommand> = mutableMapOf()
    private val regularCommandMap: MutableMap<String, Any> = mutableMapOf()
    val slashCommands: MutableCollection<BuiltCommand>
        get() = slashCommandMap.values

    fun registerCommand(command: Command, vararg l: String): CommandHandler =
        registerCommand(true, command, l.toList())

    fun registerCommand(slash: Boolean, command: Command, vararg l: String): CommandHandler =
        registerCommand(slash, command, l.toList())

    fun registerCommand(
        command: Command,
        regular: List<String>,
        slashAlias: List<String> = regular
    ): CommandHandler =
        registerCommand(true, command, regular, slashAlias)

    fun registerCommand(
        slash: Boolean,
        command: Command,
        regular: List<String>,
        slashAlias: List<String> = regular
    ): CommandHandler {
        val builtCommand = runBlockingNoJs { command.build(regular, slashAlias, slash) { "Command '$it'" } }
        for (ll in regular)
            regularCommandMap[ll.toLowerCase()] = builtCommand
        for (ll in slashAlias)
            slashCommandMap[ll.toLowerCase()] = builtCommand
        command.postRegister()
        return this
    }

    fun registerCommand(command: LegacyCommand, vararg regular: String): CommandHandler {
        for (ll in regular)
            regularCommandMap[ll.toLowerCase()] = command
        command.postRegister()
        return this
    }

    override fun getPrefix(event: MessageCreateEvent): String? =
        event.guildId.orElse(null)?.let { ConfigManager[it.asLong()].prefix }

    override suspend fun execute(event: MessageCreateEvent, ctx: CommandContext, args: MutableList<String>) {
        if (ctx.cmd in regularCommandMap) {
            val command = regularCommandMap[ctx.cmd]!!
            if (command is LegacyCommand) {
                command.execute(ctx, event.message, args)
            } else if (command is BuiltCommand) {
                executeCommand(command, args, ctx)
            }
        } else {
            TricksManager.globalTricks[ctx.cmd]?.also { trick ->
                val evalContext = EvalContext(
                    ctx,
                    event.message,
                    trick.flags,
                    args,
                    parent = true,
                )
                LinkieScripting.evalTrick(evalContext, ctx.message, trick) {
                    LinkieScripting.simpleContext.push {
                        ContextExtensions.commandContexts(evalContext, ctx.user, ctx.channel, ctx.message, this)
                    }
                }
            }
        }
    }

    private suspend fun executeCommand(command: BuiltCommand, args: MutableList<String>, ctx: CommandContext) {
        if (!command.command.execute(ctx, command.regularCommand, args)) {
            throw InvalidUsageException("Invalid Usage:\n${command.regularCommand.usage(ctx)}")
        }
    }
}
