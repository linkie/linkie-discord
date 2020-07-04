package me.shedaniel.linkie.discord.config

import kotlinx.serialization.Serializable

@Serializable
data class GuildConfig(
        var tricksEnabled: Boolean = true
)