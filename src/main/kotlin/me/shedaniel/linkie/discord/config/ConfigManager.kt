package me.shedaniel.linkie.discord.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File

object ConfigManager {
    private val default = GuildConfig()
    private val configs = mutableMapOf<Long, GuildConfig>()
    private val configsFolder get() = File(File(System.getProperty("user.dir")), "config").also { it.mkdirs() }
    private val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true))

    fun load() {
        val tempConfigs = mutableMapOf<Long, GuildConfig>()
        configsFolder.listFiles { _, name -> name.endsWith(".json") }?.forEach { configFile ->
            tempConfigs[configFile.nameWithoutExtension.toLong()] = json.parse(GuildConfig.serializer(), configFile.readText())
        }
        configs.clear()
        configs.putAll(tempConfigs)
        save()
    }

    fun save() {
        configs.forEach { (guildId, config) ->
            val configFile = File(configsFolder, "$guildId.json")
            if (configFile.exists().not()) {
                configFile.writeText(json.stringify(GuildConfig.serializer(), config))
            }
        }
    }

    operator fun get(guildId: Long): GuildConfig = configs[guildId] ?: default
}