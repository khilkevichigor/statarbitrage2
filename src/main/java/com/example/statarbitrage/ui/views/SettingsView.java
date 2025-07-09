package com.example.statarbitrage.ui.views;

import com.example.statarbitrage.ui.components.SettingsComponent;
import com.example.statarbitrage.ui.layout.MainLayout;
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