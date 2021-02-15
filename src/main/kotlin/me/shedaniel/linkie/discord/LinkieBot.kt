/*
 * Copyright (c) 2019, 2020 shedaniel
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

import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import discord4j.core.event.domain.lifecycle.ReadyEvent
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.shedaniel.linkie.LinkieConfig
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.commands.*
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.event
import me.shedaniel.linkie.namespaces.LegacyYarnNamespace
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.MojangNamespace
import me.shedaniel.linkie.namespaces.PlasmaNamespace
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.namespaces.YarrnNamespace
import java.io.File
import java.util.*

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
                LegacyYarnNamespace,
                YarrnNamespace,
                YarnNamespace,
                PlasmaNamespace,
                MCPNamespace,
                MojangNamespace
            )
        )
    ) {
        // register the commands
        registerCommands(CommandHandler)

        event<ReadyEvent> {
            gateway.updatePresence(Presence.online(Activity.watching("cool mappings"))).subscribe()
        }
    }
}

private operator fun File.div(s: String): File = File(this, s)

fun registerCommands(commands: CommandHandler) {
    commands.registerCommand(QueryCompoundCommand(null), "mapping")
    commands.registerCommand(QueryClassCommand(null), "c", "class")
    commands.registerCommand(QueryMethodCommand(null), "m", "method")
    commands.registerCommand(QueryFieldCommand(null), "f", "field")

    commands.registerCommand(QueryCompoundCommand(Namespaces["yarn"]), "y", "yarn")
    commands.registerCommand(QueryClassCommand(Namespaces["yarn"]), "yc", "yarnc")
    commands.registerCommand(QueryMethodCommand(Namespaces["yarn"]), "ym", "yarnm")
    commands.registerCommand(QueryFieldCommand(Namespaces["yarn"]), "yf", "yarnf")

    commands.registerCommand(QueryCompoundCommand(Namespaces["legacy-yarn"]), "ly", "legacy-yarn")
    commands.registerCommand(QueryClassCommand(Namespaces["legacy-yarn"]), "lyc", "legacy-yarnc")
    commands.registerCommand(QueryMethodCommand(Namespaces["legacy-yarn"]), "lym", "legacy-yarnm")
    commands.registerCommand(QueryFieldCommand(Namespaces["legacy-yarn"]), "lyf", "legacy-yarnf")

    commands.registerCommand(QueryCompoundCommand(Namespaces["yarrn"]), "yarrn")
    commands.registerCommand(QueryClassCommand(Namespaces["yarrn"]), "yrc", "yarrnc")
    commands.registerCommand(QueryMethodCommand(Namespaces["yarrn"]), "yrm", "yarrnm")
    commands.registerCommand(QueryFieldCommand(Namespaces["yarrn"]), "yrf", "yarnrf")

    commands.registerCommand(QueryCompoundCommand(Namespaces["mcp"]), "mcp")
    commands.registerCommand(QueryClassCommand(Namespaces["mcp"]), "mcpc")
    commands.registerCommand(QueryMethodCommand(Namespaces["mcp"]), "mcpm")
    commands.registerCommand(QueryFieldCommand(Namespaces["mcp"]), "mcpf")

    commands.registerCommand(QueryCompoundCommand(Namespaces["mojang"]), "mm", "mojmap")
    commands.registerCommand(QueryClassCommand(Namespaces["mojang"]), "mmc", "mojmapc")
    commands.registerCommand(QueryMethodCommand(Namespaces["mojang"]), "mmm", "mojmapm")
    commands.registerCommand(QueryFieldCommand(Namespaces["mojang"]), "mmf", "mojmapm")

    commands.registerCommand(QueryCompoundCommand(Namespaces["plasma"]), "plasma")
    commands.registerCommand(QueryClassCommand(Namespaces["plasma"]), "plasmac")
    commands.registerCommand(QueryMethodCommand(Namespaces["plasma"]), "plasmam")
    commands.registerCommand(QueryFieldCommand(Namespaces["plasma"]), "plasmaf")

    commands.registerCommand(QueryTranslateClassCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefyc", "voldec", "vc")
    commands.registerCommand(QueryTranslateMethodCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefym", "voldem", "vm")
    commands.registerCommand(QueryTranslateFieldCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefyf", "voldef", "vf")
    commands.registerCommand(QueryTranslateClassCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefyc", "devoldec", "dvc")
    commands.registerCommand(QueryTranslateMethodCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefym", "devoldem", "dvm")
    commands.registerCommand(QueryTranslateFieldCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefyf", "devoldef", "dvf")

    commands.registerCommand(QueryTranslateClassCommand(Namespaces["yarn"], Namespaces["mojang"]), "ymmc")
    commands.registerCommand(QueryTranslateMethodCommand(Namespaces["yarn"], Namespaces["mojang"]), "ymmm")
    commands.registerCommand(QueryTranslateFieldCommand(Namespaces["yarn"], Namespaces["mojang"]), "ymmf")
    commands.registerCommand(QueryTranslateClassCommand(Namespaces["mojang"], Namespaces["yarn"]), "mmyc")
    commands.registerCommand(QueryTranslateMethodCommand(Namespaces["mojang"], Namespaces["yarn"]), "mmym")
    commands.registerCommand(QueryTranslateFieldCommand(Namespaces["mojang"], Namespaces["yarn"]), "mmyf")

    commands.registerCommand(QueryTranslateClassCommand(Namespaces["mcp"], Namespaces["mojang"]), "mcpmmc")
    commands.registerCommand(QueryTranslateMethodCommand(Namespaces["mcp"], Namespaces["mojang"]), "mcpmmm")
    commands.registerCommand(QueryTranslateFieldCommand(Namespaces["mcp"], Namespaces["mojang"]), "mcpmmf")
    commands.registerCommand(QueryTranslateClassCommand(Namespaces["mojang"], Namespaces["mcp"]), "mmmcpc")
    commands.registerCommand(QueryTranslateMethodCommand(Namespaces["mojang"], Namespaces["mcp"]), "mmmcpm")
    commands.registerCommand(QueryTranslateFieldCommand(Namespaces["mojang"], Namespaces["mcp"]), "mmmcpf")

    commands.registerCommand(HelpCommand, "help", "commands")
    commands.registerCommand(FabricDramaCommand, "fabricdrama", "fdrama")
    commands.registerCommand(FTBDramaCommand, "ftbdrama", "drama")
    commands.registerCommand(AboutCommand, "about")
    commands.registerCommand(RandomClassCommand, "randc")
    commands.registerCommand(NamespacesCommand, "namespaces")
    commands.registerCommand(AWCommand, "allaccesswidener")
    commands.registerCommand(EvaluateCommand, "eval", "evaluate")
    commands.registerCommand(RunTrickCommand, "run")
    commands.registerCommand(AddTrickCommand, "trickadd")
    commands.registerCommand(RemoveTrickCommand, "trickremove")
    commands.registerCommand(ListTricksCommand, "listtricks")
    commands.registerCommand(ListAllTricksCommand, "listalltricks")
    commands.registerCommand(TrickInfoCommand, "trickinfo")
    commands.registerCommand(SetValueCommand, "value-set")
    commands.registerCommand(GetValueCommand, "value-get")
    commands.registerCommand(ValueListCommand, "value-list")
    commands.registerCommand(TricksCommand, "trick")
    commands.registerCommand(ValueCommand, "value")
    commands.registerCommand(FabricCommand, "fabric")
    commands.registerCommand(ForgeCommand, "forge")
    commands.registerCommand(GoogleCommand, "google")
}