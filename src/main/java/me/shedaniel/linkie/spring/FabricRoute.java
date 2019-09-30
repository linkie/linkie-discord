package me.shedaniel.linkie.spring;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import me.shedaniel.cursemetaapi.CurseMetaAPI;
import me.shedaniel.linkie.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Route("fabric")
public class FabricRoute extends VerticalLayout {
    private static final String BUILD_GRADLE_TEXT = "dependencies {\n    minecraft \"com.mojang:minecraft:@MC@\"\n    mappings \"@YARN@\"\n    modCompile \"@LOADER@\"\n    \n    // Optional Fabric API\n    modCompile \"@API@\"\n}";
    private static final String GRADLE_PROPERTIES_TEXT = "minecraft_version=@MC@\nyarn_mappings=@YARN@\nloader_version=@LOADER@\n\n# Optional Fabric API\nfabric_version=@API@";
    private Checkbox showSnapshots = new Checkbox("Show Snapshots");
    private TextArea buildGradleArea = new TextArea("build.gradle");
    private TextArea gradlePropertiesArea = new TextArea("gradle.properties (Example Mod)");
    private Select<LoadMeta.MinecraftVersion> versionSelect = new Select<>();
    private Label warning = new Label();
    
    public FabricRoute() {
        add(new H2("Fabric Version"));
        versionSelect.setItems(Collections.emptyList());
        versionSelect.setLabel("Minecraft Version: ");
        versionSelect.setItemLabelGenerator(item -> item.version);
        versionSelect.setRequiredIndicatorVisible(true);
        showSnapshots.addValueChangeListener(event -> updateComboBox(event.getValue()));
        versionSelect.addValueChangeListener(event -> updateInfo(versionSelect.getValue()));
        versionSelect.setEmptySelectionAllowed(false);
        versionSelect.setTextRenderer(item -> item.version);
        updateComboBox(false);
        {
            HorizontalLayout horizontalLayout = new HorizontalLayout(versionSelect, showSnapshots);
            horizontalLayout.setDefaultVerticalComponentAlignment(Alignment.END);
            add(horizontalLayout);
        }
        buildGradleArea.setWidthFull();
        buildGradleArea.setReadOnly(true);
        gradlePropertiesArea.setWidthFull();
        gradlePropertiesArea.setReadOnly(true);
        warning.getStyle().set("color", "red");
        add(warning);
        add(buildGradleArea);
        add(gradlePropertiesArea);
        updateInfo(versionSelect.getValue());
    }
    
    public void updateComboBox(boolean showSnapshots) {
        if (!showSnapshots) {
            versionSelect.setItems(LoadMeta.minecraftVersions.stream().filter(LoadMeta.MinecraftVersion::isStable).collect(Collectors.toList()));
        } else {
            versionSelect.setItems(Collections.unmodifiableList(LoadMeta.minecraftVersions));
        }
        versionSelect.setValue(LoadMeta.minecraftVersions.stream().filter(LoadMeta.MinecraftVersion::isStable).findFirst().get());
    }
    
    public void updateInfo(LoadMeta.MinecraftVersion version) {
        List<LoadMeta.MinecraftVersion> minecraftVersions = new ArrayList<>(LoadMeta.minecraftVersions);
        if (version == null)
            version = minecraftVersions.stream().filter(LoadMeta.MinecraftVersion::isStable).findFirst().get();
        String possibleFabricApiVersion = "@API@";
        String possibleCleanFabricApiVersion = "@API@";
        boolean firstOne = true;
        Label123:
        for(int i = minecraftVersions.indexOf(version); i < minecraftVersions.size(); i++) {
            String s = minecraftVersions.get(i).version;
            for(Map.Entry<String, Pair<CurseMetaAPI.AddonFile, Boolean>> entry : LoadMeta.fabricApi.entrySet()) {
                if (s.equalsIgnoreCase(entry.getKey())) {
                    if (entry.getValue().getKey().fileName.startsWith("fabric-api-")) {
                        possibleFabricApiVersion = "net.fabricmc.fabric-api:fabric-api:" + entry.getValue().getKey().fileName.replaceFirst("fabric-api-", "").replace(".jar", "");
                    } else {
                        possibleFabricApiVersion = "net.fabricmc:fabric:" + entry.getValue().getKey().fileName.replaceFirst("fabric-", "").replace(".jar", "");
                    }
                    possibleCleanFabricApiVersion = entry.getValue().getKey().fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", "");
                    firstOne = i == minecraftVersions.indexOf(version);
                    break Label123;
                }
            }
        }
        buildGradleArea.setValue(BUILD_GRADLE_TEXT
                .replaceAll("@MC@", version.version)
                .replaceAll("@YARN@", version.yarnMaven == null ? "@YARN@" : version.yarnMaven)
                .replaceAll("@LOADER@", LoadMeta.loaderVersion == null ? "@LOADER@" : LoadMeta.loaderVersion)
                .replaceAll("@API@", possibleFabricApiVersion)
        );
        gradlePropertiesArea.setValue(GRADLE_PROPERTIES_TEXT
                .replaceAll("@MC@", version.version)
                .replaceAll("@YARN@", version.yarnMaven == null ? "@YARN@" : version.yarnMaven.substring(version.yarnMaven.lastIndexOf(':') + 1))
                .replaceAll("@LOADER@", LoadMeta.loaderVersion == null ? "@LOADER@" : LoadMeta.loaderVersion.substring(LoadMeta.loaderVersion.lastIndexOf(':') + 1))
                .replaceAll("@API@", possibleCleanFabricApiVersion)
        );
        if (possibleFabricApiVersion.equals("@API@"))
            warning.setText("ERROR: This version DOES NOT have a fabric api version!");
        else
            warning.setText(firstOne ? "" : "WARNING: This version DOES NOT have a fabric api version! The closest fabric api version has been chosen. Please browse the version here: https://www.curseforge.com/minecraft/mc-mods/fabric-api/files/all");
    }
}
