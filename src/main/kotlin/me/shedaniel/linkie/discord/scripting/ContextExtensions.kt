@file:Suppress("MemberVisibilityCanBePrivate")

package me.shedaniel.linkie.discord.scripting

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.AllowedMentions
import discord4j.rest.util.Permission
import me.shedaniel.linkie.discord.discriminatedName
import me.shedaniel.linkie.discord.gateway
import me.shedaniel.linkie.discord.setTimestampToNow
import me.shedaniel.linkie.discord.validatePermissions
import p0nki.pesl.api.PESLContext
import p0nki.pesl.api.PESLEvalException
import p0nki.pesl.api.`object`.*
import p0nki.pesl.api.builtins.PESLBuiltins
import p0nki.pesl.api.builtins.PESLDataUtils
import kotlin.math.min

object ContextExtensions {
    val math = PESLBuiltins.MATH!!
    val typeOf = PESLBuiltins.TYPEOF!!
    val parseNumber = PESLBuiltins.PARSE_NUMBER!!
    val dir = PESLBuiltins.DIR!!
    val copy = funObj {
        validateArgs(1)
        PESLDataUtils.copy(first())
    }
    val equals = funObj {
        validateArgs(1, 2)
        boolObj(first() == last())
    }
    val deepEquals = funObj {
        validateArgs(1, 2)
        boolObj(PESLDataUtils.deepEquals(first(), last()))
    }
    val range = funObj {
        validateArgs(1, 2)
        val min = if (size == 1) 0 else first().asNumber().value.toInt()
        arrayObj((min until last().asNumber().value.toInt()).map { numberObj(it) })
    }
    val system = mapObj {
        it("currentTimeMillis", funObj {
            validateArgs(0)
            numberObj(System.currentTimeMillis())
        })
        it("nanoTime", funObj {
            validateArgs(0)
            numberObj(System.nanoTime())
        })
    }

    fun discordContexts(user: User, channel: MessageChannel, guild: Guild?, context: PESLContext) {
        context["channel"] = channelObj(user, channel, guild)
    }

    fun commandContexts(event: MessageCreateEvent, user: User, args: MutableList<String>, channel: MessageChannel, context: PESLContext) {
        context["args"] = arrayObj(args.map { stringObj(it) })
        context["message"] = messageObj(event.message, user, false)
        discordContexts(user, channel, event.guild.blockOptional().orElse(null), context)
    }

    fun channelObj(user: User, channel: MessageChannel, guild: Guild?): MapObject {
        val booleans = booleanArrayOf(false)
        return mapObj {
            it("sendEmbed", funObj {
                validateArgs(1, 2)
                if (!booleans[0]) {
                    booleans[0] = true
                    messageObj(channel.createEmbed {
                        if (size == 2) it.setTitle(first().castToString())
                        it.setDescription(last().castToString().let { it.substring(0, min(1999, it.length)) })
                        it.setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                        it.setTimestampToNow()
                    }.block()!!, user, false)
                } else undefined()
            })
            it("sendMessage", funObj {
                validateArgs(1)
                if (!booleans[0]) {
                    booleans[0] = true
                    messageObj(channel.createMessage {
                        it.setAllowedMentions(AllowedMentions.builder().build())
                        it.setContent(first().castToString().let { it.substring(0, min(1999, it.length)) })
                    }.block()!!, user, false)
                } else undefined()
            })
            it("id", stringObj(channel.id.asString()))
            it("mention", stringObj(channel.mention))
        }
    }

    fun messageObj(message: Message, user: User? = null, needPermsToDelete: Boolean = true): MapObject {
        val booleans = booleanArrayOf(false, message.author.get().id != gateway.selfId)
        return mapObj {
            it("id", stringObj(message.id.asString()))
            it("deleteMessage", funObj {
                validateArgs(0)
                if (needPermsToDelete)
                    (user as? Member)?.apply { validatePermissions(Permission.MANAGE_MESSAGES) }
                if (!booleans[0]) {
                    booleans[0] = true
                    message.delete().subscribe()
                }
                undefined()
            })
            it("author", message.author.map<PESLObject>(ContextExtensions::userObj).orElse(undefined()))
            it("content", stringObj(message.content))
            it("edit", funObj {
                validateArgs(1)
                if (!booleans[1]) {
                    booleans[1] = true
                    messageObj(message.edit {
                        it.setContent(first().castToString().let { it.substring(0, min(1999, it.length)) })
                    }.block()!!, user, false)
                } else undefined()
            })
            it("editAsEmbed", funObj {
                validateArgs(1, 2)
                if (!booleans[1]) {
                    booleans[1] = true
                    messageObj(message.edit {
                        it.setEmbed {
                            if (size == 2) it.setTitle(first().castToString())
                            it.setDescription(last().castToString().let { it.substring(0, min(1999, it.length)) })
                            user?.apply {
                                it.setFooter("Requested by $discriminatedName", avatarUrl)
                            }
                            it.setTimestampToNow()
                        }
                    }.block()!!, user, false)
                } else undefined()
            })
        }
    }

    fun userObj(user: User): MapObject {
        return mapObj {
            it("username", stringObj(user.username))
            it("discriminator", stringObj(user.discriminator))
            it("discriminatedName", stringObj(user.discriminatedName))
            it("mention", stringObj(user.mention))
            it("id", stringObj(user.id.asString()))
            it("isBot", boolObj(user.isBot))
        }
    }

    fun undefined(): UndefinedObject = UndefinedObject.INSTANCE

    inline fun mapObj(crossinline builder: ((String, PESLObject) -> Unit) -> Unit): MapObject {
        return MapObject(mutableMapOf()).apply {
            builder { key, value ->
                values[key] = value
            }
        }
    }

    fun funObj(arguments: List<PESLObject>.() -> PESLObject): FunctionObject {
        return FunctionObject.of(true, arguments)
    }

    fun numberObj(int: Int) = numberObj(int.toDouble())
    fun numberObj(long: Long) = numberObj(long.toDouble())
    fun numberObj(double: Double) = NumberObject(double)
    fun stringObj(string: String) = StringObject(string)
    fun boolObj(boolean: Boolean) = BooleanObject(boolean)
    fun arrayObj(list: List<PESLObject>) = ArrayObject(list)

    fun PESLObject.toId(): Snowflake = Snowflake.of(castToString())

    fun List<PESLObject>.validateArgs(vararg size: Int) {
        PESLEvalException.validArgumentListLength(this, *size)
    }
}