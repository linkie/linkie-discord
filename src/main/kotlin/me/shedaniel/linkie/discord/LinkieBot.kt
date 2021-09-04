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

@file:JvmName("LinkieBot")

package me.shedaniel.linkie.discord

import com.soywiz.klock.TimeSpan
import discord4j.core.event.domain.message.MessageCreateEvent
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.shedaniel.linkie.LinkieConfig
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.commands.AboutCommand
import me.shedaniel.linkie.discord.commands.AddTrickCommand
import me.shedaniel.linkie.discord.commands.EvaluateCommand
import me.shedaniel.linkie.discord.commands.FTBDramaCommand
import me.shedaniel.linkie.discord.commands.FabricCommand
import me.shedaniel.linkie.discord.commands.FabricDramaCommand
import me.shedaniel.linkie.discord.commands.ForgeCommand
import me.shedaniel.linkie.discord.commands.GetValueCommand
import me.shedaniel.linkie.discord.commands.ListAllTricksCommand
import me.shedaniel.linkie.discord.commands.ListTricksCommand
import me.shedaniel.linkie.discord.commands.MigrateMMCommandToRequiredCommand
import me.shedaniel.linkie.discord.commands.QueryMappingsCommand
import me.shedaniel.linkie.discord.commands.QueryTranslateMappingsCommand
import me.shedaniel.linkie.discord.commands.RandomClassCommand
import me.shedaniel.linkie.discord.commands.RemoveTrickCommand
import me.shedaniel.linkie.discord.commands.RunTrickCommand
import me.shedaniel.linkie.discord.commands.SetValueCommand
import me.shedaniel.linkie.discord.commands.TrickInfoCommand
import me.shedaniel.linkie.discord.commands.TricksCommand
import me.shedaniel.linkie.discord.commands.ValueCommand
import me.shedaniel.linkie.discord.commands.ValueListCommand
import me.shedaniel.linkie.discord.commands.legacy.RemapAWATCommand
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.handler.CommandHandler
import me.shedaniel.linkie.discord.handler.CommandManager
import me.shedaniel.linkie.discord.scommands.SlashCommands
import me.shedaniel.linkie.discord.scommands.sub
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.LinkieScripting
import me.shedaniel.linkie.discord.scripting.push
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow
import me.shedaniel.linkie.namespaces.LegacyYarnNamespace
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.MojangHashedNamespace
import me.shedaniel.linkie.namespaces.MojangNamespace
import me.shedaniel.linkie.namespaces.MojangSrgNamespace
import me.shedaniel.linkie.namespaces.PlasmaNamespace
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.namespaces.YarrnNamespace
import me.shedaniel.linkie.utils.getMillis
import me.shedaniel.linkie.utils.warn
import java.io.File
import java.util.*

const val testingGuild = 432055962233470986L

fun main() {
    (File(System.getProperty("user.dir")) / ".properties").apply {
        if (exists()) {
            val properties = Properties()
            reader().use {
                properties.load(it)
            }
            properties.forEach { key, value -> System.setProperty(key.toString(), value.toString()) }
        }
    }
    TricksManager.load()
    ConfigManager.load()
    if (System.getProperty("PORT") != null) {
        GlobalScope.launch {
            // netty server to allow status pages to ping this bot
            embeddedServer(Netty, port = System.getProperty("PORT").toInt()) {
                routing {
                    get("/status") {
                        call.respondText("""{}""", ContentType.Application.Json)
                    }
                }
            }.start(wait = true)
        }
    }
    start(
        LinkieConfig.DEFAULT.copy(
            namespaces = listOf(
                YarnNamespace,
                MojangNamespace,
                MojangSrgNamespace,
                MojangHashedNamespace,
                MCPNamespace,
                LegacyYarnNamespace,
                YarrnNamespace,
                PlasmaNamespace,
            )
        )
    ) {
        val slashCommands = SlashCommands(this, LinkieThrowableHandler, ::warn)
        val commandManager = object : CommandManager(if (isDebug) "@" else "!") {
            override fun getPrefix(event: MessageCreateEvent): String {
                return event.guildId.orElse(null)?.let { ConfigManager[it.asLong()].prefix } ?: super.getPrefix(event)
            }

            override suspend fun execute(event: MessageCreateEvent, ctx: CommandContext, args: MutableList<String>): Boolean {
                if (super.execute(event, ctx, args)) {
                    return true
                }
                val trick = TricksManager.globalTricks[ctx.cmd] ?: return false
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
                return true
            }
        }
        val trickHandler = TrickHandler(if (isDebug) "@@" else "!!")
        // register the commands
        registerCommands(commandManager)
        registerSlashCommands(slashCommands)
        CommandHandler(this, commandManager, LinkieThrowableHandler).register()
        CommandHandler(this, trickHandler, LinkieThrowableHandler).register()
        commandManager.registerToSlashCommands(slashCommands)
    }
}

