package com.example.statarbitrage.vaadin.views;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.vaadin.services.FetchPairsService;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

@Route("")  // Maps to root URL
public class MainView extends VerticalLayout {
    private final Grid<PairData> grid = new Grid<>(PairData.class, false);

    @Autowired
    private FetchPairsService fetchPairsService;

    public MainView() {
        add(new H1("Welcome to StatArbitrage"));

        Button refreshButton = new Button("Обновить", new Icon(VaadinIcon.REFRESH), e -> refreshData());

        configureGrid();
        add(refreshButton, grid);
    }

    private void refreshData() {
        List<PairData> pairs = fetchPairsService.fetchPairs();
        grid.setItems(pairs);
        Notification.show("Данные обновлены");
    }

    private void configureGrid() {
        // Основные колонки
        grid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true);
        grid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true);
        grid.addColumn(p -> BigDecimal.valueOf(p.getZScoreCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP))
                .setHeader("Z-скор").setSortable(true);

        // Кнопка "Торговать"
        grid.addColumn(new ComponentRenderer<>(pair -> {
            Button tradeButton = new Button("Торговать", event -> {
                if (pair.getStatus() == TradeStatus.SELECTED) {
                    pair.setStatus(TradeStatus.TRADING);
                    Notification.show("Начата торговля парой: " + pair.getLongTicker() + "/" + pair.getShortTicker());
                    refreshData(); // Обновляем данные, чтобы отобразить новый статус
                } else if (pair.getStatus() == TradeStatus.TRADING) {
                    Notification.show("Торговля уже ведется для этой пары");
                } else {
                    Notification.show("Нельзя начать торговлю - неверный статус пары");
                }
            });

            // Меняем стиль кнопки в зависимости от статуса
            tradeButton.getStyle().set("color", pair.getStatus() == TradeStatus.TRADING ? "green" : "black");
            return tradeButton;
        })).setHeader("Действие");

        grid.setHeight("500px");
        grid.setWidthFull();
    }
}