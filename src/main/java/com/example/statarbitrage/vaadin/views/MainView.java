package com.example.statarbitrage.vaadin.views;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.vaadin.services.FetchPairsService;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Route("")  // Maps to root URL
public class MainView extends VerticalLayout {
    private final Grid<PairData> selectedPairsGrid = new Grid<>(PairData.class, false);
    private final Grid<PairData> tradingPairsGrid = new Grid<>(PairData.class, false);

    @Autowired
    private FetchPairsService fetchPairsService;

    public MainView() {
        add(new H1("Welcome to StatArbitrage"));

        Button refreshButton = new Button("Обновить", new Icon(VaadinIcon.REFRESH), e -> refreshData());

        configureGrids();

        add(refreshButton,
                new H2("Отобранные пары (SELECTED)"),
                selectedPairsGrid,
                new H2("Торгуемые пары (TRADING/BLACKLISTED/CLOSED)"),
                tradingPairsGrid);
    }

    private void refreshData() {
        List<PairData> allPairs = fetchPairsService.fetchPairs();

        // Разделяем пары по статусам
        List<PairData> selectedPairs = allPairs.stream()
                .filter(p -> p.getStatus() == TradeStatus.SELECTED)
                .collect(Collectors.toList());

        List<PairData> nonSelectedPairs = allPairs.stream()
                .filter(p -> p.getStatus() != TradeStatus.SELECTED)
                .collect(Collectors.toList());

        selectedPairsGrid.setItems(selectedPairs);
        tradingPairsGrid.setItems(nonSelectedPairs);

        Notification.show("Данные обновлены. Отобрано: " + selectedPairs.size() +
                ", Торгуется/другие: " + nonSelectedPairs.size());
    }

    private void configureGrids() {
        // Настраиваем обе таблицы одинаково
        configureGrid(selectedPairsGrid);
        configureGrid(tradingPairsGrid);

        // Дополнительные настройки для второй таблицы
        tradingPairsGrid.addColumn(p -> p.getStatus().toString())
                .setHeader("Статус")
                .setSortable(true);
    }

    private void configureGrid(Grid<PairData> grid) {
        // Основные колонки
        grid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true);
        grid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true);
        grid.addColumn(p -> BigDecimal.valueOf(p.getZScoreCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP))
                .setHeader("Z-скор").setSortable(true);

        // Кнопка "Торговать" только для первой таблицы
        if (grid == selectedPairsGrid) {
            grid.addColumn(new ComponentRenderer<>(pair -> {
                Button tradeButton = new Button("Торговать", event -> {
                    if (pair.getStatus() == TradeStatus.SELECTED) {
                        pair.setStatus(TradeStatus.TRADING);
                        Notification.show("Начата торговля парой: " + pair.getLongTicker() + "/" + pair.getShortTicker());
                        refreshData(); // Обновляем обе таблицы
                    } else if (pair.getStatus() == TradeStatus.TRADING) {
                        Notification.show("Торговля уже ведется для этой пары");
                    } else {
                        Notification.show("Нельзя начать торговлю - неверный статус пары");
                    }
                });
                tradeButton.getStyle().set("color", pair.getStatus() == TradeStatus.TRADING ? "green" : "black");
                return tradeButton;
            })).setHeader("Действие");
        }

        grid.setHeight("300px");
        grid.setWidthFull();
    }
}