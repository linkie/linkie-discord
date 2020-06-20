@file:Suppress("ConstantConditionIf")

package me.shedaniel.linkie.discord

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import reactor.core.publisher.Mono
import java.time.Instant

val api: DiscordClient by lazy {
    DiscordClientBuilder(System.getenv("TOKEN")).build()
}
val isDebug: Boolean = System.getProperty("linkie-debug") == "true"
var commandApi: CommandApi = CommandApi(if (isDebug) "!!" else "!")

inline fun start(
        vararg namespaces: Namespace,
        cycleMs: Long = 1800000,
        crossinline setup: (CommandApi, DiscordClient) -> Unit
) {
    if (isDebug)
        println("Linkie Bot (Debug Mode)")
    else println("Linkie Bot")
    api.eventDispatcher.on(MessageCreateEvent::class.java).subscribe(commandApi::onMessageCreate)
    Namespaces.init(*namespaces, cycleMs = cycleMs)
    setup(commandApi, api)
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

inline fun Message?.editOrCreate(channel: MessageChannel, crossinline createSpec: (EmbedCreateSpec) -> Unit): Mono<Message> {
    return if (this == null) {
        channel.createMessage {
            it.setEmbed { createSpec(it) }
        }
    } else {
        edit {
            it.setEmbed { createSpec(it) }
        }
    } ?: throw NullPointerException("Unknown Message!")
}