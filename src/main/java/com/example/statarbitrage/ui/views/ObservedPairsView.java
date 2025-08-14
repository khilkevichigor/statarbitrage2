package com.example.statarbitrage.ui.views;

import com.example.statarbitrage.ui.components.ObservedPairsComponent;
import com.example.statarbitrage.ui.layout.MainLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Наблюдаемые пары")
@Route(value = "observed-pairs", layout = MainLayout.class)
public class ObservedPairsView extends VerticalLayout {

    public ObservedPairsView(ObservedPairsComponent observedPairsComponent) {
        setSizeFull();
        setSpacing(true);
        setPadding(true);
        add(observedPairsComponent);
    }
}
