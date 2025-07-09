package com.example.statarbitrage.ui.views;

import com.example.statarbitrage.ui.layout.MainLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Главная страница приложения
 */
@PageTitle("Главная")
@Route(value = "", layout = MainLayout.class)
public class DashboardView extends VerticalLayout {

    public DashboardView() {
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        add(new H1("Добро пожаловать в StatArbitrage"));

        // Здесь будет дашборд с общей информацией
        // TODO: Добавить обзорную информацию о системе
    }
}