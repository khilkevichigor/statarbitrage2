package com.example.statarbitrage.vaadin;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.processors.FetchPairsProcessor;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.TradeStatus;
import com.example.statarbitrage.services.UIUpdateService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
@Route("") // Maps to root URL
public class MainView extends VerticalLayout {

    private final FetchPairsProcessor fetchPairsProcessor;
    private final PairDataService pairDataService;
    private final UIUpdateService uiUpdateService;

    // Components
    private final SettingsComponent settingsComponent;
    private final TradingPairsComponent tradingPairsComponent;
    private final StatisticsComponent statisticsComponent;

    public MainView(FetchPairsProcessor fetchPairsProcessor,
                    PairDataService pairDataService,
                    UIUpdateService uiUpdateService,
                    SettingsComponent settingsComponent,
                    TradingPairsComponent tradingPairsComponent,
                    StatisticsComponent statisticsComponent) {

        this.fetchPairsProcessor = fetchPairsProcessor;
        this.pairDataService = pairDataService;
        this.uiUpdateService = uiUpdateService;
        this.settingsComponent = settingsComponent;
        this.tradingPairsComponent = tradingPairsComponent;
        this.statisticsComponent = statisticsComponent;

        initializeLayout();
        setupUIUpdateCallback();
    }

    private void initializeLayout() {
        add(new H1("Welcome to StatArbitrage"));

        Button getCointPairsButton = new Button("Получить пары", new Icon(VaadinIcon.REFRESH), e -> {
            try {
                tradingPairsComponent.setSelectedPairs(Collections.emptyList());
                int deleteAllByStatus = pairDataService.deleteAllByStatus(TradeStatus.SELECTED);
                log.info("Deleted all {} pairs from database", deleteAllByStatus);
                findSelectedPairs();
            } catch (Exception ex) {
                log.error("Error fetching pairs", ex);
                Notification.show("Ошибка при получении пар: " + ex.getMessage());
            }
        });

        add(
                settingsComponent,
                getCointPairsButton,
                tradingPairsComponent,
                statisticsComponent
        );
    }

    private void setupUIUpdateCallback() {
        tradingPairsComponent.setUiUpdateCallback(v -> updateUI());
    }


    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        uiUpdateService.registerView(UI.getCurrent(), this);
        // Initial data load
        updateUI();
    }

    public void handleUiUpdateRequest() {
        getUI().ifPresent(ui -> ui.access(this::updateUI));
    }


    public void updateUI() {
        getUI().ifPresent(ui -> ui.access(() -> {
            try {
                tradingPairsComponent.updateAllData();
                statisticsComponent.updateStatistics();
            } catch (Exception e) {
                log.error("Ошибка при обновлении UI", e);
            }
        }));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiUpdateService.unregisterView(UI.getCurrent());
        super.onDetach(detachEvent);
    }


    private void findSelectedPairs() {
        try {
            List<PairData> pairs = fetchPairsProcessor.fetchPairs(null);
            tradingPairsComponent.setSelectedPairs(pairs);
        } catch (Exception e) {
            log.error("Error fetching pairs", e);
            Notification.show("Ошибка при получении пар: " + e.getMessage());
        }
    }


}