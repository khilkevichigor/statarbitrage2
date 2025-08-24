package com.example.core.ui.views;

import com.example.core.ui.components.PortfolioComponent;
import com.example.core.ui.interfaces.UIUpdateable;
import com.example.core.ui.layout.MainLayout;
import com.example.core.ui.services.UIUpdateService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

/**
 * Страница портфолио
 */
@Slf4j
@PageTitle("Портфолио")
@Route(value = "portfolio", layout = MainLayout.class)
public class PortfolioView extends VerticalLayout implements UIUpdateable {

    private final PortfolioComponent portfolioComponent;
    private final UIUpdateService uiUpdateService;

    public PortfolioView(PortfolioComponent portfolioComponent,
                         UIUpdateService uiUpdateService) {
        this.portfolioComponent = portfolioComponent;
        this.uiUpdateService = uiUpdateService;

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        add(portfolioComponent);
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
                portfolioComponent.updatePortfolioInfo();
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении портфолио", e);
            }
        }));
    }
}