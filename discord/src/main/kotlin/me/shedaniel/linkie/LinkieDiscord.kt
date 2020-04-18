@file:Suppress("ConstantConditionIf")

package me.shedaniel.linkie

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.Channel
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.guild.MemberLeaveEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.audio.LinkieMusic
import me.shedaniel.linkie.commands.*
import reactor.core.publisher.Mono
import java.time.Instant

val api: DiscordClient by lazy {
    DiscordClientBuilder(System.getenv("TOKEN")).build()
}
val isDebug: Boolean = System.getProperty("linkie-debug") == "true"
var commandApi: CommandApi = CommandApi(if (isDebug) "!!" else "!")
val music: Boolean = System.getProperty("linkie-music") != "false"
val commands: Boolean = System.getProperty("linkie-commands") != "false"

fun registerCommands(commandApi: CommandApi) {
    if (music) LinkieMusic.setupCommands(commandApi)
    if (commands) {
        commandApi.registerCommand(QueryClassMethod(null), "c", "class")
        commandApi.registerCommand(QueryClassMethod(Namespaces["yarn"]), "yc", "yarnc")
        commandApi.registerCommand(QueryClassMethod(Namespaces["pomf"]), "pomfc")
        commandApi.registerCommand(QueryClassMethod(Namespaces["mcp"]), "mcpc")
        commandApi.registerCommand(QueryTranslateClassCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefyc", "devoldec", "dvc")
        commandApi.registerCommand(QueryTranslateClassCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefyc", "voldec", "vc")
        commandApi.registerCommand(QueryMethodCommand(null), "m", "method")
        commandApi.registerCommand(QueryMethodCommand(Namespaces["yarn"]), "ym", "yarnm")
        commandApi.registerCommand(QueryMethodCommand(Namespaces["pomf"]), "pomfm")
        commandApi.registerCommand(QueryMethodCommand(Namespaces["mcp"]), "mcpm")
        commandApi.registerCommand(QueryTranslateMethodCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefym", "devoldem", "dvm")
        commandApi.registerCommand(QueryTranslateMethodCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefym", "voldem", "vm")
        commandApi.registerCommand(QueryFieldCommand(null), "f", "field")
        commandApi.registerCommand(QueryFieldCommand(Namespaces["yarn"]), "yf", "yarnf")
        commandApi.registerCommand(QueryFieldCommand(Namespaces["pomf"]), "pomff")
        commandApi.registerCommand(QueryFieldCommand(Namespaces["mcp"]), "mcpf")
        commandApi.registerCommand(QueryTranslateFieldCommand(Namespaces["mcp"], Namespaces["yarn"]), "devoldefyf", "devoldef", "dvf")
        commandApi.registerCommand(QueryTranslateFieldCommand(Namespaces["yarn"], Namespaces["mcp"]), "voldefyf", "voldef", "vf")
        commandApi.registerCommand(HelpCommand, "help", "?", "commands")
        commandApi.registerCommand(FabricApiVersionCommand, "fabricapi")
        commandApi.registerCommand(FabricDramaCommand, "fabricdrama", "fdrama")
        commandApi.registerCommand(FTBDramaCommand, "ftbdrama", "drama")
        commandApi.registerCommand(AboutCommand, "about")
        commandApi.registerCommand(CalculateLength, "calclen")
        commandApi.registerCommand(RandomClassCommand, "randc")
        commandApi.registerCommand(NamespacesCommand, "namespaces")
        commandApi.registerCommand(AWCommand, "allaccesswidener")
    }
}

fun start() {
    if (isDebug)
        println("Linkie Bot (Debug Mode)")
    else println("Linkie Bot")
    api.eventDispatcher.on(MessageCreateEvent::class.java).subscribe(commandApi::onMessageCreate)
    if (music) LinkieMusic.setupMusic()
    registerCommands(commandApi)
    Namespaces.startLoop()
    if (commands) {
        api.eventDispatcher.on(ReadyEvent::class.java).subscribe {
            api.updatePresence(Presence.online(Activity.playing("c o o l gamez"))).subscribe()
        }
        api.eventDispatcher.on(MemberJoinEvent::class.java).subscribe { event ->
            if (event.guildId.asLong() == 621271154019270675L)
                api.getChannelById(Snowflake.of(621298431855427615L)).filter { c -> c.type == Channel.Type.GUILD_TEXT }.subscribe { textChannel ->
                    val member = event.member
                    val guild = event.guild.block()
                    (textChannel as TextChannel).createMessage {
                        it.setEmbed {
                            it.setTitle("Welcome **${member.discriminatedName}**! #${guild?.memberCount?.asInt}")
                            it.setThumbnail(member.avatarUrl)
                            it.setTimestamp(Instant.now())
                            it.setDescription("Welcome ${member.discriminatedName} to `${guild?.name}`. \n\nEnjoy your stay!")
                        }
                    }.subscribe()
                }
            else if (event.guildId.asLong() == 432055962233470986L)
                api.getChannelById(Snowflake.of(432057546589601792L)).filter { c -> c.type == Channel.Type.GUILD_TEXT }.subscribe { textChannel ->
                    val member = event.member
                    val guild = event.guild.block()
                    (textChannel as TextChannel).createMessage {
                        it.setEmbed {
                            it.setTitle("Welcome **${member.discriminatedName}**! #${guild?.memberCount?.asInt}")
                            it.setThumbnail(member.avatarUrl)
                            it.setTimestampToNow()
                            it.setDescription("Welcome ${member.discriminatedName} to `${guild?.name}`. Get mod related support at <#576851123345031177>, <#582248149729411072>, <#593809520682205184> and <#576851701911388163>, and chat casually at <#432055962233470988>!\n" +
                                    "\n" +
                                    "Anyways, enjoy your stay!")
                        }
                    }.subscribe()
                }
        }
        api.eventDispatcher.on(MemberLeaveEvent::class.java).subscribe { event ->
            if (event.guildId.asLong() == 621271154019270675L)
                api.getChannelById(Snowflake.of(621298431855427615L)).filter { c -> c.type == Channel.Type.GUILD_TEXT }.subscribe send@{ textChannel ->
                    val member = event.member.orElse(null) ?: return@send
                    (textChannel as TextChannel).createMessage {
                        it.setEmbed {
                            it.setTitle("Goodbye **${member.discriminatedName}**! Farewell.")
                            it.setThumbnail(member.avatarUrl)
                            it.setTimestampToNow()
                        }
                    }.subscribe()
                }
            else if (event.guildId.asLong() == 432055962233470986L)
                api.getChannelById(Snowflake.of(432057546589601792L)).filter { c -> c.type == Channel.Type.GUILD_TEXT }.subscribe send@{ textChannel ->
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
    api.login().block()
}

val User.discriminatedName: String
    get() = "${username}#${discriminator}"

fun EmbedCreateSpec.setTimestampToNow(): EmbedCreateSpec =
        setTimestamp(Instant.now())

fun EmbedCreateSpec.addField(name: String, value: String): EmbedCreateSpec =
        addField(name, value, false)

fun EmbedCreateSpec.addInlineField(name: String, value: String): EmbedCreateSpec =
        addField(name, value, true)

fun Message.addReaction(unicode: String): Mono<Void> = addReaction(ReactionEmoji.unicode(unicode))
fun Message.subscribeReaction(unicode: String) {
    addReaction(unicode).subscribe()
}

fun Message.subscribeReactions(vararg unicodes: String) {
    if (unicodes.size <= 1) {
        unicodes.forEach(::subscribeReaction)
    } else {
        val list = unicodes.toMutableList()
        val first = list.first()
        var mono = addReaction(first)
        list.remove(first)
        for (s in list) {
            mono = mono.then(addReaction(s))
        }
        mono.subscribe()
    }
}