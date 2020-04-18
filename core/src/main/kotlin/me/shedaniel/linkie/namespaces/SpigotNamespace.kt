package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import java.io.InputStream
import java.io.InputStreamReader
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
    override fun getDefaultVersion(command: String?, channelId: Long?): String = "1.8.9"

    private fun MappingsContainer.loadClassFromSpigot(stream: InputStream) {
        InputStreamReader(stream).forEachLine {
            val split = it.split(' ')
            getOrCreateClass(split[1]).also {
                it.obfName.merged = split[0]
            }
        }
    }

    private fun MappingsContainer.loadMembersFromSpigot(stream: InputStream) {
        InputStreamReader(stream).forEachLine {
            val split = it.split(' ')
            if (split.size == 3) {
                // Field
                getOrCreateClass(split[0]).also { clazz ->
                    clazz.getOrCreateField(split[2], "").also { field ->
                        field.obfName.merged = split[1]
                        field.obfDesc.merged = ""
                    }
                }
            } else if (split.size == 4) {
                // Method
                getOrCreateClass(split[0]).also { clazz ->
                    clazz.getOrCreateMethod(split[3], split[2]).also { method ->
                        method.obfName.merged = split[1]
                        method.obfDesc.merged = ""
                    }
                }
            }
        }
    }
}