fun cycle(time: TimeSpan, delay: TimeSpan = TimeSpan.ZERO, doThing: CoroutineScope.() -> Unit) {
    val cycleMs = time.millisecondsLong

    var nextDelay = getMillis() - cycleMs + delay.millisecondsLong
    CoroutineScope(Dispatchers.Default).launch {
        while (true) {
            if (getMillis() > nextDelay + cycleMs) {
                launch {
                    doThing()
                }
                nextDelay = getMillis()
            }
            delay(1000)
        }
    }
}

private operator fun File.div(s: String): File = File(this, s)

fun registerCommands(commands: CommandManager) {
    commands.registerCommand(QueryMappingsCommand(null, *MappingsEntryType.values()), "mapping")
    commands.registerCommand(false, QueryMappingsCommand(null, MappingsEntryType.CLASS), "c", "class")
    commands.registerCommand(false, QueryMappingsCommand(null, MappingsEntryType.METHOD), "m", "method")
    commands.registerCommand(false, QueryMappingsCommand(null, MappingsEntryType.FIELD), "f", "field")

    commands.registerCommand(false, QueryMappingsCommand(Namespaces["yarn"], *MappingsEntryType.values()), "y", "yarn")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["yarn"], MappingsEntryType.CLASS), "yc", "yarnc")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["yarn"], MappingsEntryType.METHOD), "ym", "yarnm")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["yarn"], MappingsEntryType.FIELD), "yf", "yarnf")

    commands.registerCommand(false, QueryMappingsCommand(Namespaces["legacy-yarn"], *MappingsEntryType.values()), "ly", "legacy-yarn")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["legacy-yarn"], MappingsEntryType.CLASS), "lyc", "legacy-yarnc")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["legacy-yarn"], MappingsEntryType.METHOD), "lym", "legacy-yarnm")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["legacy-yarn"], MappingsEntryType.FIELD), "lyf", "legacy-yarnf")

    commands.registerCommand(false, QueryMappingsCommand(Namespaces["yarrn"], *MappingsEntryType.values()), "yarrn")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["yarrn"], MappingsEntryType.CLASS), "yrc", "yarrnc")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["yarrn"], MappingsEntryType.METHOD), "yrm", "yarrnm")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["yarrn"], MappingsEntryType.FIELD), "yrf", "yarnrf")

    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mcp"], *MappingsEntryType.values()), "mcp")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mcp"], MappingsEntryType.CLASS), "mcpc")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mcp"], MappingsEntryType.METHOD), "mcpm")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mcp"], MappingsEntryType.FIELD), "mcpf")

    commands.registerCommand(false, MigrateMMCommandToRequiredCommand(), "mm", "mojmap", "mmc", "mojmapc", "mmm", "mojmapm", "mmf", "mojmapm")

    commands.registerCommand(false, QueryMappingsCommand(Namespaces["plasma"], *MappingsEntryType.values()), "plasma", "pl")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["plasma"], MappingsEntryType.CLASS), "plasmac", "plc")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["plasma"], MappingsEntryType.METHOD), "plasmam", "plm")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["plasma"], MappingsEntryType.FIELD), "plasmaf", "plf")

    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang"], *MappingsEntryType.values()), "mmi", "mojmapi")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang"], MappingsEntryType.CLASS), "mmic", "mojmapic")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang"], MappingsEntryType.METHOD), "mmim", "mojmapim")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang"], MappingsEntryType.FIELD), "mmif", "mojmapim")

    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang_srg"], *MappingsEntryType.values()), "mms", "mojmaps")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang_srg"], MappingsEntryType.CLASS), "mmsc", "mojmapsc")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang_srg"], MappingsEntryType.METHOD), "mmsm", "mojmapsm")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang_srg"], MappingsEntryType.FIELD), "mmsf", "mojmapsm")

    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang_hashed"], *MappingsEntryType.values()), "qh")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang_hashed"], MappingsEntryType.CLASS), "qhc")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang_hashed"], MappingsEntryType.METHOD), "qhm")
    commands.registerCommand(false, QueryMappingsCommand(Namespaces["mojang_hashed"], MappingsEntryType.FIELD), "qhf")

    commands.registerCommand(QueryTranslateMappingsCommand(null, null, *MappingsEntryType.values()), listOf("translate", "t"), listOf("translate"))
    commands.registerCommand(false, QueryTranslateMappingsCommand(null, null, MappingsEntryType.CLASS), "translatec", "tc")
    commands.registerCommand(false, QueryTranslateMappingsCommand(null, null, MappingsEntryType.METHOD), "translatem", "tm")
    commands.registerCommand(false, QueryTranslateMappingsCommand(null, null, MappingsEntryType.FIELD), "translatef", "tf")

    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mcp"], *MappingsEntryType.values()), "voldefy", "volde", "v", "ymcp")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mcp"], MappingsEntryType.CLASS), "voldefyc", "voldec", "vc", "ymcpc")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mcp"], MappingsEntryType.METHOD), "voldefym", "voldem", "vm", "ymcpm")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mcp"], MappingsEntryType.FIELD), "voldefyf", "voldef", "vf", "ymcpf")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["yarn"], *MappingsEntryType.values()), "devoldefy", "devolde", "dv", "mcpy")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["yarn"], MappingsEntryType.CLASS), "devoldefyc", "devoldec", "dvc", "mcpyc")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["yarn"], MappingsEntryType.METHOD), "devoldefym", "devoldem", "dvm", "mcpym")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["yarn"], MappingsEntryType.FIELD), "devoldefyf", "devoldef", "dvf", "mcpyf")

    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mojang"], *MappingsEntryType.values()), "ymm", "ymim", "ymsm")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mojang"], MappingsEntryType.CLASS), "ymmc", "ymmic", "ymmsc")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mojang"], MappingsEntryType.METHOD), "ymmm", "ymmim", "ymmsm")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mojang"], MappingsEntryType.FIELD), "ymmf", "ymmif", "ymmsf")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["yarn"], *MappingsEntryType.values()), "mmy", "mmiy", "mmsy")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["yarn"], MappingsEntryType.CLASS), "mmyc", "mmyic", "mmsyc")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["yarn"], MappingsEntryType.METHOD), "mmym", "mmyim", "mmsym")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["yarn"], MappingsEntryType.FIELD), "mmyf", "mmyif", "mmsyf")

    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang"], *MappingsEntryType.values()), "mcpmm", "mcpmim")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang"], MappingsEntryType.CLASS), "mcpmmc", "mcpmmic")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang"], MappingsEntryType.METHOD), "mcpmmm", "mcpmmim")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang"], MappingsEntryType.FIELD), "mcpmmf", "mcpmmif")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["mcp"], *MappingsEntryType.values()), "mmmcp", "mmimcp")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["mcp"], MappingsEntryType.CLASS), "mmmcpc", "mmimcpc")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["mcp"], MappingsEntryType.METHOD), "mmmcpm", "mmimcpm")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["mcp"], MappingsEntryType.FIELD), "mmmcpf", "mmimcpf")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang_srg"], *MappingsEntryType.values()), "mcpmsm")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang_srg"], MappingsEntryType.CLASS), "mcpmmsc")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang_srg"], MappingsEntryType.METHOD), "mcpmmsm")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang_srg"], MappingsEntryType.FIELD), "mcpmmsf")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang_srg"], Namespaces["mcp"], *MappingsEntryType.values()), "mmsmcp")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang_srg"], Namespaces["mcp"], MappingsEntryType.CLASS), "mmsmcpc")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang_srg"], Namespaces["mcp"], MappingsEntryType.METHOD), "mmsmcpm")
    commands.registerCommand(false, QueryTranslateMappingsCommand(Namespaces["mojang_srg"], Namespaces["mcp"], MappingsEntryType.FIELD), "mmsmcpf")

