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
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.commands.AWCommand
import me.shedaniel.linkie.discord.commands.AboutCommand
import me.shedaniel.linkie.discord.commands.AddTrickCommand
import me.shedaniel.linkie.discord.commands.EvaluateCommand
import me.shedaniel.linkie.discord.commands.FTBDramaCommand
import me.shedaniel.linkie.discord.commands.FabricCommand
import me.shedaniel.linkie.discord.commands.FabricDramaCommand
import me.shedaniel.linkie.discord.commands.ForgeCommand
import me.shedaniel.linkie.discord.commands.GetValueCommand
import me.shedaniel.linkie.discord.commands.GoogleCommand
import me.shedaniel.linkie.discord.commands.HelpCommand
import me.shedaniel.linkie.discord.commands.ListAllTricksCommand
import me.shedaniel.linkie.discord.commands.ListTricksCommand
import me.shedaniel.linkie.discord.commands.ListenerCommand
import me.shedaniel.linkie.discord.commands.NamespacesCommand
import me.shedaniel.linkie.discord.commands.QueryMappingsCommand
import me.shedaniel.linkie.discord.commands.QueryTranslateMappingsCommand
import me.shedaniel.linkie.discord.commands.RandomClassCommand
import me.shedaniel.linkie.discord.commands.RemapAWATCommand
import me.shedaniel.linkie.discord.commands.RemoveTrickCommand
import me.shedaniel.linkie.discord.commands.RunTrickCommand
import me.shedaniel.linkie.discord.commands.SetValueCommand
import me.shedaniel.linkie.discord.commands.TrickInfoCommand
import me.shedaniel.linkie.discord.commands.TricksCommand
import me.shedaniel.linkie.discord.commands.ValueCommand
import me.shedaniel.linkie.discord.commands.ValueListCommand
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.listener.ChannelListeners
import me.shedaniel.linkie.discord.listener.listeners.ArchitecturyVersionListener
import me.shedaniel.linkie.discord.listener.listeners.FabricMCVersionListener
import me.shedaniel.linkie.discord.listener.listeners.MinecraftVersionListener
import me.shedaniel.linkie.discord.listener.listeners.ModMenuVersionListener
import me.shedaniel.linkie.discord.listener.listeners.ShedanielVersionListener
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.event
import me.shedaniel.linkie.namespaces.LegacyYarnNamespace
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.MojangNamespace
import me.shedaniel.linkie.namespaces.MojangSrgNamespace
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
                MojangNamespace,
                MojangSrgNamespace,
            )
        )
    ) {
//        val slashCommands = SlashCommands()
        // register the commands
        registerCommands(CommandHandler)
        registerListeners(ChannelListeners)
//        registerSlashCommands(slashCommands)
//        slashCommands.register()

        event<ReadyEvent> {
            gateway.updatePresence(Presence.online(Activity.watching("cool mappings"))).subscribe()
        }
    }
    ChannelListeners.init()
}

private operator fun File.div(s: String): File = File(this, s)

