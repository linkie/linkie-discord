package me.shedaniel.linkie.discord.config

import kotlinx.serialization.Serializable

@Serializable
data class GuildConfig(
        var tricksEnabled: Boolean = true,
        var whitelistedMappings: List<String> = listOf(),
        var blacklistedMappings: List<String> = listOf()
) {
    fun isMappingsEnabled(namespace: String): Boolean {
        if (whitelistedMappings.isNotEmpty())
            return whitelistedMappings.contains(namespace)
        return !blacklistedMappings.contains(namespace)
    }
}