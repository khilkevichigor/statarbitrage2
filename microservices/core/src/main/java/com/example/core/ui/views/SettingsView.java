package com.example.core.ui.views;

import com.example.core.ui.components.SettingsComponent;
import com.example.core.ui.layout.MainLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Страница настроек
 */
@PageTitle("Настройки")
@Route(value = "settings", layout = MainLayout.class)
public class SettingsView extends VerticalLayout {

    private final SettingsComponent settingsComponent;

    public SettingsView(SettingsComponent settingsComponent) {
        this.settingsComponent = settingsComponent;

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        add(settingsComponent);
    }
}