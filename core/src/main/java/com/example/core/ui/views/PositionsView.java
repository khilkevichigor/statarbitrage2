package com.example.core.ui.views;

import com.example.core.ui.components.PositionsComponent;
import com.example.core.ui.layout.MainLayout;
import com.example.core.ui.services.UIUpdateService;
import com.example.core.ui.services.UIUpdateable;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

/**
 * Страница позиций
 */
@Slf4j
@PageTitle("Позиции")
@Route(value = "positions", layout = MainLayout.class)
public class PositionsView extends VerticalLayout implements UIUpdateable {

    private final PositionsComponent positionsComponent;
    private final UIUpdateService uiUpdateService;

    public PositionsView(PositionsComponent positionsComponent,
                         UIUpdateService uiUpdateService) {
        this.positionsComponent = positionsComponent;
        this.uiUpdateService = uiUpdateService;

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        initializeLayout();
        setupCallbacks();
    }

    private void initializeLayout() {
        H2 heading = new H2("Позиции");

        Button refreshButton = new Button("Обновить", VaadinIcon.REFRESH.create());
        refreshButton.addClickListener(e -> {
            positionsComponent.refreshPositions();
            log.info("🔄 Позиции обновлены вручную");
        });
        refreshButton.getStyle().set("margin-left", "auto");

        HorizontalLayout header = new HorizontalLayout(heading, refreshButton);
        header.setWidthFull();
        header.setAlignItems(Alignment.BASELINE);

        add(header, positionsComponent);
    }

    private void setupCallbacks() {
        positionsComponent.setUIUpdateCallback(unused -> updateUI());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        log.debug("PositionsView прикреплена к UI");

        // Регистрируемся в UIUpdateService для получения обновлений
        uiUpdateService.registerView(UI.getCurrent(), this);

        // Обновляем данные при открытии страницы
        updateUI();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        log.debug("PositionsView отключена от UI");

        // Отписываемся от обновлений
        uiUpdateService.unregisterView(UI.getCurrent());
    }

    @Override
    public void handleUiUpdateRequest() {
        getUI().ifPresent(ui -> ui.access(this::updateUI));
    }

    private void updateUI() {
        getUI().ifPresent(ui -> ui.access(() -> {
            try {
                log.debug("🔄 Обновление PositionsView");
                positionsComponent.refreshPositions();
            } catch (Exception e) {
                log.error("Ошибка при обновлении PositionsView", e);
            }
        }));
    }
}