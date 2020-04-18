package me.shedaniel.linkie

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent

interface CommandBase {

    @Throws(Exception::class)
    fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel)

    fun getName(): String? = null
    fun getDescription(): String? = null

    fun getCategory(): CommandCategory = CommandCategory.NORMAL

}

enum class CommandCategory(val description: String?) {
    NORMAL(null),
    MUSIC("Music Commands"),
    MATH("Math Commands");

    companion object {
        fun getValues(guildId: Snowflake?): Array<CommandCategory> {
            if (guildId == null) return arrayOf(NORMAL)
            if (guildId.asLong() == 591645350016712709 || guildId.asLong() == 432055962233470986) {
                return values()
            }
            return arrayOf(NORMAL)
        }
    }
}