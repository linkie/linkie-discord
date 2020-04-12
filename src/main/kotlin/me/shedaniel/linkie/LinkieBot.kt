@file:JvmName("LinkieBot")

package me.shedaniel.linkie

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Snowflake
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.shedaniel.linkie.utils.toVersion
import java.time.Duration

fun main() {
    YarnV2BlackList.loadData()
    GlobalScope.launch {
//        api.getGuildById(Snowflake.of(595505494408429598L)).subscribe { it.roles.subscribe { role ->
//            run {
//                println("${role.name} ${role.id.asLong()}")
//            }
//        } }
        delay(5000)
        if (System.getProperty("linkie-xml") != "true")
            return@launch
        api.getChannelById(Snowflake.of(637898458233176081L)).subscribe {
            if (it is TextChannel) {
                for (i in 1..69) {
                    it.createMessage { msg ->
                        if (i == 69)
                            msg.setContent("<@&637898602336878594> $i yes")
                        else msg.setContent("<@&637898602336878594> $i")
                    }.delaySubscription(Duration.ofSeconds(i.toLong())).subscribe()
                }
            }
        }
//        val version = "1.15.1"
//        val yarn = tryLoadYarnMappingContainer(version, null).third()
//        val mcp = tryLoadMCPMappingContainer(version, null).third()
//        val file = java.io.File(System.getProperty("user.dir"), "migrate/" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".xml")
//        file.parentFile.mkdirs()
//        val writer = file.printWriter()
//        writer.println("<?xml version=\"1.0\"?>\n\n<migrationMap>\n  <name value=\"MCP->Yarn (${mcp.version}-${mcpConfigSnapshots[mcp.version.toVersion()]?.max()} -&gt; ${yarnBuilds[yarn.version]?.version})\" />\n" +
//                "  <description value=\"This map is generated automatically.\" />")
//        mcp.classes.forEach { mcpClass ->
//            runCatching {
//                val obfName = mcpClass.obfName.merged ?: return@forEach
//                val yarnClass = yarn.getClassByObfName(obfName) ?: error("Can't find class: $obfName ${mcpClass.intermediaryName}!")
//                writer.println("  <entry oldName=\"${mcpClass.intermediaryName.replace('/', '.')}\" newName=\"${(yarnClass.mappedName ?: yarnClass.intermediaryName).replace('/', '.')}\" type=\"class\" />")
//            }
//        }
//        writer.println("</migrationMap>\n")
//        writer.close()
//        println("Done Generated XML!")
    }
    start()
}