//    commands.registerCommand(RemapStackTraceCommand(MojangNamespace), "fabriccrash")
//    commands.registerCommand(RemapStackTraceCommand(MojangSrgNamespace), "forgecrash")
    commands.registerCommand(RemapAWATCommand, "remapaccess")

    commands.registerCommand(FabricDramaCommand, listOf("fabricdrama", "fdrama"), listOf("fabricdrama"))
    commands.registerCommand(FTBDramaCommand, listOf("ftbdrama", "drama"), listOf("ftbdrama"))
    commands.registerCommand(AboutCommand, "about")
    commands.registerCommand(RandomClassCommand, listOf("randc"), listOf("random_class"))
    commands.registerCommand(EvaluateCommand, listOf("eval", "evaluate"), listOf("evaluate"))
    commands.registerCommand(RunTrickCommand, "run")
    commands.registerCommand(false, AddTrickCommand, "trickadd")
    commands.registerCommand(false, RemoveTrickCommand, "trickremove")
    commands.registerCommand(false, ListTricksCommand, "listtricks")
    commands.registerCommand(false, ListAllTricksCommand, "listalltricks")
    commands.registerCommand(false, TrickInfoCommand, "trickinfo")
    commands.registerCommand(false, SetValueCommand, "value-set")
    commands.registerCommand(false, GetValueCommand, "value-get")
    commands.registerCommand(false, ValueListCommand, "value-list")
    commands.registerCommand(TricksCommand, "trick")
    commands.registerCommand(ValueCommand, "value")
    commands.registerCommand(FabricCommand, "fabric")
    commands.registerCommand(ForgeCommand, "forge")
}

fun registerSlashCommands(commands: SlashCommands) {
    commands.globalCommand("Base command for Linkie.") {
        cmd("linkie")
        sub("help", "Display the link to Linkie help.") {
            execute { command, ctx, options ->
                ctx.message.reply {
                    title("Linkie Help Command")
                    footer("Requested by " + ctx.user.discriminatedName, ctx.user.avatarUrl)
                    setTimestampToNow()
                    description("View the list of commands at https://github.com/linkie/linkie-discord/wiki/Commands")
                }
                true
            }
        }
    }
}
