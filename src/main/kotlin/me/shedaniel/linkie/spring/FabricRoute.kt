@file:Suppress("unused")

package me.shedaniel.linkie.spring

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Label
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route

@Route("fabric")
class FabricRoute : VerticalLayout() {
    private val buildGradleText = "dependencies {\n    minecraft \"com.mojang:minecraft:@MC@\"\n    mappings \"@YARN@\"\n    modCompile \"@LOADER@\"\n    \n    // Optional Fabric API\n    modCompile \"@API@\"\n}"
    private val gradlePropertiesText = "minecraft_version=@MC@\nyarn_mappings=@YARN@\nloader_version=@LOADER@\n\n# Optional Fabric API\nfabric_version=@API@"
    private val showSnapshots = Checkbox("Show Snapshots")
    private val buildGradleArea = TextArea("build.gradle")
    private val gradlePropertiesArea = TextArea("gradle.properties (Example Mod)")
    private val versionSelect = Select<MinecraftVersion>()
    private val warning = Label()

    init {
        add(H2("Fabric Version"))
        versionSelect.setItems(emptyList<MinecraftVersion>())
        versionSelect.label = "Minecraft Version: "
        versionSelect.setItemLabelGenerator { it.version }
        versionSelect.isRequiredIndicatorVisible = true
        showSnapshots.addValueChangeListener { updateComboBox(it.value) }
        versionSelect.addValueChangeListener { updateInfo(versionSelect.value) }
        versionSelect.isEmptySelectionAllowed = false
        versionSelect.setTextRenderer { it.version }
        updateComboBox(false)

        val horizontalLayout = HorizontalLayout(versionSelect, showSnapshots)
        horizontalLayout.defaultVerticalComponentAlignment = FlexComponent.Alignment.END
        add(horizontalLayout)

        buildGradleArea.setWidthFull()
        buildGradleArea.isReadOnly = true
        gradlePropertiesArea.setWidthFull()
        gradlePropertiesArea.isReadOnly = true
        warning.style.set("color", "red")
        add(warning)
        add(buildGradleArea)
        add(gradlePropertiesArea)
        updateInfo(versionSelect.value)
    }

    private fun updateComboBox(showSnapshots: Boolean) {
        if (!showSnapshots) {
            versionSelect.setItems(minecraftVersions.filter(MinecraftVersion::stable))
        } else {
            versionSelect.setItems(minecraftVersions.toMutableList())
        }
        versionSelect.value = minecraftVersions.firstOrNull(MinecraftVersion::stable)
    }

    private fun updateInfo(versionNullable: MinecraftVersion?) {
        val minecraftVersions = minecraftVersions.toMutableList()
        val version = versionNullable ?: minecraftVersions.firstOrNull(MinecraftVersion::stable) ?: return
        var possibleFabricApiVersion = "@API@"
        var possibleCleanFabricApiVersion = "@API@"
        var firstOne = true
        Label123@ for (i in minecraftVersions.indexOf(version) until minecraftVersions.size) {
            val s = minecraftVersions[i].version
            for ((key, value) in fabricApi) {
                if (s.equals(key, ignoreCase = true)) {
                    possibleFabricApiVersion = if (value.first.fileName.startsWith("fabric-api-")) {
                        "net.fabricmc.fabric-api:fabric-api:" + value.first.fileName.replaceFirst("fabric-api-", "").replace(".jar", "")
                    } else {
                        "net.fabricmc:fabric:" + value.first.fileName.replaceFirst("fabric-", "").replace(".jar", "")
                    }
                    possibleCleanFabricApiVersion = value.first.fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", "")
                    firstOne = i == minecraftVersions.indexOf(version)
                    break@Label123
                }
            }
        }
        buildGradleArea.value = buildGradleText
                .replace("@MC@", version.version)
                .replace("@YARN@", if (version.yarnMaven == null) "@YARN@" else version.yarnMaven!!)
                .replace("@LOADER@", if (loaderVersion == null) "@LOADER@" else loaderVersion!!)
                .replace("@API@", possibleFabricApiVersion)
        gradlePropertiesArea.value = gradlePropertiesText
                .replace("@MC@", version.version)
                .replace("@YARN@", if (version.yarnMaven == null) "@YARN@" else version.yarnMaven!!.substring(version.yarnMaven!!.lastIndexOf(':') + 1))
                .replace("@LOADER@", if (loaderVersion == null) "@LOADER@" else loaderVersion!!.substring(loaderVersion!!.lastIndexOf(':') + 1))
                .replace("@API@", possibleCleanFabricApiVersion)
        if (possibleFabricApiVersion == "@API@")
            warning.text = "ERROR: This version DOES NOT have a fabric api version!"
        else
            warning.text = if (firstOne) "" else "WARNING: This version DOES NOT have a fabric api version! The closest fabric api version has been chosen. Please browse the version here: https://www.curseforge.com/minecraft/mc-mods/fabric-api/files/all"
    }
}