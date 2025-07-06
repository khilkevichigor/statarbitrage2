package com.example.statarbitrage.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.shared.ui.Transport;
import org.springframework.stereotype.Component;

@Push(transport = Transport.WEBSOCKET_XHR)
@Component
public class AppShell implements AppShellConfigurator {
    @Override
    public void configurePage(AppShellSettings settings) {
        // Дополнительные настройки, если нужны
    }
}