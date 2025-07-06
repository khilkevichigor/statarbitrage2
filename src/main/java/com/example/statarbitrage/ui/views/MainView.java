package com.example.statarbitrage.ui.views;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.processors.FetchPairsProcessor;
import com.example.statarbitrage.core.services.PairDataService;
import com.example.statarbitrage.ui.components.SettingsComponent;
import com.example.statarbitrage.ui.components.StatisticsComponent;
import com.example.statarbitrage.ui.components.TradingPairsComponent;
import com.example.statarbitrage.ui.services.UIUpdateService;
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
import java.util.concurrent.CompletableFuture;

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

    // UI elements
    private Button getCointPairsButton;

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
        setupAutoTradingCallback();
    }

    private void initializeLayout() {
        add(new H1("Welcome to StatArbitrage"));

        getCointPairsButton = new Button("Получить пары", new Icon(VaadinIcon.REFRESH), e -> handleFetchPairsClick());

        // Устанавливаем начальное состояние кнопки
        updateButtonState();

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

    private void setupAutoTradingCallback() {
        settingsComponent.setAutoTradingChangeCallback(this::updateButtonState);
    }

    private void updateButtonState() {
        getUI().ifPresent(ui -> ui.access(() -> {
            boolean autoTradingEnabled = settingsComponent.isAutoTradingEnabled();
            getCointPairsButton.setEnabled(!autoTradingEnabled);

            if (autoTradingEnabled) {
                getCointPairsButton.setText("Автотрейдинг активен");
                getCointPairsButton.setIcon(new Icon(VaadinIcon.AUTOMATION));
            } else {
                getCointPairsButton.setText("Получить пары");
                getCointPairsButton.setIcon(new Icon(VaadinIcon.REFRESH));
            }
        }));
    }

    private void handleFetchPairsClick() {
        setButtonLoadingState(true);

        CompletableFuture.runAsync(() -> {
            try {
                // Выполняем UI операции в UI потоке
                getUI().ifPresent(ui -> ui.access(() -> {
                    tradingPairsComponent.setSelectedPairs(Collections.emptyList());
                }));

                // Выполняем операции с базой данных в background потоке
                int deleteAllByStatus = pairDataService.deleteAllByStatus(TradeStatus.SELECTED);
                log.info("Deleted all {} pairs from database", deleteAllByStatus);
                List<PairData> pairs = fetchPairsProcessor.fetchPairs(null);

                // Обновляем UI в UI потоке
                getUI().ifPresent(ui -> ui.access(() -> {
                    tradingPairsComponent.setSelectedPairs(pairs);
                }));
            } catch (Exception ex) {
                log.error("Error fetching pairs", ex);
                getUI().ifPresent(ui -> ui.access(() ->
                        Notification.show("Ошибка при получении пар: " + ex.getMessage())
                ));
            } finally {
                getUI().ifPresent(ui -> ui.access(() -> setButtonLoadingState(false)));
            }
        });
    }

    private void setButtonLoadingState(boolean loading) {
        if (loading) {
            getCointPairsButton.setEnabled(false);
            getCointPairsButton.setText("Ищем пары...");
            getCointPairsButton.setIcon(new Icon(VaadinIcon.SPINNER));
        } else {
            updateButtonState(); // Восстанавливаем состояние на основе автотрейдинга
        }
    }


    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        uiUpdateService.registerView(UI.getCurrent(), this);
        // Initial data load
        updateUI();
        // Обновляем состояние кнопки при присоединении компонента
        updateButtonState();
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


}