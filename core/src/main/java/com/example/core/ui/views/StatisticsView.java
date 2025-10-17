package com.example.core.ui.views;

import com.example.core.ui.components.StatisticsComponent;
import com.example.core.ui.layout.MainLayout;
import com.example.core.ui.services.UIUpdateService;
import com.example.core.ui.services.UIUpdateable;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

/**
 * Страница статистики
 */
@Slf4j
@PageTitle("Статистика")
@Route(value = "statistics", layout = MainLayout.class)
public class StatisticsView extends VerticalLayout implements UIUpdateable {

    private final StatisticsComponent statisticsComponent;
    private final UIUpdateService uiUpdateService;

    public StatisticsView(StatisticsComponent statisticsComponent,
                          UIUpdateService uiUpdateService) {
        this.statisticsComponent = statisticsComponent;
        this.uiUpdateService = uiUpdateService;

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        add(statisticsComponent);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        uiUpdateService.registerView(UI.getCurrent(), this);
        updateUI();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiUpdateService.unregisterView(UI.getCurrent());
        super.onDetach(detachEvent);
    }

    public void handleUiUpdateRequest() {
        getUI().ifPresent(ui -> ui.access(this::updateUI));
    }

    private void updateUI() {
        getUI().ifPresent(ui -> ui.access(() -> {
            try {
                statisticsComponent.updateStatistics();
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении статистики", e);
            }
        }));
    }
}