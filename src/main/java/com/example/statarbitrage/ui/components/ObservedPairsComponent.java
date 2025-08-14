package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.ObservedPairsService;
import com.example.statarbitrage.core.services.PairDataService;
import com.example.statarbitrage.core.services.SettingsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@SpringComponent
@UIScope
public class ObservedPairsComponent extends VerticalLayout {

    private final ObservedPairsService observedPairsService;
    private final PairDataService pairDataService;
    private final SettingsService settingsService;
    private final ZScoreChartDialog zScoreChartDialog;

    private final TextArea pairsTextArea;
    private final Button saveButton;
    private final Grid<PairData> observedPairsGrid;

    public ObservedPairsComponent(ObservedPairsService observedPairsService, PairDataService pairDataService, SettingsService settingsService, ZScoreChartDialog zScoreChartDialog) {
        this.observedPairsService = observedPairsService;
        this.pairDataService = pairDataService;
        this.settingsService = settingsService;
        this.zScoreChartDialog = zScoreChartDialog;

        this.pairsTextArea = new TextArea("Observed Pairs (comma-separated)");
        this.saveButton = new Button("Save");
        this.observedPairsGrid = new Grid<>(PairData.class, false);

        setupLayout();
        setupGrid();
        loadData();

        saveButton.addClickListener(e -> saveObservedPairs());
    }

    private void setupLayout() {
        pairsTextArea.setWidthFull();
        add(pairsTextArea, saveButton, observedPairsGrid);
    }

    private void setupGrid() {
        observedPairsGrid.addColumn(PairData::getPairName).setHeader("Pair").setSortable(true);
        observedPairsGrid.addColumn(PairData::getZScoreCurrent).setHeader("Z-Score").setSortable(true);
        observedPairsGrid.addColumn(PairData::getCorrelationCurrent).setHeader("Correlation").setSortable(true);
        observedPairsGrid.addColumn(PairData::getPValueCurrent).setHeader("P-Value").setSortable(true);
        observedPairsGrid.addColumn(PairData::getAdfPvalueCurrent).setHeader("ADF P-Value").setSortable(true);
        observedPairsGrid.addColumn(new ComponentRenderer<>(this::createChartButton)).setHeader("Chart");
        observedPairsGrid.setHeight("600px");
        observedPairsGrid.setWidthFull();
    }

    private void loadData() {
        String observedPairs = settingsService.getSettings().getObservedPairs();
        pairsTextArea.setValue(observedPairs != null ? observedPairs : "");
        observedPairsGrid.setItems(pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.OBSERVED));
    }

    private void saveObservedPairs() {
        observedPairsService.updateObservedPairs(pairsTextArea.getValue());
        loadData();
    }

    private Button createChartButton(PairData pair) {
        Button chartButton = new Button(VaadinIcon.LINE_CHART.create());
        chartButton.getElement().setAttribute("title", "Show Z-Score Chart");
        chartButton.addClickListener(event -> zScoreChartDialog.showChart(pair));
        return chartButton;
    }
}
