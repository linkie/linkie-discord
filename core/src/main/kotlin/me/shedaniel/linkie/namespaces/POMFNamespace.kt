package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromTinyFile
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromGithubRepo
import java.net.URL

object POMFNamespace : Namespace("pomf") {
    init {
        registerProvider({ it == "b1.7.3" }) {
            MappingsContainer(it, name = "POMF").apply {
                println("Loading pomf for $version")
                classes.clear()
                loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/Beta%201.7.3%20Merge.tiny"))
                loadNamedFromGithubRepo("minecraft-cursed-legacy/Minecraft-Cursed-POMF", "master", showError = false)
                mappingSource = MappingsContainer.MappingSource.ENGIMA
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override fun getAllVersions(): List<String> = listOf("b1.7.3")
    override fun reloadData() {}
    override fun supportsMixin(): Boolean = true
    override fun getDefaultVersion(command: String?, channelId: Long?): String = "b1.7.3"
}