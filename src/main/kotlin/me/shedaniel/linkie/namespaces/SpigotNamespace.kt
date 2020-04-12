package me.shedaniel.linkie.namespaces

import discord4j.core.`object`.util.Snowflake
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.loadClassFromSpigot
import me.shedaniel.linkie.loadMembersFromSpigot
import java.net.URL

object SpigotNamespace : Namespace("spigot") {
    init {
        registerProvider({ it == "1.8.9" }) {
            MappingsContainer(it, name = "Spigot").apply {
                println("Loading spigot mappings for $version")
                classes.clear()
                loadClassFromSpigot(URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-1.15.2-cl.csrg?at=refs%2Fheads%2Fmaster").openStream())
                loadMembersFromSpigot(URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-1.15.2-members.csrg?at=refs%2Fheads%2Fmaster").openStream())
                mappingSource = MappingsContainer.MappingSource.SPIGOT
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override fun getAllVersions(): List<String> = listOf("1.8.9")
    override fun reloadData() {}
    override fun getDefaultVersion(command: String?, snowflake: Snowflake?): String = "1.8.9"
}