fun registerCommands(commands: CommandHandler) {
    commands.registerCommand(QueryMappingsCommand(null, *MappingsEntryType.values()), "mapping")
    commands.registerCommand(QueryMappingsCommand(null, MappingsEntryType.CLASS), "c", "class")
    commands.registerCommand(QueryMappingsCommand(null, MappingsEntryType.METHOD), "m", "method")
    commands.registerCommand(QueryMappingsCommand(null, MappingsEntryType.FIELD), "f", "field")

    commands.registerCommand(QueryMappingsCommand(Namespaces["yarn"], *MappingsEntryType.values()), "y", "yarn")
    commands.registerCommand(QueryMappingsCommand(Namespaces["yarn"], MappingsEntryType.CLASS), "yc", "yarnc")
    commands.registerCommand(QueryMappingsCommand(Namespaces["yarn"], MappingsEntryType.METHOD), "ym", "yarnm")
    commands.registerCommand(QueryMappingsCommand(Namespaces["yarn"], MappingsEntryType.FIELD), "yf", "yarnf")

    commands.registerCommand(QueryMappingsCommand(Namespaces["legacy-yarn"], *MappingsEntryType.values()), "ly", "legacy-yarn")
    commands.registerCommand(QueryMappingsCommand(Namespaces["legacy-yarn"], MappingsEntryType.CLASS), "lyc", "legacy-yarnc")
    commands.registerCommand(QueryMappingsCommand(Namespaces["legacy-yarn"], MappingsEntryType.METHOD), "lym", "legacy-yarnm")
    commands.registerCommand(QueryMappingsCommand(Namespaces["legacy-yarn"], MappingsEntryType.FIELD), "lyf", "legacy-yarnf")

    commands.registerCommand(QueryMappingsCommand(Namespaces["yarrn"], *MappingsEntryType.values()), "yarrn")
    commands.registerCommand(QueryMappingsCommand(Namespaces["yarrn"], MappingsEntryType.CLASS), "yrc", "yarrnc")
    commands.registerCommand(QueryMappingsCommand(Namespaces["yarrn"], MappingsEntryType.METHOD), "yrm", "yarrnm")
    commands.registerCommand(QueryMappingsCommand(Namespaces["yarrn"], MappingsEntryType.FIELD), "yrf", "yarnrf")

    commands.registerCommand(QueryMappingsCommand(Namespaces["mcp"], *MappingsEntryType.values()), "mcp")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mcp"], MappingsEntryType.CLASS), "mcpc")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mcp"], MappingsEntryType.METHOD), "mcpm")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mcp"], MappingsEntryType.FIELD), "mcpf")

    commands.registerCommand(QueryMappingsCommand(Namespaces["mojang"], *MappingsEntryType.values()), "mm", "mojmap")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mojang"], MappingsEntryType.CLASS), "mmc", "mojmapc")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mojang"], MappingsEntryType.METHOD), "mmm", "mojmapm")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mojang"], MappingsEntryType.FIELD), "mmf", "mojmapm")

    commands.registerCommand(QueryMappingsCommand(Namespaces["plasma"], *MappingsEntryType.values()), "plasma")
    commands.registerCommand(QueryMappingsCommand(Namespaces["plasma"], MappingsEntryType.CLASS), "plasmac")
    commands.registerCommand(QueryMappingsCommand(Namespaces["plasma"], MappingsEntryType.METHOD), "plasmam")
    commands.registerCommand(QueryMappingsCommand(Namespaces["plasma"], MappingsEntryType.FIELD), "plasmaf")

    commands.registerCommand(QueryMappingsCommand(Namespaces["mojang_srg"], *MappingsEntryType.values()), "mms", "mojmaps")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mojang_srg"], MappingsEntryType.CLASS), "mmsc", "mojmapsc")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mojang_srg"], MappingsEntryType.METHOD), "mmsm", "mojmapsm")
    commands.registerCommand(QueryMappingsCommand(Namespaces["mojang_srg"], MappingsEntryType.FIELD), "mmsf", "mojmapsm")

    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mcp"], *MappingsEntryType.values()), "voldefy", "volde", "v", "ymcp")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mcp"], MappingsEntryType.CLASS), "voldefyc", "voldec", "vc", "ymcpc")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mcp"], MappingsEntryType.METHOD), "voldefym", "voldem", "vm", "ymcpm")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mcp"], MappingsEntryType.FIELD), "voldefyf", "voldef", "vf", "ymcpf")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["yarn"], *MappingsEntryType.values()), "devoldefy", "devolde", "dv", "mcpy")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["yarn"], MappingsEntryType.CLASS), "devoldefyc", "devoldec", "dvc", "mcpyc")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["yarn"], MappingsEntryType.METHOD), "devoldefym", "devoldem", "dvm", "mcpym")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["yarn"], MappingsEntryType.FIELD), "devoldefyf", "devoldef", "dvf", "mcpyf")

    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mojang"], *MappingsEntryType.values()), "ymm")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mojang"], MappingsEntryType.CLASS), "ymmc")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mojang"], MappingsEntryType.METHOD), "ymmm")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["yarn"], Namespaces["mojang"], MappingsEntryType.FIELD), "ymmf")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["yarn"], *MappingsEntryType.values()), "mmy")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["yarn"], MappingsEntryType.CLASS), "mmyc")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["yarn"], MappingsEntryType.METHOD), "mmym")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["yarn"], MappingsEntryType.FIELD), "mmyf")

    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang"], *MappingsEntryType.values()), "mcpmm")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang"], MappingsEntryType.CLASS), "mcpmmc")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang"], MappingsEntryType.METHOD), "mcpmmm")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mcp"], Namespaces["mojang"], MappingsEntryType.FIELD), "mcpmmf")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["mcp"], *MappingsEntryType.values()), "mmmcp")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["mcp"], MappingsEntryType.CLASS), "mmmcpc")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["mcp"], MappingsEntryType.METHOD), "mmmcpm")
    commands.registerCommand(QueryTranslateMappingsCommand(Namespaces["mojang"], Namespaces["mcp"], MappingsEntryType.FIELD), "mmmcpf")

    commands.registerCommand(RemapAWATCommand, "remapaccess")

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
    commands.registerCommand(ListenerCommand, "listener")
}

fun registerListeners(listeners: ChannelListeners) {
    listeners["minecraft"] = MinecraftVersionListener
    listeners["fabricmc"] = FabricMCVersionListener
    listeners["architectury"] = ArchitecturyVersionListener
    listeners["shedaniel"] = ShedanielVersionListener
    listeners["modmenu"] = ModMenuVersionListener
}

/*
fun registerSlashCommands(commands: SlashCommands) {
    commands.guildCommand(432055962233470986L, "linkie", "Base command for Linkie.") {
        sub("help", "Display the link to Linkie help.").execute { command, cmd, interaction ->
            interaction.reply {
                setTitle("Linkie Help Command")
                setFooter("Requested by " + interaction.userDiscriminatedName, interaction.userAvatarUrl)
                setTimestampToNow()
                description = "View the list of commands at https://github.com/linkie/linkie-discord/wiki/Commands"
            }
        }
    }

    commands.guildCommand(432055962233470986L, "mappings", "Query mappings") {
        Namespaces.namespaces.forEach { (namespaceId, namespace) ->
            subGroup(namespaceId, "Query mappings from $namespaceId.") {
                sub("class", "Query classes from $namespaceId.")
                    .string("query", "The query filter")
                    .string("version", "The version the mappings should target") { required(false) }
                    .execute { command, cmd, interaction -> interaction.reply("LOL") }
                sub("field", "Query fields from $namespaceId.")
                    .string("query", "The query filter")
                    .string("version", "The version the mappings should target") { required(false) }
                    .execute { command, cmd, interaction -> interaction.acknowledge() }
                sub("method", "Query methods from $namespaceId.")
                    .string("query", "The query filter")
                    .string("version", "The version the mappings should target") { required(false) }
                    .execute { command, cmd, interaction -> interaction.acknowledge() }
//                this.string("query", "The query filter")
//                    .string("version", "The version the mappings should target") { required(false) }
//                    .execute { command, cmd, interaction -> interaction.acknowledge() }
            }
        }
    }
}
*/
