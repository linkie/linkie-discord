package me.shedaniel.linkie.discord.commands

import me.shedaniel.linkie.discord.SubCommandHolder

object TricksCommand : SubCommandHolder() {
    val add = subCmd(AddTrickCommand)
    val run = subCmd(RunTrickCommand)
    val remove = subCmd(RemoveTrickCommand)
    val list = subCmd(ListTricksCommand)
    val `list-all` = subCmd(ListAllTricksCommand)
    val info = subCmd(TrickInfoCommand)
}