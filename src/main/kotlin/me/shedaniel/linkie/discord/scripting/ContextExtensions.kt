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

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import me.shedaniel.linkie.discord.gateway
import me.shedaniel.linkie.discord.utils.*
import me.shedaniel.linkie.discord.validatePermissions
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyInstant
import java.time.Instant
import java.util.*
import kotlin.math.*
import kotlin.random.Random

object ContextExtensions {
    val math = context("Math") {
        this["random"] = funObj {
            validateArgs(0)
            Random.nextDouble()
        }
        this["sqrt"] = funObj {
            validateArgs(1)
            sqrt(first().getAsDouble())
        }
        this["floor"] = funObj {
            validateArgs(1)
            floor(first().getAsDouble())
        }
        this["pow"] = funObj {
            validateArgs(2)
            first().getAsDouble().pow(last().getAsDouble())
        }
        this["ceil"] = funObj {
            validateArgs(1)
            ceil(first().getAsDouble())
        }
        this["abs"] = funObj {
            validateArgs(1)
            abs(first().getAsDouble())
        }
        this["sin"] = funObj {
            validateArgs(1)
            sin(first().getAsDouble())
        }
        this["cos"] = funObj {
            validateArgs(1)
            cos(first().getAsDouble())
        }
        this["tan"] = funObj {
            validateArgs(1)
            tan(first().getAsDouble())
        }
        this["min"] = funObj {
            map { it.getAsDouble() }.minOrNull()!!
        }
        this["max"] = funObj {
            map { it.getAsDouble() }.maxOrNull()!!
        }
        this["any"] = funObj {
            map { it.getAsBoolean() }.any { it }
        }
        this["all"] = funObj {
            map { it.getAsBoolean() }.all { it }
        }
    }
    val parseNumber = funObj {
        validateArgs(1)
        first().getAsString().toDouble()
    }
    val equals = funObj {
        validateArgs(1, 2)
        first() == last()
    }
    val range = funObj {
        validateArgs(1, 2)
        val min = if (size == 1) 0 else first().getAsDouble().toInt()
        (min until last().getAsDouble().toInt()).toList()
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

    fun discordContexts(evalContext: EvalContext, user: User, channel: MessageChannel, context: ScriptingContext) {
        context["channel"] = channelObj(evalContext, user, channel)
    }

    fun commandContexts(evalContext: EvalContext, user: User, channel: MessageChannel, context: ScriptingContext) {
        context["args"] = ProxyArray.fromList(evalContext.args)
        context["message"] = messageObj(evalContext, evalContext.event.message, user, false)
        context["flags"] = ProxyArray.fromList(evalContext.flags.toList())
        discordContexts(evalContext, user, channel, context)
    }

    fun channelObj(evalContext: EvalContext, user: User, channel: MessageChannel): ScriptingContext {
        val booleans = booleanArrayOf(false)
        return context("Channel") {
            this["sendEmbed"] = funObj {
                validateArgs(1, 2)
                if (!booleans[0]) {
                    booleans[0] = true
                    messageObj(evalContext, channel.sendEmbedMessage {
                        if (size == 2) setTitle(first().getAsString())
                        description = last().getAsString()
                        setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                        setTimestampToNow()
                    }.block()!!, user, false)
                } else throw IllegalStateException("Scripts can not send more than 1 message.")
            }
            this["sendMessage"] = funObj {
                validateArgs(1)
                if (!booleans[0]) {
                    booleans[0] = true
                    messageObj(evalContext, channel.sendMessage(first().getAsString().let { it.substring(0, min(1999, it.length)) }).block()!!, user, false)
                } else throw IllegalStateException("Scripts can not send more than 1 message.")
            }
            this["id"] = channel.id.asString()
            this["mention"] = channel.mention
        }
    }

    fun messageObj(evalContext: EvalContext, message: Message, user: User? = null, needPermsToDelete: Boolean = true): ScriptingContext {
        val booleans = booleanArrayOf(false, message.author.get().id != gateway.selfId)
        return context("Message") {
            this["id"] = message.id.asString()
            this["deleteMessage"] = funObj {
                validateArgs(0)
                if (needPermsToDelete)
                    (user as? Member)?.apply { validatePermissions(Permission.MANAGE_MESSAGES) }
                if (!booleans[0]) {
                    booleans[0] = true
                    message.delete().subscribe()
                }
                null
            }
            this["author"] = message.authorAsMember.blockOptional().map { userObj(evalContext, it) }.getOrNull() ?: message.author.map { userObj(evalContext, it) }.getOrNull()
            this["content"] = message.content
            this["edit"] = funObj {
                validateArgs(1)
                if (!booleans[1]) {
                    booleans[1] = true
                    messageObj(evalContext, message.sendEdit {
                        it.content = first().getAsString().let { it.substring(0, min(1999, it.length)) }
                    }.block()!!, user, false)
                } else null
            }
            this["editAsEmbed"] = funObj {
                validateArgs(1, 2)
                if (!booleans[1]) {
                    booleans[1] = true
                    messageObj(evalContext, message.sendEditEmbed {
                        if (size == 2) setTitle(first().getAsString())
                        description = last().getAsString().let { it.substring(0, min(1999, it.length)) }
                        user?.apply {
                            setFooter("Requested by $discriminatedName", avatarUrl)
                        }
                        setTimestampToNow()
                    }.block()!!, user, false)
                } else null
            }
            this["timestamp"] = message.timestamp.toProxyInstant()
            this["mentionsEveryone"] = message.mentionsEveryone()
            this["isPinned"] = message.isPinned
            this["isTts"] = message.isTts
            this["channelId"] = message.channelId.toString()
            this["guildId"] = message.guildId.getOrNull()?.toString()
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
        this["joinTime"] = (user as? Member)?.joinTime?.toProxyInstant()
        this["premiumTime"] = (user as? Member)?.premiumTime?.getOrNull()?.toProxyInstant()
        this["displayName"] = (user as? Member)?.displayName ?: user.username
        this["getPresence"] = funObj {
            validateArgs(0)
            (user as? Member)?.presence?.block()?.let { presenceObj(it) }
        }
        if (evalContext.hasFlag('p'))
            this["openPrivateChannel"] = funObj {
                validateArgs(0)
                channelObj(evalContext, evalContext.event.message.author.get(), user.privateChannel.block()!!)
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

fun Value.getAsString(): String {
    if (isString) return asString()
    if (isNull) return "null"
    throw IllegalArgumentException("Cannot cast ${this.errorInferredName()} to string!")
}

fun Value.getAsDouble(): Double {
    if (isNumber) return asDouble()
    throw IllegalArgumentException("Cannot cast ${this.errorInferredName()} to double!")
}

fun Value.getAsBoolean(): Boolean {
    if (isBoolean) return asBoolean()
    throw IllegalArgumentException("Cannot cast ${this.errorInferredName()} to string!")
}

fun Value.errorInferredName(): String = toString()

fun List<Value>.validateArgs(vararg size: Int) {
    if (this.size !in size)
        throw IllegalArgumentException("Invalid amount of arguments!")
}

fun Instant.toProxyInstant(): ProxyInstant = this.let { ProxyInstant.from(it) }

data class EvalContext(
    val event: MessageCreateEvent,
    val flags: List<Char>,
    val args: List<String>,
) {
    fun hasFlag(c: Char): Boolean = flags.contains(c)
}