package me.shedaniel.linkie.discord.scripting

import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.AllowedMentions
import kotlinx.coroutines.*
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.tricks.ContentType
import me.shedaniel.linkie.discord.tricks.Trick
import me.shedaniel.linkie.discord.validateInGuild
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject
import kotlin.math.min

object LinkieScripting {
    val simpleContext = context {
        flatAdd(ContextExtensions.math)
        flatAdd(ContextExtensions.system)
        this["math"] = ContextExtensions.math
        this["system"] = ContextExtensions.system
        this["number"] = ContextExtensions.parseNumber
        this["parseNumber"] = ContextExtensions.parseNumber
        this["range"] = ContextExtensions.range
        this["equals"] = ContextExtensions.equals
    }

    inline fun evalTrick(channel: MessageChannel, args: MutableList<String>, trick: Trick, crossinline context: () -> ScriptingContext) {
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

    fun eval(context: ScriptingContext, script: String) {
        try {
            var t: Throwable? = null
            runBlocking {
                withTimeout(3000) {
                    GlobalScope.launch(Dispatchers.IO) {
                        val engine = Context.newBuilder("js")
                            .allowExperimentalOptions(true)
                            .allowHostAccess(HostAccess.NONE)
//                            .option("js.console", "false")
                            .option("js.nashorn-compat", "true")
                            .build()
                        try {
                            engine.getBindings("js").also {
                                context.applyTo(it)
                            }
                            engine.eval("js", script)
                        } catch (throwable: Throwable) {
                            t = throwable
                        }
                    }.join()
                }
            }
            t?.let { throw it }
        } catch (throwable: Throwable) {
            throw throwable
        }
    }
}

inline fun context(name: String? = null, crossinline builder: ScriptingContext.() -> Unit): ScriptingContext {
    return ScriptingContext(name).apply(builder)
}

inline fun ScriptingContext.push(crossinline builder: ScriptingContext.() -> Unit): ScriptingContext {
    return push().apply(builder)
}

interface NamedProxyObject : ProxyObject

class ScriptingContext(val name: String? = null, val map: MutableMap<String, Any?> = mutableMapOf()) {
    val keys get() = map.keys

    fun push(): ScriptingContext = ScriptingContext(name, LinkedHashMap(map))
    override fun toString(): String = name ?: super.toString()
    operator fun get(key: String): Any? = map[key]
    operator fun set(key: String, value: Any?) = when (value) {
        is ScriptingContext -> map.put(key, value.toProxyObject())
        is ContextExtensions.NameableProxyExecutable -> {
            value.name = "\"$key\""
            map.put(key, value)
        }
        else -> map.put(key, value)
    }
    
    fun toProxyObject(): NamedProxyObject {
        val delegate = ProxyObject.fromMap(map)
        return object : NamedProxyObject {
            override fun getMember(key: String?): Any? = delegate.getMember(key)
            override fun getMemberKeys(): Any? = delegate.memberKeys
            override fun hasMember(key: String?): Boolean = delegate.hasMember(key)
            override fun putMember(key: String?, value: Value?) = delegate.putMember(key, value)
            override fun removeMember(key: String?): Boolean = delegate.removeMember(key)
            override fun toString(): String = name ?: super.toString()
        }
    }

    fun flatAdd(obj: ScriptingContext): ScriptingContext = apply {
        obj.map.keys.forEach { key ->
            this[key] = obj[key]
        }
    }

    fun applyTo(bindings: Value) {
        map.forEach { (key, value) ->
            bindings.putMember(key, value)
        }
    }
}

//class EvalException(private val exception: PESLEvalException) : RuntimeException() {
//    override val message: String?
//        get() = exception.`object`.toString()
//}
//
//class TokenizeException(private val cmd: String, private val exception: PESLTokenizeException) : RuntimeException() {
//    override val message: String?
//        get() = buildString {
//            append("${exception.localizedMessage}\n\n")
//            append(cmd)
//            append('\n')
//            for (i in 1..exception.index) append(' ')
//            append('^')
//        }
//}
//
//class ParseException(private val cmd: String, private val exception: PESLParseException) : RuntimeException() {
//    override val message: String?
//        get() = buildString {
//            append("${exception.localizedMessage}\n\n")
//            append(cmd)
//            append('\n')
//            for (i in 1..exception.token.start) append(' ')
//            for (i in exception.token.start until exception.token.end) append('^')
//        }
//}