/*
 * Copyright (c) 2019, 2020, 2021 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.shedaniel.linkie.discord.scripting

import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.shedaniel.linkie.discord.config.ConfigManager
import me.shedaniel.linkie.discord.tricks.ContentType
import me.shedaniel.linkie.discord.tricks.TrickBase
import me.shedaniel.linkie.discord.tricks.TrickFlags
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.MessageCreator
import me.shedaniel.linkie.discord.utils.reply
import me.shedaniel.linkie.discord.utils.validateInGuild
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.EnvironmentAccess
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
        this["exit"] = funObj {
            throw YouTriedException("Achievement Get!\nYou tried to stop Linkie!")
        }
        this["quit"] = funObj {
            throw YouTriedException("Achievement Get!\nYou tried to stop Linkie!")
        }
    }

    suspend inline fun evalTrick(evalContext: EvalContext, creator: MessageCreator, trick: TrickBase, context: () -> ScriptingContext) {
        when (trick.contentType) {
            ContentType.SCRIPT -> {
                val scriptingContext = context()
                trick.flags.forEach {
                    val flag = TrickFlags.flags[it]!!
                    if (trick.requirePermissionForFlags) {
                        flag.validatePermission(evalContext.ctx.member!!)
                    }
                    flag.extendContext(evalContext, scriptingContext)
                }
                eval(scriptingContext, trick.content)
            }
            ContentType.TEXT -> {
                creator.reply(trick.content.format(*evalContext.args.toTypedArray()).let { it.substring(0, min(1999, it.length)) })
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

    fun validateGuild(event: CommandContext) {
        event.validateInGuild {
            require(ConfigManager[guildId.asLong()].tricksEnabled) { "Tricks are not enabled on this server." }
        }
    }

    suspend fun eval(context: ScriptingContext, script: String) {
        val engine = Context.newBuilder("js")
            .allowExperimentalOptions(true)
            .allowNativeAccess(false)
            .allowIO(false)
            .allowCreateProcess(false)
            .allowEnvironmentAccess(EnvironmentAccess.NONE)
            .allowHostClassLoading(false)
            .allowHostAccess(
                HostAccess.newBuilder()
                    .allowArrayAccess(true)
                    .allowListAccess(true)
                    .build()
            )
            .option("js.console", "false")
            .option("js.nashorn-compat", "true")
            .option("js.experimental-foreign-object-prototype", "true")
            .option("js.disable-eval", "true")
            .option("js.load", "false")
            .option("log.level", "OFF")
            .build()
        try {
            var t: Throwable? = null
            withContext(Dispatchers.IO) {
                withTimeout(3000) {
                    launch {
                        try {
                            engine.getBindings("js").also {
                                it.removeMember("load")
                                it.removeMember("loadWithNewGlobal")
                                it.removeMember("eval")
                                it.removeMember("exit")
                                it.removeMember("quit")
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
        } finally {
            engine.close(true)
        }
    }
}

inline fun context(name: String? = null, builder: ScriptingContext.() -> Unit): ScriptingContext {
    return ScriptingContext(name).apply(builder)
}

inline fun ScriptingContext.push(builder: ScriptingContext.() -> Unit): ScriptingContext {
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