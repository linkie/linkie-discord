@file:Suppress("MemberVisibilityCanBePrivate")

package me.shedaniel.linkie.discord.scripting

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.discord.discriminatedName
import me.shedaniel.linkie.discord.setTimestampToNow
import me.shedaniel.linkie.discord.stripMentions
import p0nki.pesl.api.PESLEvalException
import p0nki.pesl.api.`object`.*
import p0nki.pesl.api.builtins.PESLBuiltins
import kotlin.math.min

object ContextExtensions {
    val math = PESLBuiltins.MATH!!
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

    inline fun discordContexts(user: User, channel: MessageChannel, guild: Guild?, crossinline it: (String, PESLObject) -> Unit) {
        it("channel", channelObj(user, channel, guild))
    }

    inline fun commandContexts(event: MessageCreateEvent, user: User, cmd: String, args: MutableList<String>, channel: MessageChannel, crossinline it: (String, PESLObject) -> Unit) {
        it("message", messageObj(event.message, user))
        discordContexts(user, channel, event.guild.blockOptional().orElse(null), it)
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
                    }.block()!!, user)
                } else undefined()
            })
            it("sendMessage", funObj {
                validateArgs(1)
                if (!booleans[0]) {
                    booleans[0] = true
                    messageObj(channel.createMessage {
                        it.setContent(first().castToString().stripMentions(channel, guild).let { it.substring(0, min(1999, it.length)) })
                    }.block()!!, user)
                } else undefined()
            })
            it("id", stringObj(channel.id.asString()))
            it("mention", stringObj(channel.mention))
            it("getMessage", funObj {
                validateArgs(1)
                val first = first()
                if (first is NumberObject) messageObj(channel.getMessageById(Snowflake.of(first.value.toLong())).block()!!)
                else messageObj(channel.getMessageById(Snowflake.of(first.castToString())).block()!!)
            })
        }
    }

    fun messageObj(message: Message, user: User? = null): MapObject {
        val booleans = booleanArrayOf(false, false)
        return mapObj {
            it("delete", funObj {
                validateArgs(0)
                if (!booleans[0]) {
                    booleans[0] = true
                    message.delete().subscribe()
                }
                undefined()
            })
            it("author", message.author.map<PESLObject>(ContextExtensions::userObj).orElse(undefined()))
            it("content", stringObj(message.content.orElse("")))
            it("edit", funObj {
                validateArgs(1)
                if (!booleans[1]) {
                    booleans[1] = true
                    messageObj(message.edit {
                        it.setContent(first().castToString().stripMentions(message.channel.block()!!, message.guild.blockOptional().orElse(null)).let { it.substring(0, min(1999, it.length)) })
                    }.block()!!, user)
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
                    }.block()!!, user)
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

    private fun undefined(): UndefinedObject = UndefinedObject.INSTANCE

    inline fun mapObj(crossinline builder: ((String, PESLObject) -> Unit) -> Unit): MapObject {
        return MapObject(mutableMapOf()).apply {
            builder { key, value ->
                builderSet(key, value)
            }
        }
    }

    fun funObj(arguments: List<PESLObject>.() -> PESLObject): FunctionObject {
        return FunctionObject.of(true, arguments)
    }

    fun numberObj(long: Long) = numberObj(long.toDouble())
    fun numberObj(double: Double) = NumberObject(double)
    fun stringObj(string: String) = StringObject(string)
    fun boolObj(boolean: Boolean) = BooleanObject(boolean)

    fun List<PESLObject>.validateArgs(vararg size: Int) {
        PESLEvalException.validArgumentListLength(this, *size)
    }
}