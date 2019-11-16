package me.shedaniel.linkie

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent

interface CommandBase {

    @Throws(Exception::class)
    fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel)

    fun getName(): String? = null
    fun getDescription(): String? = null

}