@file:Suppress("ConstantConditionIf")

package me.shedaniel.linkie.discord

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.*
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
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

val api: DiscordClient by lazy {
    DiscordClientBuilder(System.getenv("TOKEN") ?: throw NullPointerException("Invalid Token: null")).build()
}
val isDebug: Boolean = System.getProperty("linkie-debug") == "true"
var commandApi: CommandApi = CommandApi(if (isDebug) "!!" else "!")

inline fun start(
        vararg namespaces: Namespace,
        cycleMs: Long = 1800000,
        crossinline setup: (CommandApi, DiscordClient) -> Unit
) {
    if (isDebug)
        info("Linkie Bot (Debug Mode)")
    else info("Linkie Bot")
    Timer().schedule(Duration.ofMinutes(5).toMillis()) { System.gc() }
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

fun String.stripMentions(messageChannel: MessageChannel, guild: Guild?): String {
    var message = this
    message = message.replace("@everyone", "@\u0435veryone")
    message = message.replace("@here", "@h\u0435re")
    if (guild != null) guild.apply{
        roles.subscribe { role ->
            message = message.replace(role.mention, "@${role.name}")
        }
        members.subscribe { user ->
            message = message.replace("<@${user.id.asString()}>", "@${user.username}")
            message = message.replace("<@!${user.id.asString()}>", "@${user.username}")
        }
    } else if (messageChannel is PrivateChannel) messageChannel.apply {
        recipients.subscribe { user ->
            message = message.replace("<@${user.id.asString()}>", "@${user.username}")
            message = message.replace("<@!${user.id.asString()}>", "@${user.username}")
        }
    }
    return message
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
        api.eventDispatcher.on(ReactionAddEvent::class.java).filter { e -> e.messageId == message.id }.take(duration).subscribe {
            if (userPredicate(it.userId)) {
                val emote = it.emoji.asUnicodeEmoji().map(ReactionEmoji.Unicode::getRaw).orElse(null)
                val action = actions[emote]
                if (action == null || action()) {
                    message.tryRemoveReaction(it.emoji, it.userId)
                }
            } else if (it.userId != api.selfId.get()) {
                message.tryRemoveReaction(it.emoji, it.userId)
            }
        }
    }
}