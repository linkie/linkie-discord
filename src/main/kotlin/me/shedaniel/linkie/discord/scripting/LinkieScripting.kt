package me.shedaniel.linkie.discord.scripting

import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.AllowedMentions
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.tricks.ContentType
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.validateInGuild
import p0nki.pesl.api.PESLContext
import p0nki.pesl.api.PESLEvalException
import p0nki.pesl.api.`object`.MapLikeObject
import p0nki.pesl.api.`object`.PESLObject
import p0nki.pesl.api.parse.ASTNode
import p0nki.pesl.api.parse.PESLParseException
import p0nki.pesl.api.parse.PESLParser
import p0nki.pesl.api.token.PESLTokenList
import p0nki.pesl.api.token.PESLTokenizeException
import p0nki.pesl.api.token.PESLTokenizer
import kotlin.math.min

object LinkieScripting {
    private val tokenizer by lazy { PESLTokenizer() }
    private val parser by lazy { PESLParser() }
    val simpleContext = context {
        flatAdd(ContextExtensions.math)
        flatAdd(ContextExtensions.system)
        this["math"] = ContextExtensions.math
        this["system"] = ContextExtensions.system
        this["typeof"] = ContextExtensions.typeOf
        this["number"] = ContextExtensions.parseNumber
        this["parseNumber"] = ContextExtensions.parseNumber
        this["range"] = ContextExtensions.range
        this["dir"] = ContextExtensions.dir
        this["copy"] = ContextExtensions.copy
        this["equals"] = ContextExtensions.equals
        this["deepEquals"] = ContextExtensions.deepEquals
    }

    fun eval(context: PESLContext, cmd: String) {
        val tokens = parseTokens(cmd)
        while (tokens.hasAny()) {
            val node = parseExpression(cmd, tokens)
            evalNode(context, node)
        }
    }

    inline fun evalTrick(channel: MessageChannel, args: MutableList<String>, trick: Trick, crossinline context: () -> PESLContext) {
        when (trick.contentType) {
            ContentType.SCRIPT -> {
                eval(context(), trick.content)
            }
            ContentType.TEXT -> {
                channel.createMessage {
                    it.setAllowedMentions(AllowedMentions.builder().build())
                    it.setContent(trick.content.format(*args.toTypedArray()).let { it.substring(0, min(1999, it.length)) })
                }.subscribe()
            }
            else -> throw IllegalStateException("Invalid Script Type: ${trick.contentType}")
        }
    }

    fun validateTrickName(name: String) {
        if (!name.all { it == '_' || it == '-' || it in 'a'..'z' || it in '0'..'9' || it == '.' })
            throw IllegalArgumentException("$name is an illegal trick name, it contains non [a-z0-9_.-] character(s).")
    }

    fun validateGuild(event: MessageCreateEvent) {
        event.validateInGuild()
        require(ConfigManager[event.guildId.get().asLong()].tricksEnabled) { "Tricks are not enabled on this server." }
    }

    private fun parseTokens(cmd: String): PESLTokenList {
        try {
            return tokenizer.tokenize(cmd)
        } catch (e: PESLTokenizeException) {
            throw TokenizeException(cmd, e)
        }
    }

    private fun parseExpression(cmd: String, tokens: PESLTokenList): ASTNode {
        try {
            return parser.parseExpression(tokens)
        } catch (e: PESLParseException) {
            throw ParseException(cmd, e)
        }
    }

    private fun evalNode(context: PESLContext, node: ASTNode) {
        try {
            node.evaluate(context)
        } catch (e: PESLEvalException) {
            throw EvalException(e)
        }
    }
}

operator fun PESLContext.set(key: String, value: PESLObject) = let(key, value)
operator fun MapLikeObject.get(key: String) = getKey(key)

inline fun context(crossinline builder: PESLContext.() -> Unit): PESLContext {
    return PESLContext().clearAll().apply(builder)
}

fun PESLContext.clearAll(): PESLContext = apply {
    keys().forEach { this[it] = ContextExtensions.undefined() }
}

fun PESLContext.flatAdd(obj: PESLObject): PESLContext = apply {
    if (obj is MapLikeObject) {
        obj.keys().forEach { key ->
            this[key] = obj[key]
        }
    }
}

inline fun PESLContext.push(crossinline builder: PESLContext.() -> Unit): PESLContext {
    return push().apply(builder)
}

class EvalException(private val exception: PESLEvalException) : RuntimeException() {
    override val message: String?
        get() = exception.`object`.toString()
}

class TokenizeException(private val cmd: String, private val exception: PESLTokenizeException) : RuntimeException() {
    override val message: String?
        get() = buildString {
            append("${exception.localizedMessage}\n\n")
            append(cmd)
            append('\n')
            for (i in 1..exception.index) append(' ')
            append('^')
        }
}

class ParseException(private val cmd: String, private val exception: PESLParseException) : RuntimeException() {
    override val message: String?
        get() = buildString {
            append("${exception.localizedMessage}\n\n")
            append(cmd)
            append('\n')
            for (i in 1..exception.token.start) append(' ')
            for (i in exception.token.start until exception.token.end) append('^')
        }
}