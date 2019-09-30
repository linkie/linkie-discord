package me.shedaniel.linkie.spring;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.util.Collections;
import java.util.stream.Collectors;

@Route("fabric")
public class FabricRoute extends VerticalLayout {
    private Checkbox showSnapshots = new Checkbox("Show Snapshots");
    private ComboBox<LoadMeta.MinecraftVersion> comboBox = new ComboBox<>();
    
    public FabricRoute() {
        add(new H2("Fabric Version"));
        comboBox.setItems(Collections.emptyList());
        comboBox.setLabel("Minecraft Version: ");
        comboBox.setItemLabelGenerator(item -> item.version);
        comboBox.setAllowCustomValue(false);
        comboBox.setRequired(true);
        comboBox.setRequiredIndicatorVisible(true);
        comboBox.setPreventInvalidInput(true);
        showSnapshots.addValueChangeListener(event -> updateComboBox(event.getValue()));
        comboBox.addValueChangeListener(event -> {
            if (event.getValue() == null) {
                comboBox.setValue(event.getOldValue());
                return;
            }
        });
        updateComboBox(false);
        {
            HorizontalLayout horizontalLayout = new HorizontalLayout(comboBox, showSnapshots);
            horizontalLayout.setDefaultVerticalComponentAlignment(Alignment.END);
            add(horizontalLayout);
        }
    }
    
    public void updateComboBox(boolean showSnapshots) {
        if (!showSnapshots) {
            comboBox.setItems(LoadMeta.minecraftVersions.stream().filter(LoadMeta.MinecraftVersion::isStable).collect(Collectors.toList()));
        } else {
            comboBox.setItems(Collections.unmodifiableList(LoadMeta.minecraftVersions));
        }
        comboBox.setValue(LoadMeta.minecraftVersions.stream().filter(LoadMeta.MinecraftVersion::isStable).findFirst().get());
    }
}
