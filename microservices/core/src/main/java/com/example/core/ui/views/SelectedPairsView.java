package com.example.core.ui.views;

import com.example.core.processors.FetchPairsProcessor;
import com.example.core.services.TradingPairService;
import com.example.core.ui.components.SettingsComponent;
import com.example.core.ui.components.TradingPairsComponent;
import com.example.core.ui.layout.MainLayout;
import com.example.core.ui.services.UIUpdateService;
import com.example.core.ui.services.UIUpdateable;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradingPair;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Страница отобранных пар
 */
@Slf4j
@PageTitle("Отобранные пары")
@Route(value = "selected-pairs", layout = MainLayout.class)
public class SelectedPairsView extends VerticalLayout implements UIUpdateable {

    private final FetchPairsProcessor fetchPairsProcessor;
    private final TradingPairService tradingPairService;
    private final UIUpdateService uiUpdateService;
    private final TradingPairsComponent tradingPairsComponent;
    private final SettingsComponent settingsComponent;

    private Button getCointPairsButton;

    public SelectedPairsView(FetchPairsProcessor fetchPairsProcessor,
                             TradingPairService tradingPairService,
                             UIUpdateService uiUpdateService,
                             TradingPairsComponent tradingPairsComponent,
                             SettingsComponent settingsComponent) {
        this.fetchPairsProcessor = fetchPairsProcessor;
        this.tradingPairService = tradingPairService;
        this.uiUpdateService = uiUpdateService;
        this.tradingPairsComponent = tradingPairsComponent;
        this.settingsComponent = settingsComponent;

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        initializeLayout();
        setupCallbacks();
    }

    private void initializeLayout() {
        add(new H2("Отобранные пары"));

        getCointPairsButton = new Button("Получить пары", new Icon(VaadinIcon.REFRESH), e -> handleFetchPairsClick());
        updateButtonState();

        add(getCointPairsButton);

        // Показываем только отобранные пары
        tradingPairsComponent.showOnlySelectedPairs();
        add(tradingPairsComponent);
    }

    private void setupCallbacks() {
        tradingPairsComponent.setUiUpdateCallback(v -> updateUI());
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
                getUI().ifPresent(ui -> ui.access(() -> {
                    tradingPairsComponent.setSelectedPairs(Collections.emptyList());
                }));

                int deleteAllByStatus = tradingPairService.deleteAllByStatus(TradeStatus.SELECTED);
                log.debug("Deleted all {} pairs from database", deleteAllByStatus);
                List<TradingPair> pairs = fetchPairsProcessor.fetchPairs(FetchPairsRequest.builder().build());

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
            updateButtonState();
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        uiUpdateService.registerView(UI.getCurrent(), this);
        updateUI();
        updateButtonState();
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