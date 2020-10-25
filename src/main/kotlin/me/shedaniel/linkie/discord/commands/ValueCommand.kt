package me.shedaniel.linkie.discord.commands

import me.shedaniel.linkie.discord.SubCommandHolder

object ValueCommand : SubCommandHolder() {
    val set = subCmd(SetValueCommand)
    val get = subCmd(GetValueCommand)
    val list = subCmd(ValueListCommand)
}