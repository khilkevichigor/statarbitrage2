package com.example.statarbitrage.vaadin.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("")  // Maps to root URL
public class MainView extends VerticalLayout {
    public MainView() {
        add(new H1("Welcome to StatArbitrage"));
        add(new Button("Click me", e -> Notification.show("Hello from Vaadin!")));
    }
}