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

@file:Suppress("MemberVisibilityCanBePrivate")

package me.shedaniel.linkie.discord.scripting

import com.soywiz.korio.async.runBlockingNoJs
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.discord.gateway
import me.shedaniel.linkie.discord.tricks.TricksManager
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.MessageCreator
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.extensions.getOrNull
import me.shedaniel.linkie.discord.utils.msgCreator
import me.shedaniel.linkie.discord.utils.reply
import me.shedaniel.linkie.discord.utils.sendEdit
import me.shedaniel.linkie.discord.utils.sendEditEmbed
import me.shedaniel.linkie.discord.utils.validateEmpty
import me.shedaniel.linkie.discord.utils.validateNotEmpty
import me.shedaniel.linkie.discord.utils.validatePermissions
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyInstant
import java.net.URLEncoder
import java.time.Instant
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

object ContextExtensions {
    val math = context("Math") {
        this["random"] = funObj {
            validateArgs(0)
            Random.nextDouble()
        }
        this["sqrt"] = funObj {
            validateArgs(1)
            sqrt(first().getAsDouble("x"))
        }
        this["floor"] = funObj {
            validateArgs(1)
            floor(first().getAsDouble("x"))
        }
        this["pow"] = funObj {
            validateArgs(2)
            first().getAsDouble("base").pow(last().getAsDouble("power"))
        }
        this["ceil"] = funObj {
            validateArgs(1)
            ceil(first().getAsDouble("x"))
        }
        this["abs"] = funObj {
            validateArgs(1)
            abs(first().getAsDouble("x"))
        }
        this["sin"] = funObj {
            validateArgs(1)
            sin(first().getAsDouble("x"))
        }
        this["cos"] = funObj {
            validateArgs(1)
            cos(first().getAsDouble("x"))
        }
        this["tan"] = funObj {
            validateArgs(1)
            tan(first().getAsDouble("x"))
        }
        this["min"] = funObj {
            mapIndexed { index, value -> value.getAsBoolean("[$index]") }.minOrNull()!!
        }
        this["max"] = funObj {
            mapIndexed { index, value -> value.getAsBoolean("[$index]") }.maxOrNull()!!
        }
        this["any"] = funObj {
            mapIndexed { index, value -> value.getAsBoolean("[$index]") }.any { it }
        }
        this["all"] = funObj {
            mapIndexed { index, value -> value.getAsBoolean("[$index]") }.all { it }
        }
    }
    val parseNumber = funObj {
        validateArgs(1)
        first().getAsString("number").toDouble()
    }
    val equals = funObj {
        validateArgs(1, 2)
        first() == last()
    }
    val range = funObj {
        validateArgs(1, 2)
        val min = if (size == 1) 0 else first().getAsDouble("min").toInt()
        (min until last().getAsDouble("max").toInt()).toList()
    }
    val system = context("System") {
        this["currentTimeMillis"] = funObj {
            validateArgs(0)
            System.currentTimeMillis()
        }
        this["nanoTime"] = funObj {
            validateArgs(0)
            System.nanoTime()
        }
    }
    val namespaces: ScriptingContext
        get() = context("Namespaces") {
            this["namespaces"] = ProxyArray.fromList(Namespaces.namespaces.values.map { namespaceObj(it) }.map { it.toProxyObject() })
        }

    fun namespaceObj(namespace: Namespace) = context("Namespace") {
        this["id"] = namespace.id
        this["reloading"] = namespace.reloading
        this["defaultVersion"] = namespace.defaultVersion
        this["versions"] = ProxyArray.fromList(namespace.getAllSortedVersions())
        this["supportsAT"] = namespace.supportsAT()
        this["supportsAW"] = namespace.supportsAW()
        this["supportsMixin"] = namespace.supportsMixin()
        this["supportsFieldDescription"] = namespace.supportsFieldDescription()
    }

    fun engineContexts(evalContext: EvalContext, context: ScriptingContext, channel: MessageChannel, creator: MessageCreator, user: User) {
        context["engine"] = context("Engine") {
            this["prefix"] = evalContext.ctx.prefix
            this["cmd"] = evalContext.ctx.cmd
            this["flags"] = ProxyArray.fromList(evalContext.flags.map { it.toString() })
            this["runGlobalTrick"] = funObj {
                if (isEmpty())
                    throw IllegalArgumentException("Invalid amount of arguments!")
                if (!evalContext.parent)
                    throw IllegalStateException("Cannot invoke another trick without being a parent invoker or a global trick!")
                val trickName = first().getAsString("trick_name")
                val trick = TricksManager.get(trickName, evalContext.ctx.guildId)
                runBlockingNoJs {
                    LinkieScripting.evalTrick(evalContext.copy(parent = false), creator, trick) {
                        LinkieScripting.simpleContext.push {
                            commandContexts(evalContext.copy(parent = false), user, channel, creator, this)
                        }
                    }
                }
            }
            this["escapeUrl"] = funObj {
                validateArgs(1)
                URLEncoder.encode(first().getAsString("url"), "UTF-8")
            }
        }
        context["validateArgsEmpty"] = funObj {
            validateArgs(0)
            evalContext.args.validateEmpty(evalContext.ctx.prefix, evalContext.ctx.cmd)
        }
        context["validateArgsNotEmpty"] = funObj {
            validateArgs(1)
            evalContext.args.validateNotEmpty(evalContext.ctx.prefix, evalContext.ctx.cmd + " " + first().getAsString("usage"))
        }
    }

