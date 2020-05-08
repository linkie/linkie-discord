package me.shedaniel.linkie.namespaces

import kotlinx.serialization.json.content
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromTinyFile
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromTinyJar
import java.net.URL

object PlasmaNamespace : Namespace("plasma") {
    private lateinit var downloadUrl: String

    init {
        registerProvider({ it == "b1.7.3" }) {
            MappingsContainer(it, name = "Plasma").apply {
                println("Loading plasma for $version")
                classes.clear()
                loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/Beta%201.7.3%20Merge.tiny"))
                loadNamedFromTinyJar(URL(downloadUrl), showError = false)
                mappingSource = MappingsContainer.MappingSource.YARN_V1
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override fun getAllVersions(): List<String> = listOf("b1.7.3")
    override fun reloadData() {
        val element = json.parseJson(URL("https://api.github.com/repos/minecraft-cursed-legacy/Plasma/releases/latest").readText())
        downloadUrl = element.jsonObject["assets"]!!.jsonArray[0].jsonObject["browser_download_url"]!!.content
    }

    override fun supportsMixin(): Boolean = true
    override fun getDefaultVersion(command: String?, channelId: Long?): String = "b1.7.3"
}