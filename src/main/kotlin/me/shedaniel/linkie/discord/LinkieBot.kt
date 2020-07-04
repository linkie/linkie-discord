@file:JvmName("LinkieBot")

package me.shedaniel.linkie.discord

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.guild.MemberLeaveEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.commands.*
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.namespaces.*
import java.io.File
import java.util.*

fun main() {
    File(File(System.getProperty("user.dir")), ".properties").apply {
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
    start(
            YarnNamespace,
            SpigotNamespace,
            PlasmaNamespace,
            MCPNamespace,
            MojangNamespace
    ) {
        registerCommands(CommandHandler)

        // This is only used for shedaniel's server, if you are hosting this yourself please remove this.
        registerWelcomeMessages()

        gateway.eventDispatcher.on(ReadyEvent::class.java).subscribe {
            gateway.updatePresence(Presence.online(Activity.watching("cool mappings"))).subscribe()
        }
    }
}

fun registerCommands(commands: CommandHandler) {
    commands.registerCommand(QueryClassMethod(null), "c", "class")
    commands.registerCommand(QueryClassMethod(Namespaces["yarn"]), "yc", "yarnc")
    commands.registerCommand(QueryClassMethod(Namespaces["plasma"]), "plasmac")
    commands.registerCommand(QueryClassMethod(Namespaces["mcp"]), "mcpc")
    commands.registerCommand(QueryTranslateClassCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefyc", "devoldec", "dvc")
    commands.registerCommand(QueryTranslateClassCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefyc", "voldec", "vc")
    commands.registerCommand(QueryMethodCommand(null), "m", "method")
    commands.registerCommand(QueryMethodCommand(Namespaces["yarn"]), "ym", "yarnm")
    commands.registerCommand(QueryMethodCommand(Namespaces["plasma"]), "plasmam")
    commands.registerCommand(QueryMethodCommand(Namespaces["mcp"]), "mcpm")
    commands.registerCommand(QueryTranslateMethodCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefym", "devoldem", "dvm")
    commands.registerCommand(QueryTranslateMethodCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefym", "voldem", "vm")
    commands.registerCommand(QueryFieldCommand(null), "f", "field")
    commands.registerCommand(QueryFieldCommand(Namespaces["yarn"]), "yf", "yarnf")
    commands.registerCommand(QueryFieldCommand(Namespaces["plasma"]), "plasmaf")
    commands.registerCommand(QueryFieldCommand(Namespaces["mcp"]), "mcpf")
    commands.registerCommand(QueryTranslateFieldCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefyf", "devoldef", "dvf")
    commands.registerCommand(QueryTranslateFieldCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefyf", "voldef", "vf")
    commands.registerCommand(HelpCommand, "help", "?", "commands")
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
}

private fun registerWelcomeMessages() {
    gateway.eventDispatcher.on(MemberJoinEvent::class.java).subscribe { event ->
        if (event.guildId.asLong() == 432055962233470986L) {
            gateway.getChannelById(Snowflake.of(432057546589601792L)).filter { c -> c.type == Channel.Type.GUILD_TEXT }.subscribe { textChannel ->
                val member = event.member
                val guild = event.guild.block()
                (textChannel as TextChannel).createMessage {
                    it.setEmbed {
                        it.setTitle("Welcome **${member.discriminatedName}**! #${guild?.memberCount}")
                        it.setThumbnail(member.avatarUrl)
                        it.setTimestampToNow()
                        it.setDescription("Welcome ${member.discriminatedName} to `${guild?.name}`. Get mod related support at <#576851123345031177>, <#582248149729411072>, <#593809520682205184> and <#576851701911388163>, and chat casually at <#432055962233470988>!\n" +
                                "\n" +
                                "Anyways, enjoy your stay!")
                    }
                }.subscribe()
            }
        }
    }
    gateway.eventDispatcher.on(MemberLeaveEvent::class.java).subscribe { event ->
        if (event.guildId.asLong() == 432055962233470986L) {
            gateway.getChannelById(Snowflake.of(432057546589601792L)).filter { c -> c.type == Channel.Type.GUILD_TEXT }.subscribe send@{ textChannel ->
                val member = event.member.orElse(null) ?: return@send
                (textChannel as TextChannel).createMessage {
                    it.setEmbed {
                        it.setTitle("Goodbye **${member.discriminatedName}**! Farewell.")
                        it.setThumbnail(member.avatarUrl)
                        it.setTimestampToNow()
                    }
                }.subscribe()
            }
        }
    }
}