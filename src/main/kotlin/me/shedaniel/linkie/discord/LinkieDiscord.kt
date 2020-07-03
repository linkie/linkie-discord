@file:Suppress("ConstantConditionIf")

package me.shedaniel.linkie.discord

import discord4j.common.util.Snowflake
import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.utils.info
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.concurrent.schedule
import kotlin.properties.Delegates

val api: DiscordClient by lazy {
    DiscordClientBuilder.create(System.getenv("TOKEN") ?: throw NullPointerException("Invalid Token: null")).build()
}
val isDebug: Boolean = System.getProperty("linkie-debug") == "true"
var commandApi: CommandApi = CommandApi(if (isDebug) "!!" else "!")
var gateway by Delegates.notNull<GatewayDiscordClient>()

inline fun start(
        vararg namespaces: Namespace,
        cycleMs: Long = 1800000,
        crossinline setup: (CommandApi, DiscordClient) -> Unit
) {
    if (isDebug)
        info("Linkie Bot (Debug Mode)")
    else info("Linkie Bot")
    Timer().schedule(Duration.ofMinutes(5).toMillis()) { System.gc() }
    gateway = api.login().block()!!
    Namespaces.init(*namespaces, cycleMs = cycleMs)
    setup(commandApi, api)
    gateway.eventDispatcher.on(MessageCreateEvent::class.java).subscribe(commandApi::onMessageCreate)
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

inline fun Message?.editOrCreate(channel: MessageChannel, crossinline createSpec: EmbedCreateSpec.() -> Unit): Mono<Message> {
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

fun Message.tryRemoveReaction(emoji: ReactionEmoji, userId: Snowflake) {
    channel.filter { it.type != Channel.Type.DM }.subscribe { removeReaction(emoji, userId).subscribe() }
}

fun Message.tryRemoveAllReactions(): Mono<Void> {
    return channel.filter { it.type != Channel.Type.DM }.flatMap { removeAllReactions() }
}

inline fun buildReactions(duration: Duration = Duration.ofMinutes(10), crossinline builder: ReactionBuilder.() -> Unit): ReactionBuilder {
    val reactionBuilder = ReactionBuilder(duration)
    builder(reactionBuilder)
    return reactionBuilder
}

fun MessageChannel.createEmbedMessage(spec: EmbedCreateSpec.() -> Unit): Mono<Message> = createEmbed(spec)

class ReactionBuilder(val duration: Duration = Duration.ofMinutes(10)) {
    private val actions = mutableMapOf<String, () -> Boolean>()

    fun registerB(unicode: String, action: () -> Boolean) {
        actions[unicode] = action
    }

    fun register(unicode: String, action: () -> Unit) {
        actions[unicode] = { action(); true }
    }

    fun build(message: Message, user: User) {
        build(message) { it == user.id }
    }

    fun build(message: Message, userPredicate: (Snowflake) -> Boolean) {
        message.subscribeReactions(*actions.keys.toTypedArray())
        gateway.eventDispatcher.on(ReactionAddEvent::class.java).filter { e -> e.messageId == message.id }.take(duration).subscribe {
            if (userPredicate(it.userId)) {
                val emote = it.emoji.asUnicodeEmoji().map(ReactionEmoji.Unicode::getRaw).orElse(null)
                val action = actions[emote]
                if (action == null || action()) {
                    message.tryRemoveReaction(it.emoji, it.userId)
                }
            } else if (it.userId != gateway.selfId) {
                message.tryRemoveReaction(it.emoji, it.userId)
            }
        }
    }
}