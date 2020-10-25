@file:Suppress("ConstantConditionIf")

package me.shedaniel.linkie.discord

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.utils.info
import java.time.Duration
import java.util.*
import kotlin.concurrent.schedule
import kotlin.properties.Delegates

val api: DiscordClient by lazy {
    DiscordClientBuilder.create(System.getenv("TOKEN") ?: System.getProperty("linkie.token") ?: throw NullPointerException("Invalid Token: null")).build()
}
val isDebug: Boolean = System.getProperty("linkie-debug") == "true"
var commandMap: CommandMap = CommandMap(CommandHandler, if (isDebug) "@" else "!")
var trickMap: CommandMap = CommandMap(TrickHandler, if (isDebug) "@@" else "!!")
var gateway by Delegates.notNull<GatewayDiscordClient>()

inline fun start(
    vararg namespaces: Namespace,
    cycleMs: Long = 1800000,
    crossinline setup: () -> Unit
) {
    if (isDebug)
        info("Linkie Bot (Debug Mode)")
    else info("Linkie Bot")
    Timer().schedule(0, Duration.ofMinutes(1).toMillis()) { System.gc() }
//    Timer().schedule(0, Duration.ofSeconds(1).toMillis()) {
//        trace(String.format("Total: %s, Free: %s",
//                Runtime.getRuntime().totalMemory(),
//                Runtime.getRuntime().freeMemory()))
//    }
    gateway = api.login().block()!!
    Namespaces.init(*namespaces, cycleMs = cycleMs)
    setup()
    gateway.eventDispatcher.on(MessageCreateEvent::class.java).subscribe(commandMap::onMessageCreate)
    gateway.eventDispatcher.on(MessageCreateEvent::class.java).subscribe(trickMap::onMessageCreate)
}