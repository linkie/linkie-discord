package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import me.shedaniel.linkie.*
import java.security.InvalidParameterException
import kotlin.math.*

object CalculateLength : CommandBase {
    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.size != 4)
            throw InvalidUsageException("!$cmd <x1> <y1> <x2> <y2>")
        val x1 = args[0].toDoubleOrNull() ?: throw InvalidParameterException("x1: ${args[0]} is not a valid number.")
        val y1 = args[1].toDoubleOrNull() ?: throw InvalidParameterException("y1: ${args[1]} is not a valid number.")
        val x2 = args[2].toDoubleOrNull() ?: throw InvalidParameterException("x2: ${args[2]} is not a valid number.")
        val y2 = args[3].toDoubleOrNull() ?: throw InvalidParameterException("y2: ${args[3]} is not a valid number.")
        channel.createEmbed {
            fun createText(one: Double, two: Double): String {
                var text = if (one < 0 || two < 0) "[" else "("
                max(one, two).also {
                    if (it < 0)
                        text += "($it)"
                    else text += it
                }
                text += " - "
                min(one, two).also {
                    if (it < 0)
                        text += "($it)"
                    else text += it
                }
                text += if (one < 0 || two < 0) "]^2" else ")^2"
                return text
            }
            it.setTitle("Length between ($x1, $y1) ($x2, $y2)")
            it.setFooter("Requested by ${user.discriminatedName}", user.avatarUrl)
            it.setTimestampToNow()
            it.setDescription("√(${createText(x1, x2)} + ${createText(y1, y2)})\n = ${sqrt(abs(x1 - x2).pow(2) + abs(y1 - y2).pow(2))} (√(${abs(x1 - x2).pow(2) + abs(y1 - y2).pow(2)}))")
        }.subscribe()
    }

    override fun getCategory(): CommandCategory = CommandCategory.MUSIC
    override fun getName(): String = "Calculate Length Command"
    override fun getDescription(): String = "Calculates the length between points"
}