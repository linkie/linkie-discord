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
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.MessageBasedCommandContext
import me.shedaniel.linkie.discord.utils.msgCreator

object CommandHandler : CommandAcceptor {
    private val commandMap: MutableMap<String, Any> = mutableMapOf()
    val slashCommands: Set<BuiltCommand>
        get() = commandMap.mapNotNull { it.value as? BuiltCommand }.toSet()

    fun registerCommand(command: Command, vararg l: String): CommandHandler =
        registerCommand(true, command, *l)

    fun registerCommand(slash: Boolean, command: Command, vararg l: String): CommandHandler {
        val list = l.toMutableList()
        modifyDebug(list)
        val builtCommand = runBlockingNoJs { command.build(list.toList(), slash) { "Command '$it'" } }
        for (ll in list)
            commandMap[ll.toLowerCase()] = builtCommand
        command.postRegister()
        return this
    }

    private fun modifyDebug(list: MutableList<String>) {
        if (isDebug) {
            list.replaceAll { "${it}_debug" }
        }
    }

    fun registerCommand(command: LegacyCommand, vararg l: String): CommandHandler {
        for (ll in l)
            commandMap[ll.toLowerCase()] = command
        command.postRegister()
        return this
    }

    override fun getPrefix(event: MessageCreateEvent): String? =
        event.guildId.orElse(null)?.let { ConfigManager[it.asLong()].prefix }

    override suspend fun execute(event: MessageCreateEvent, prefix: String, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel) {
        val ctx = MessageBasedCommandContext(event, channel.msgCreator(event.message), prefix, cmd, channel)
        if (cmd in commandMap) {
            val command = commandMap[cmd]!!
            if (command is LegacyCommand) {
                command.execute(ctx, event.message, args)
            } else if (command is BuiltCommand) {
                executeCommand(command, args, ctx)
            }
        } else {
            TricksManager.globalTricks[cmd]?.also { trick ->
                val evalContext = EvalContext(
                    ctx,
                    event.message,
                    trick.flags,
                    args,
                    parent = true,
                )
                LinkieScripting.evalTrick(evalContext, ctx.message, trick) {
                    LinkieScripting.simpleContext.push {
                        ContextExtensions.commandContexts(evalContext, user, channel, ctx.message, this)
                    }
                }
            }
        }
    }

    private suspend fun executeCommand(command: BuiltCommand, args: MutableList<String>, ctx: CommandContext) {
        if (!command.command.execute(ctx, command.slashCommand, args)) {
            throw InvalidUsageException("Invalid Usage:\n${command.slashCommand.usage(ctx)}")
        }
    }
}