    fun discordContexts(evalContext: EvalContext, user: User, channel: MessageChannel, creator: MessageCreator, context: ScriptingContext) {
        context["channel"] = channelObj(evalContext, user, channel, creator)
    }

    fun commandContexts(evalContext: EvalContext, user: User, channel: MessageChannel, creator: MessageCreator, context: ScriptingContext) {
        context["args"] = ProxyArray.fromList(evalContext.args)
        evalContext.message?.also { message -> context["message"] = messageObj(evalContext, message, user, false) }
        context["flags"] = ProxyArray.fromList(evalContext.flags.toList())
        discordContexts(evalContext, user, channel, creator, context)
        engineContexts(evalContext, context, channel, creator, user)
        context["namespaces"] = namespaces
    }

    fun channelObj(evalContext: EvalContext, user: User, channel: MessageChannel, creator: MessageCreator): ScriptingContext {
        val sent = booleanArrayOf(false)
        return context("Channel") {
            this["sendEmbed"] = funObj {
                validateArgs(1, 2)
                if (!sent[0]) {
                    sent[0] = true
                    if (size == 1 && first().canExecute()) {
                        val specObj = embedSpecObj().apply {
                            first().execute(toProxyObject())
                        }
                        creator.reply {
                            Value.asValue(specObj["title"]).getAsString("title").takeIf(String::isNotBlank)?.let(this::title)
                            Value.asValue(specObj["description"]).getAsString("description").takeIf(String::isNotBlank)?.let(this::description)
                            Value.asValue(specObj["url"]).getAsString("url").takeIf(String::isNotBlank)?.let(this::title)
                            Value.asValue(specObj["color"]).getAsString("color").takeIf(String::isNotBlank)?.let {
                                "#([0-9a-fA-F]{0,2})([0-9a-fA-F]{0,2})([0-9a-fA-F]{1,2})".toRegex().matchEntire(it).let {
                                    if (it != null) {
                                        val (r, g, b) = it.destructured
                                        color(Color.of(r.ifEmpty { "0" }.toInt(16), g.ifEmpty { "0" }.toInt(16), b.ifEmpty { "0" }.toInt(16)))
                                    } else {
                                        throw IllegalArgumentException("Invalid color format! Expected #RRGGBB")
                                    }
                                }
                            }
                            Value.asValue(specObj["image"]).getAsString("image").takeIf(String::isNotBlank)?.let(this::image)
                            Value.asValue(specObj["thumbnail"]).getAsString("thumbnail").takeIf(String::isNotBlank)?.let(this::thumbnail)
                            basicEmbed(user)
                        }
                    } else {
                        creator.reply {
                            if (size == 2) title(first().getAsString("title"))
                            description = last().getAsString("content")
                            basicEmbed(user)
                        }
                    }.block()?.let { message ->
                        messageObj(evalContext, message, user, false)
                    }
                } else throw IllegalStateException("Scripts can not send more than 1 message.")
            }
            this["sendMessage"] = funObj {
                validateArgs(1)
                if (!sent[0]) {
                    sent[0] = true
                    creator.reply(first().getAsString("content").let { it.substring(0, min(1999, it.length)) }).block()?.let { message ->
                        messageObj(evalContext, message, user, false)
                    }
                } else throw IllegalStateException("Scripts can not send more than 1 message.")
            }
            this["id"] = channel.id.asString()
            this["mention"] = channel.mention
        }
    }
    
    fun embedSpecObj(): ScriptingContext {
        return context("EmbedSpec") { 
            this["title"] = ""
            this["description"] = ""
            this["url"] = ""
            this["color"] = ""
            this["image"] = ""
            this["thumbnail"] = ""
        }
    }

