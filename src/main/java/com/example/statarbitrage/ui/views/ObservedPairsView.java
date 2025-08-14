package com.example.statarbitrage.ui.views;

import com.example.statarbitrage.core.services.ObservedPairsService;
import com.example.statarbitrage.core.services.SettingsService;
import com.example.statarbitrage.ui.components.TradingPairsComponent;
import com.example.statarbitrage.ui.layout.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Наблюдаемые пары")
@Route(value = "observed-pairs", layout = MainLayout.class)
public class ObservedPairsView extends VerticalLayout {

    private final ObservedPairsService observedPairsService;
    private final SettingsService settingsService;
    private final TradingPairsComponent tradingPairsComponent;

    private final TextArea pairsTextArea;
    private final Button saveButton;

    public ObservedPairsView(ObservedPairsService observedPairsService, SettingsService settingsService, TradingPairsComponent tradingPairsComponent) {
        this.observedPairsService = observedPairsService;
        this.settingsService = settingsService;
        this.tradingPairsComponent = tradingPairsComponent;

        this.pairsTextArea = new TextArea("Наблюдаемые пары (через запятую, разделитель тикеров - '/')");
        this.saveButton = new Button("Сохранить");

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        setupLayout();
        loadData();

        saveButton.addClickListener(e -> saveObservedPairs());
    }

    private void setupLayout() {
        pairsTextArea.setWidthFull();
        tradingPairsComponent.showOnlyObservedPairs();
        add(pairsTextArea, saveButton, tradingPairsComponent);
    }

    private void loadData() {
        String observedPairs = settingsService.getSettings().getObservedPairs();
        pairsTextArea.setValue(observedPairs != null ? observedPairs : "");
        tradingPairsComponent.updateObservedPairs();
    }

    private void saveObservedPairs() {
        observedPairsService.updateObservedPairs(pairsTextArea.getValue());
        loadData();
    }
}
