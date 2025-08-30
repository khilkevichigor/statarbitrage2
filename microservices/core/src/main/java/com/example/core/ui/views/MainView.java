package com.example.core.ui.views;

import com.example.core.processors.FetchPairsProcessor;
import com.example.core.services.TradingPairService;
import com.example.core.ui.components.PortfolioComponent;
import com.example.core.ui.components.SettingsComponent;
import com.example.core.ui.components.StatisticsComponent;
import com.example.core.ui.components.TradingPairsComponent;
import com.example.core.ui.services.UIUpdateService;
import com.example.core.ui.services.UIUpdateable;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradingPair;
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
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Route("old-main") // Изменил роут, чтобы не конфликтовал с новым DashboardView
public class MainView extends VerticalLayout implements UIUpdateable {

    private final FetchPairsProcessor fetchPairsProcessor;
    private final TradingPairService tradingPairService;
    private final UIUpdateService uiUpdateService;

    // Components
    private final SettingsComponent settingsComponent;
    private final TradingPairsComponent tradingPairsComponent;
    private final StatisticsComponent statisticsComponent;
    private final PortfolioComponent portfolioComponent;

    // UI elements
    private Button getCointPairsButton;

    public MainView(FetchPairsProcessor fetchPairsProcessor,
                    TradingPairService tradingPairService,
                    UIUpdateService uiUpdateService,
                    SettingsComponent settingsComponent,
                    TradingPairsComponent tradingPairsComponent,
                    StatisticsComponent statisticsComponent,
                    PortfolioComponent portfolioComponent) {

        this.fetchPairsProcessor = fetchPairsProcessor;
        this.tradingPairService = tradingPairService;
        this.uiUpdateService = uiUpdateService;
        this.settingsComponent = settingsComponent;
        this.tradingPairsComponent = tradingPairsComponent;
        this.statisticsComponent = statisticsComponent;
        this.portfolioComponent = portfolioComponent;

        initializeLayout();
        setupUIUpdateCallback();
    }

    @PostConstruct
    public void setupCallbacks() {
        // Устанавливаем callback'и после того, как все компоненты инициализированы
        log.info("🔗 MainView: Устанавливаем callback'и после PostConstruct");
        setupAutoTradingCallback();
    }

    private void initializeLayout() {
        add(new H1("Welcome to StatArbitrage"));

        getCointPairsButton = new Button("Получить пары", new Icon(VaadinIcon.REFRESH), e -> handleFetchPairsClick());

        // Устанавливаем начальное состояние кнопки
        updateButtonState();

        add(
                portfolioComponent,
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

            // Обновляем доступность режима торговли в PortfolioComponent
            portfolioComponent.updateTradingModeAvailability();
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
                int deleteAllByStatus = tradingPairService.deleteAllByStatus(TradeStatus.SELECTED);
                log.debug("Deleted all {} pairs from database", deleteAllByStatus);
                List<TradingPair> pairs = fetchPairsProcessor.fetchPairs(FetchPairsRequest.builder().build());

                // Обновляем UI в UI потоке
                getUI().ifPresent(ui -> ui.access(() -> {
                    tradingPairsComponent.setSelectedPairs(pairs);
                }));
            } catch (Exception ex) {
                log.error("❌ Ошибка получения пар", ex);
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
                portfolioComponent.updatePortfolioInfo();
                tradingPairsComponent.updateAllData();
                statisticsComponent.updateStatistics();
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении UI", e);
            }
        }));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        uiUpdateService.unregisterView(UI.getCurrent());
        super.onDetach(detachEvent);
    }


}