    fun messageObj(evalContext: EvalContext, message: Message, user: User? = null, needPermsToDelete: Boolean = true): ScriptingContext {
        val booleans = booleanArrayOf(false, message.author.get().id != gateway.selfId)
        return context("Message") {
            this["id"] = message.id.asString()
            val delete = funObj {
                validateArgs(0)
                if (needPermsToDelete)
                    (user as? Member)?.apply { validatePermissions(Permission.MANAGE_MESSAGES) }
                if (!booleans[0]) {
                    booleans[0] = true
                    message.delete().subscribe()
                }
                null
            }
            this["deleteMessage"] = delete
            this["delete"] = delete
            this["author"] = message.authorAsMember.blockOptional().map { userObj(evalContext, it) }.getOrNull() ?: message.author.map { userObj(evalContext, it) }.getOrNull()
            this["content"] = message.content
            this["edit"] = funObj {
                validateArgs(1)
                if (!booleans[1]) {
                    booleans[1] = true
                    Thread.sleep(500)
                    messageObj(evalContext, message.sendEdit {
                        contentOrNull(first().getAsString("content").let { it.substring(0, min(1999, it.length)) })
                    }.block()!!, user, false)
                } else null
            }
            this["editAsEmbed"] = funObj {
                validateArgs(1, 2)
                if (!booleans[1]) {
                    booleans[1] = true
                    Thread.sleep(500)
                    messageObj(evalContext, message.sendEditEmbed {
                        if (size == 2) title(first().getAsString("title"))
                        description = last().getAsString("content").let { it.substring(0, min(1999, it.length)) }
                        basicEmbed(user)
                    }.block()!!, user, false)
                } else null
            }
            this["timestamp"] = message.timestamp.toProxyInstant()
            this["mentionsEveryone"] = message.mentionsEveryone()
            this["isPinned"] = message.isPinned
            this["isTts"] = message.isTts
            this["channelId"] = message.channelId.asString()
            this["guildId"] = message.guildId.getOrNull()?.asString()
        }
    }

    fun userObj(evalContext: EvalContext, user: User): ScriptingContext = context(user.javaClass.simpleName) {
        this["username"] = user.username
        this["discriminator"] = user.discriminator
        this["discriminatedName"] = user.discriminatedName
        this["mention"] = user.mention
        this["id"] = user.id.asString()
        this["isBot"] = user.isBot
        this["avatarUrl"] = user.avatarUrl
        this["animatedAvatar"] = user.hasAnimatedAvatar()
        this["nickname"] = (user as? Member)?.nickname?.getOrNull()
        this["joinTime"] = (user as? Member)?.joinTime?.getOrNull()?.toProxyInstant()
        this["premiumTime"] = (user as? Member)?.premiumTime?.getOrNull()?.toProxyInstant()
        this["displayName"] = (user as? Member)?.displayName ?: user.username
        this["getPresence"] = funObj {
            validateArgs(0)
            (user as? Member)?.presence?.block()?.let { presenceObj(it) }
        }
        if (evalContext.hasFlag('p'))
            this["openPrivateChannel"] = funObj {
                validateArgs(0)
                val channel = user.privateChannel.block()!!
                channelObj(evalContext, evalContext.ctx.user, channel, channel.msgCreator(gateway, user, null))
            }
    }

    fun presenceObj(presence: Presence): ScriptingContext = context("Presence") {
        this["status"] = presence.status.value
        this["activity"] = presence.activity.getOrNull()?.let { activityObj(it) }
    }

    fun activityObj(activity: Activity): ScriptingContext = context("Activity") {
        this["createdAt"] = activity.createdAt.toProxyInstant()
        this["name"] = activity.name
        this["type"] = activity.type.name.toLowerCase(Locale.ROOT)
        this["details"] = activity.details.getOrNull()
        this["applicationId"] = activity.applicationId.getOrNull()?.asString()
        this["currentPartySize"] = activity.currentPartySize.getOrNull()
    }

    interface NameableProxyExecutable : ProxyExecutable {
        var name: String?
    }
}

class YouTriedException(message: String) : Exception(message)

fun funObj(arguments: List<Value>.() -> Any?): ProxyExecutable {
    return object : ContextExtensions.NameableProxyExecutable {
        override fun execute(vararg values: Value): Any? {
            val any = arguments(values.toList())
            if (any is ScriptingContext)
                return any.toProxyObject()
            return any
        }

        override var name: String? = null
        override fun toString(): String = name ?: super.toString()
    }
}

fun Value.getAsString(field: String): String {
    if (isString) return asString()
    if (isNull) return "null"
    throw IllegalArgumentException("Cannot cast ${this.errorInferredName()} (Field: $field) to string!")
}

fun Value.getAsDouble(field: String): Double {
    if (isNumber) return asDouble()
    throw IllegalArgumentException("Cannot cast ${this.errorInferredName()} (Field: $field) to double!")
}

fun Value.getAsBoolean(field: String): Boolean {
    if (isBoolean) return asBoolean()
    throw IllegalArgumentException("Cannot cast ${this.errorInferredName()} (Field: $field) to string!")
}

fun Value.errorInferredName(): String = toString()

fun List<Value>.validateArgs(vararg size: Int) {
    if (this.size !in size)
        throw IllegalArgumentException("Invalid amount of arguments!")
}

fun Instant.toProxyInstant(): ProxyInstant = this.let { ProxyInstant.from(it) }

data class EvalContext(
    val ctx: CommandContext,
    val message: Message?,
    val flags: List<Char>,
    val args: List<String>,
    val parent: Boolean,
) {
    fun hasFlag(c: Char): Boolean = flags.contains(c)
}
