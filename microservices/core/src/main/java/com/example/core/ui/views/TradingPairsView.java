package com.example.core.ui.views;

import com.example.core.ui.components.TradingPairsComponent;
import com.example.core.ui.layout.MainLayout;
import com.example.core.ui.services.UIUpdateService;
import com.example.core.ui.services.UIUpdateable;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

/**
 * Страница торгуемых пар
 */
@Slf4j
@PageTitle("Торгуемые пары")
@Route(value = "trading-pairs", layout = MainLayout.class)
public class TradingPairsView extends VerticalLayout implements UIUpdateable {

    private final TradingPairsComponent tradingPairsComponent;
    private final UIUpdateService uiUpdateService;

    public TradingPairsView(TradingPairsComponent tradingPairsComponent,
                            UIUpdateService uiUpdateService) {
        this.tradingPairsComponent = tradingPairsComponent;
        this.uiUpdateService = uiUpdateService;

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        initializeLayout();
        setupCallbacks();
    }

    private void initializeLayout() {
        H2 heading = new H2("Торгуемые пары");
        Button closeAllButton = new Button("Закрыть все");
        closeAllButton.getStyle().set("margin-left", "auto");
        closeAllButton.addClickListener(e -> tradingPairsComponent.closeAllTradesWithConfirmation());

        HorizontalLayout header = new HorizontalLayout(heading, closeAllButton);
        header.setWidthFull();
        header.setAlignItems(Alignment.BASELINE);

        add(header);

        // Показываем только активные торгуемые пары
        tradingPairsComponent.showOnlyPairs();
        add(tradingPairsComponent);
    }

    private void setupCallbacks() {
        tradingPairsComponent.setUiUpdateCallback(v -> updateUI());
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
                tradingPairsComponent.updateAllData();
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении UI", e);
            }
        }));
    }
}