package com.example.core.ui.views;

import com.example.core.services.ObservedPairsService;
import com.example.core.services.SettingsService;
import com.example.core.ui.components.TradingPairsComponent;
import com.example.core.ui.layout.MainLayout;
import com.example.core.ui.services.UIUpdateService;
import com.example.core.ui.services.UIUpdateable;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PageTitle("Наблюдаемые пары")
@Route(value = "observed-pairs", layout = MainLayout.class)
public class ObservedPairsView extends VerticalLayout implements UIUpdateable {

    private final ObservedPairsService observedPairsService;
    private final SettingsService settingsService;
    private final TradingPairsComponent tradingPairsComponent;
    private final UIUpdateService uiUpdateService;

    private final TextArea pairsTextArea;
    private final Button saveButton;

    public ObservedPairsView(ObservedPairsService observedPairsService, SettingsService settingsService, TradingPairsComponent tradingPairsComponent, UIUpdateService uiUpdateService) {
        this.observedPairsService = observedPairsService;
        this.settingsService = settingsService;
        this.tradingPairsComponent = tradingPairsComponent;
        this.uiUpdateService = uiUpdateService;

        this.pairsTextArea = new TextArea("Наблюдаемые пары (через запятую, разделитель тикеров - '/')");
        this.saveButton = new Button("Сохранить наблюдаемые пары");

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

    @Override
    public void handleUiUpdateRequest() {
        getUI().ifPresent(ui -> ui.access(this::updateUI));
    }

    private void updateUI() {
        getUI().ifPresent(ui -> ui.access(() -> {
            try {
                tradingPairsComponent.updateObservedPairs();
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении UI", e);
            }
        }));
    }
}
