package me.shedaniel.linkie.discord.config

import kotlinx.serialization.json.Json
import java.io.File

object ConfigManager {
    private val configs = mutableMapOf<Long, GuildConfig>()
    private val configsFolder get() = File(File(System.getProperty("user.dir")), "config").also { it.mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val configValues: MutableList<ConfigValueProvider> = mutableListOf(
            simpleValue("prefix", value({ prefix ?: "null" }) { require(it.length in 1..5) { "Prefix can only be between 1 and 5 characters long!" }; require(it == "null" || tricksPrefix != it) { "Tricks prefix can not be same as command prefix!" }; prefix = it.takeUnless { it == "null" } }),
            simpleValue("tricks.prefix", value({ tricksPrefix ?: "null" }) { require(it.length in 1..5) { "Prefix can only be between 1 and 5 characters long!" }; require(it == "null" || it != prefix) { "Tricks prefix can not be same as command prefix!" }; tricksPrefix = it.takeUnless { it == "null" } }),
            simpleValue("tricks.enabled", value({ tricksEnabled.toString() }) { tricksEnabled = it.toBoolean() }),
            simpleValue("mappings.whitelist", value({ whitelistedMappings.joinToString(",") }) { whitelistedMappings = it.split(",").toList().filter { it.isNotBlank() } }),
            simpleValue("mappings.blacklist", value({ blacklistedMappings.joinToString(",") }) { blacklistedMappings = it.split(",").toList().filter { it.isNotBlank() } }),
    )

    fun load() {
        val tempConfigs = mutableMapOf<Long, GuildConfig>()
        configsFolder.listFiles { _, name -> name.endsWith(".json") }?.forEach { configFile ->
            tempConfigs[configFile.nameWithoutExtension.toLong()] = json.decodeFromString(GuildConfig.serializer(), configFile.readText())
        }
        configs.clear()
        configs.putAll(tempConfigs)
        save()
    }

    fun save() {
        configs.forEach { (guildId, config) ->
            val configFile = File(configsFolder, "$guildId.json")
            configFile.writeText(json.encodeToString(GuildConfig.serializer(), config))
        }
    }

    private fun getConfigValueHandler(property: String): ConfigValue? {
        for (configValue in configValues) {
            val value = configValue.provide(property)
            if (value != null) return value
        }
        return null
    }

    fun getValueOf(guildConfig: GuildConfig, property: String): String =
            (getConfigValueHandler(property) ?: throw NullPointerException("Invalid Property: `$property`")).get(guildConfig)

    fun setValueOf(guildConfig: GuildConfig, property: String, value: String) =
            (getConfigValueHandler(property) ?: throw NullPointerException("Invalid Property: `$property`")).set(guildConfig, value)

    operator fun get(guildId: Long): GuildConfig = configs.getOrPut(guildId) { GuildConfig() }

    private fun simpleValue(property: String, configValue: ConfigValue): ConfigValueProvider =
            object : ConfigValueProvider {
                override fun provide(string: String): ConfigValue? =
                        if (property != string) null else configValue

                override fun propertiesNames(): Collection<String> = listOf(property)
            }

    private fun multipleValue(properties: Collection<String>, configValue: (String) -> ConfigValue): ConfigValueProvider =
            object : ConfigValueProvider {
                override fun provide(string: String): ConfigValue? =
                        if (string !in properties) null else configValue(string)

                override fun propertiesNames(): Collection<String> = properties
            }

    private fun value(getter: GuildConfig.() -> String, setter: GuildConfig.(String) -> Unit): ConfigValue =
            object : ConfigValue {
                override fun get(thisRef: GuildConfig): String = getter(thisRef)
                override fun set(thisRef: GuildConfig, value: String) = setter(thisRef, value)
            }

    fun getProperties(): Set<String> {
        val set = mutableSetOf<String>()
        for (configValue in configValues) {
            set.addAll(configValue.propertiesNames())
        }
        return set.sorted().toSortedSet()
    }

    interface ConfigValueProvider {
        fun provide(string: String): ConfigValue?
        fun propertiesNames(): Collection<String>
    }

    interface ConfigValue {
        fun get(thisRef: GuildConfig): String
        fun set(thisRef: GuildConfig, value: String)
    }
}