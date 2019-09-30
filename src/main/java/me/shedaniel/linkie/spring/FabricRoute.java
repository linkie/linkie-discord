package me.shedaniel.linkie.spring;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("fabric")
public class FabricRoute extends VerticalLayout {
    public FabricRoute() {
        add(new Label("Hello World!"));
    }
}
