package me.shedaniel.linkie.discord.utils

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec
import discord4j.rest.util.AllowedMentions
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Obf
import me.shedaniel.linkie.discord.gateway
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

fun <T> Optional<T>.getOrNull(): T? = orElse(null)
fun OptionalInt.getOrNull(): Int? = if (isPresent) asInt else null
fun OptionalLong.getOrNull(): Long? = if (isPresent) asLong else null
fun OptionalDouble.getOrNull(): Double? = if (isPresent) asDouble else null

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

fun AtomicReference<Message?>.editOrCreate(channel: MessageChannel, previous: Message? = null, createSpec: EmbedCreateSpec.() -> Unit): Mono<Message> {
    return if (get() == null) {
        channel.sendEmbedMessage(previous, createSpec).doOnSuccess { set(it) }
    } else {
        get()!!.edit {
            it.setEmbed { createSpec(it) }
        }.doOnSuccess { set(it) }
    }
}

fun Message.tryRemoveReaction(emoji: ReactionEmoji, userId: Snowflake) {
    channel.filter { it.type != Channel.Type.DM }.doOnError { }.subscribe { removeReaction(emoji, userId).subscribe() }
}

fun Message.tryRemoveAllReactions(): Mono<Void> {
    return channel.filter { it.type != Channel.Type.DM }.flatMap { removeAllReactions() }.doOnError { }
}

fun Obf.buildString(nonEmptySuffix: String? = null): String =
    when {
        isEmpty() -> ""
        isMerged() -> merged!! + (nonEmptySuffix ?: "")
        else -> buildString {
            if (client != null) append("client=**$client**")
            if (server != null) append("server=**$server**")
            if (nonEmptySuffix != null) append(nonEmptySuffix)
        }
    }

fun String?.suffixIfNotNull(suffix: String): String? =
    mapIfNotNull { it + suffix }

inline fun String?.mapIfNotNull(mapper: (String) -> String): String? =
    when {
        isNullOrEmpty() -> this
        else -> mapper(this)
    }

inline fun String?.mapIfNotNullOrNotEquals(other: String, mapper: (String) -> String): String? =
    when {
        isNullOrEmpty() -> null
        this == other -> null
        else -> mapper(this)
    }

inline fun buildReactions(duration: Duration = Duration.ofMinutes(10), builder: ReactionBuilder.() -> Unit): ReactionBuilder {
    val reactionBuilder = ReactionBuilder(duration)
    builder(reactionBuilder)
    return reactionBuilder
}

private val noAllowedMentions = AllowedMentions.builder().build()

fun MessageChannel.sendMessage(spec: (MessageCreateSpec) -> Unit): Mono<Message> = createMessage {
    spec(it)
    it.setAllowedMentions(noAllowedMentions)
}

fun MessageChannel.sendMessage(content: String): Mono<Message> = sendMessage { 
    it.content = content
}

var MessageCreateSpec.content: String
    set(value) {
        setContent(value.substring(0, min(value.length, 2000)))
    }
    get() = throw UnsupportedOperationException()

var EmbedCreateSpec.description: String
    set(value) {
        setDescription(value.substring(0, min(value.length, 2000)))
    }
    get() = throw UnsupportedOperationException()

fun MessageChannel.sendEmbedMessage(message: Message? = null, spec: EmbedCreateSpec.() -> Unit): Mono<Message> = sendMessage {
    it.setEmbed(spec)
    message?.let { message ->
        it.setMessageReference(message.id)
    }
}

fun EmbedCreateSpec.setSafeDescription(description: String) {
    this.description = description.substring(0, min(description.length, 2000))
}

inline fun EmbedCreateSpec.buildSafeDescription(builderAction: StringBuilder.() -> Unit) {
    setSafeDescription(buildString(builderAction))
}

val Class.optimumName: String
    get() = mappedName ?: intermediaryName

val Field.optimumName: String
    get() = mappedName ?: intermediaryName

val Method.optimumName: String
    get() = mappedName ?: intermediaryName

fun String.isValidIdentifier(): Boolean {
    forEachIndexed { index, c ->
        if (index == 0) {
            if (!Character.isJavaIdentifierStart(c))
                return false
        } else {
            if (!Character.isJavaIdentifierPart(c))
                return false
        }
    }
    return isNotEmpty()
}

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