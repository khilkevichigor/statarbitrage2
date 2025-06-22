package com.example.statarbitrage.vaadin.views;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.vaadin.services.FetchPairsService;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
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
    private final Grid<PairData> closedPairsGrid = new Grid<>(PairData.class, false);

    private final Binder<Settings> settingsBinder = new Binder<>(Settings.class);
    private Settings currentSettings;

    @Autowired
    private FetchPairsService fetchPairsService;

    @Autowired
    private SettingsService settingsService;

    public MainView() {
        add(new H1("Welcome to StatArbitrage"));

        // Загружаем текущие настройки
        currentSettings = settingsService.getSettings(); //todo перейти от json на H2
        createSettingsForm();

        Button refreshButton = new Button("Обновить", new Icon(VaadinIcon.REFRESH), e -> refreshData());
        Button saveSettingsButton = new Button("Сохранить настройки", e -> saveSettings());

        configureGrids();

        add(refreshButton,
                saveSettingsButton,
                new H2("Настройки торговли"),
                createSettingsForm(),
                new H2("Отобранные пары (SELECTED)"),
                selectedPairsGrid,
                new H2("Торгуемые пары (TRADING)"),
                tradingPairsGrid,
                new H2("Закрытые пары (CLOSED/BLACKLISTED)"),
                closedPairsGrid);
    }

    private FormLayout createSettingsForm() {
        FormLayout settingsForm = new FormLayout();

        // Основные настройки
        TextField timeframeField = new TextField("Таймфрейм");
        NumberField candleLimitField = new NumberField("Лимит свечей");
        NumberField windowSizeField = new NumberField("Размер окна");
        NumberField significanceLevelField = new NumberField("Уровень значимости");
        NumberField adfSignificanceLevelField = new NumberField("ADF уровень значимости");
        NumberField checkIntervalField = new NumberField("Интервал проверки (мин)");

        // Настройки капитала
        NumberField capitalLongField = new NumberField("Капитал лонг");
        NumberField capitalShortField = new NumberField("Капитал шорт");
        NumberField leverageField = new NumberField("Кредитное плечо");
        NumberField feePctPerTradeField = new NumberField("Комиссия (%)");

        // Настройки выхода
        NumberField exitTakeField = new NumberField("Тейк-профит");
        NumberField exitStopField = new NumberField("Стоп-лосс");
        NumberField exitZMinField = new NumberField("Минимальный Z-скор");
        NumberField exitZMaxPercentField = new NumberField("Макс Z-скор (%)");
        NumberField exitTimeHoursField = new NumberField("Лимит времени (часы)");

        // Фильтры
        NumberField minCorrelationField = new NumberField("Мин корреляция");
        NumberField minVolumeField = new NumberField("Мин объем");

        // Добавляем поля в форму
        settingsForm.add(
                timeframeField, candleLimitField, windowSizeField,
                significanceLevelField, adfSignificanceLevelField, checkIntervalField,
                capitalLongField, capitalShortField, leverageField, feePctPerTradeField,
                exitTakeField, exitStopField, exitZMinField, exitZMaxPercentField, exitTimeHoursField,
                minCorrelationField, minVolumeField
        );

        // Настраиваем привязку данных
        settingsBinder.forField(timeframeField).bind(Settings::getTimeframe, Settings::setTimeframe);
//        settingsBinder.forField(candleLimitField).bind(Settings::getCandleLimit, Settings::setCandleLimit);
//        settingsBinder.forField(windowSizeField).bind(Settings::getWindowSize, Settings::setWindowSize);
        // ... аналогично для всех остальных полей

        settingsBinder.readBean(currentSettings);

        return settingsForm;
    }

    private void saveSettings() {
        try {
            settingsBinder.writeBean(currentSettings);
//            settingsService.saveSettings(currentSettings);
            Notification.show("Настройки сохранены");
        } catch (Exception e) {
            Notification.show("Ошибка сохранения настроек: " + e.getMessage());
        }
    }

    private void refreshData() {
        List<PairData> allPairs = fetchPairsService.fetchPairs();

        // Разделяем пары по статусам
        List<PairData> selectedPairs = allPairs.stream()
                .filter(p -> p.getStatus() == TradeStatus.SELECTED)
                .collect(Collectors.toList());

        List<PairData> tradingPairs = allPairs.stream()
                .filter(p -> p.getStatus() == TradeStatus.TRADING)
                .collect(Collectors.toList());

        List<PairData> closedPairs = allPairs.stream()
                .filter(p -> p.getStatus() == TradeStatus.CLOSED ||
                        p.getStatus() == TradeStatus.BLACKLISTED)
                .collect(Collectors.toList());

        selectedPairsGrid.setItems(selectedPairs);
        tradingPairsGrid.setItems(tradingPairs);
        closedPairsGrid.setItems(closedPairs);

        Notification.show(String.format(
                "Данные обновлены. Отобрано: %d, Торгуется: %d, Закрыто: %d",
                selectedPairs.size(), tradingPairs.size(), closedPairs.size()
        ));
    }

    private void configureGrids() {
        // Настраиваем все таблицы с базовыми колонками
        configureCommonColumns(selectedPairsGrid);
        configureCommonColumns(tradingPairsGrid);
        configureCommonColumns(closedPairsGrid);

        // Добавляем колонку статуса для торгуемых и закрытых пар
        tradingPairsGrid.addColumn(p -> p.getStatus().toString())
                .setHeader("Статус")
                .setSortable(true);

        closedPairsGrid.addColumn(p -> p.getStatus().toString())
                .setHeader("Статус")
                .setSortable(true);

        // Добавляем кнопки действий для каждой таблицы
        addActionButtons(selectedPairsGrid, "Торговать", TradeStatus.TRADING);
        addActionButtons(tradingPairsGrid, "Закрыть", TradeStatus.CLOSED);

        // Для закрытых пар можно добавить кнопку "Вернуть в отобранные"
        addActionButtons(closedPairsGrid, "Вернуть", TradeStatus.SELECTED);
    }

    private void configureCommonColumns(Grid<PairData> grid) {
        grid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true);
        grid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true);
        grid.addColumn(p -> BigDecimal.valueOf(p.getZScoreCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP))
                .setHeader("Z-скор").setSortable(true);

        grid.setHeight("300px");
        grid.setWidthFull();
    }

    private void addActionButtons(Grid<PairData> grid, String buttonText, TradeStatus targetStatus) {
        grid.addColumn(new ComponentRenderer<>(pair -> {
            Button actionButton = new Button(buttonText, event -> {
                pair.setStatus(targetStatus);
                // TODO: Сохранить изменение статуса в БД
                Notification.show(String.format(
                        "Статус пары %s/%s изменен на %s",
                        pair.getLongTicker(), pair.getShortTicker(), targetStatus
                ));
                refreshData();
            });

            // Настраиваем цвет кнопки в зависимости от статуса
            String color = switch (targetStatus) {
                case TRADING -> "green";
                case CLOSED -> "red";
                default -> "black";
            };
            actionButton.getStyle().set("color", color);

            return actionButton;
        })).setHeader("Действие");
    }
}