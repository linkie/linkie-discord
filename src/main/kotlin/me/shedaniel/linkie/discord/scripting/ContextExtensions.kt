@file:Suppress("MemberVisibilityCanBePrivate")

package me.shedaniel.linkie.discord.scripting

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import me.shedaniel.linkie.discord.discriminatedName
import me.shedaniel.linkie.discord.setTimestampToNow
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

    fun channelObj(user: User, channel: MessageChannel): MapObject {
        val booleans = booleanArrayOf(false)
        return mapObj {
            funObj {
                validateArgs(1, 2)
                if (!booleans[0]) {
                    booleans[0] = true
                    messageObj(channel.createEmbed {
                        if (size == 2) it.setTitle(first().castToString())
                        it.setDescription(last().castToString().let { it.substring(0, min(1999, it.length)) })
                        it.setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                        it.setTimestampToNow()
                    }.block()!!)
                } else undefined()
            }.apply { 
                it("sendEmbedMessage", this)
                it("sendMessage", this)
            }
//            it("sendMessage", funObj {
//                validateArgs(1)
//                if (!booleans[0]) {
//                    booleans[0] = true
//                    messageObj(channel.createMessage {
//                        it.setContent(first().castToString().let { it.substring(0, min(1999, it.length)) })
//                    }.block()!!)
//                } else undefined()
//            })
        }
    }

    private fun messageObj(message: Message): MapObject {
        val booleans = booleanArrayOf(false)
        return mapObj {
            it("delete", funObj {
                validateArgs(0)
                if (!booleans[0]) {
                    booleans[0] = true
                    message.delete().subscribe()
                }
                undefined()
            })
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

    fun List<PESLObject>.validateArgs(vararg size: Int) {
        PESLEvalException.validArgumentListLength(this, *size)
    }
}