package com.example.statarbitrage.vaadin.views;

import com.example.statarbitrage.events.UpdateUiEvent;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.vaadin.services.FetchPairsProcessor;
import com.example.statarbitrage.vaadin.services.TestTradeProcessor;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Route("") // Maps to root URL
@Service
public class MainView extends VerticalLayout {
    private final Grid<PairData> selectedPairsGrid = new Grid<>(PairData.class, false);
    private final Grid<PairData> tradingPairsGrid = new Grid<>(PairData.class, false);
    private final Grid<PairData> closedPairsGrid = new Grid<>(PairData.class, false);

    private final Binder<Settings> settingsBinder = new Binder<>(Settings.class);
    private final TestTradeProcessor testTradeProcessor;
    private Settings currentSettings;

    private FetchPairsProcessor fetchPairsProcessor;
    private SettingsService settingsService;
    private PairDataService pairDataService;
    private ApplicationEventPublisher applicationEventPublisher;

    private Checkbox simulationCheckbox;
    private ScheduledExecutorService uiUpdateExecutor;

    public MainView(FetchPairsProcessor fetchPairsProcessor, SettingsService settingsService, PairDataService pairDataService, TestTradeProcessor testTradeProcessor, ApplicationEventPublisher applicationEventPublisher) {
        this.fetchPairsProcessor = fetchPairsProcessor;
        this.settingsService = settingsService;
        this.pairDataService = pairDataService;
        this.testTradeProcessor = testTradeProcessor;
        this.applicationEventPublisher = applicationEventPublisher;

        add(new H1("Welcome to StatArbitrage"));

        // Инициализируем чекбокс симуляции
        createSimulationCheckbox();

        // Загружаем текущие настройки
        currentSettings = settingsService.getSettingsFromDb();
        createSettingsForm();

        Button getCointPairsButton = new Button("Получить пары", new Icon(VaadinIcon.REFRESH), e -> findSelectedPairs());
        Button saveSettingsButton = new Button("Сохранить настройки", e -> saveSettings());

        configureGrids();

        add(simulationCheckbox);

        add(new H2("Настройки торговли"),
                saveSettingsButton,
                createSettingsForm(),
                new H2("Отобранные пары (SELECTED)"),
                getCointPairsButton,
                selectedPairsGrid,
                new H2("Торгуемые пары (TRADING)"),
                tradingPairsGrid,
                new H2("Закрытые пары (CLOSED)"),
                closedPairsGrid);

        // Запускаем обновление UI каждые 5 секунд
        startUiUpdater();
    }

    @EventListener
    public void onStartNewTradeEvent(UpdateUiEvent event) {
        updateUI();
    }

    private void startUiUpdater() {
        uiUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
        uiUpdateExecutor.scheduleAtFixedRate(this::updateUI, 0, 60, TimeUnit.SECONDS);
    }

    public void updateUI() {
        getUI().ifPresent(ui -> ui.access(() -> {
            try {
                getSelectedPairs();
                getTraidingPairs();
                getClosedPairs();
            } catch (Exception e) {
                log.error("Ошибка при обновлении UI", e);
            }
        }));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Останавливаем обновление UI при закрытии вкладки
        if (uiUpdateExecutor != null) {
            uiUpdateExecutor.shutdownNow();
        }
        super.onDetach(detachEvent);
    }

    private Checkbox createSimulationCheckbox() {
        simulationCheckbox = new Checkbox("Симуляция");
        simulationCheckbox.setValue(settingsService.getSettingsFromDb().isSimulationEnabled());

        simulationCheckbox.addValueChangeListener(event -> {
            Settings settings = settingsService.getSettingsFromDb();
            settings.setSimulationEnabled(event.getValue());
            settingsService.saveSettingsInDb(settings);
        });

        return simulationCheckbox;
    }

    private FormLayout createSettingsForm() {
        FormLayout settingsForm = new FormLayout();
        settingsForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2),
                new FormLayout.ResponsiveStep("800px", 4),
                new FormLayout.ResponsiveStep("1100px", 6),
                new FormLayout.ResponsiveStep("1400px", 8),
                new FormLayout.ResponsiveStep("1700px", 10)
        );

        // Основные настройки
        TextField timeframeField = new TextField("Таймфрейм");
        NumberField candleLimitField = new NumberField("Лимит свечей (шт)");
        NumberField windowSizeField = new NumberField("Размер окна");
        NumberField significanceLevelField = new NumberField("Уровень значимости");
        NumberField adfSignificanceLevelField = new NumberField("ADF уровень значимости");
        NumberField checkIntervalField = new NumberField("Интервал проверки (мин)");

        // Настройки капитала
        NumberField capitalLongField = new NumberField("Капитал лонг ($)");
        NumberField capitalShortField = new NumberField("Капитал шорт ($)");
        NumberField leverageField = new NumberField("Кредитное плечо");
        NumberField feePctPerTradeField = new NumberField("Комиссия (%)");

        // Настройки выхода
        NumberField exitTakeField = new NumberField("Тейк-профит (%)");
        NumberField exitStopField = new NumberField("Стоп-лосс (%)");
        NumberField exitZMinField = new NumberField("Минимальный Z-скор");
        NumberField exitZMaxPercentField = new NumberField("Макс Z-скор (%)");
        NumberField exitTimeHoursField = new NumberField("Лимит времени (часы)");

        // Фильтры
        NumberField minCorrelationField = new NumberField("Мин корреляция");
        NumberField minVolumeField = new NumberField("Мин объем (млн $)");

        // Добавляем все поля в форму
        settingsForm.add(
                timeframeField, candleLimitField, windowSizeField, significanceLevelField, adfSignificanceLevelField,
                checkIntervalField, capitalLongField, capitalShortField, leverageField, feePctPerTradeField,
                exitTakeField, exitStopField, exitZMinField, exitZMaxPercentField, exitTimeHoursField,
                minCorrelationField, minVolumeField
        );

        // Настраиваем привязку данных
        settingsBinder.forField(timeframeField).bind(Settings::getTimeframe, Settings::setTimeframe);
        settingsBinder.forField(candleLimitField).bind(Settings::getCandleLimit, Settings::setCandleLimit);
        settingsBinder.forField(windowSizeField).bind(Settings::getWindowSize, Settings::setWindowSize);
        settingsBinder.forField(significanceLevelField).bind(Settings::getSignificanceLevel, Settings::setSignificanceLevel);
        settingsBinder.forField(adfSignificanceLevelField).bind(Settings::getAdfSignificanceLevel, Settings::setAdfSignificanceLevel);
        settingsBinder.forField(checkIntervalField).bind(Settings::getCheckInterval, Settings::setCheckInterval);
        settingsBinder.forField(capitalLongField).bind(Settings::getCapitalLong, Settings::setCapitalLong);
        settingsBinder.forField(capitalShortField).bind(Settings::getCapitalShort, Settings::setCapitalShort);
        settingsBinder.forField(leverageField).bind(Settings::getLeverage, Settings::setLeverage);
        settingsBinder.forField(feePctPerTradeField).bind(Settings::getFeePctPerTrade, Settings::setFeePctPerTrade);
        settingsBinder.forField(exitTakeField).bind(Settings::getExitTake, Settings::setExitTake);
        settingsBinder.forField(exitStopField).bind(Settings::getExitStop, Settings::setExitStop);
        settingsBinder.forField(exitZMinField).bind(Settings::getExitZMin, Settings::setExitZMin);
        settingsBinder.forField(exitZMaxPercentField).bind(Settings::getExitZMaxPercent, Settings::setExitZMaxPercent);
        settingsBinder.forField(exitTimeHoursField).bind(Settings::getExitTimeHours, Settings::setExitTimeHours);
        settingsBinder.forField(minCorrelationField).bind(Settings::getMinCorrelation, Settings::setMinCorrelation);
        settingsBinder.forField(minVolumeField).bind(Settings::getMinVolume, Settings::setMinVolume);

        settingsBinder.readBean(currentSettings);

        return settingsForm;
    }

    private void saveSettings() {
        try {
            settingsBinder.writeBean(currentSettings);
            settingsService.saveSettingsInDb(currentSettings);
            Notification.show("Настройки сохранены");
        } catch (Exception e) {
            Notification.show("Ошибка сохранения настроек: " + e.getMessage());
        }
    }

    private void findSelectedPairs() {
        List<PairData> pairs = fetchPairsProcessor.fetchPairs();
        selectedPairsGrid.setItems(pairs);
    }

    private void getSelectedPairs() {
        List<PairData> pairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.SELECTED);
        selectedPairsGrid.setItems(pairs);
    }

    private void getTraidingPairs() {
        List<PairData> pairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        tradingPairsGrid.setItems(pairs);
    }

    private void getClosedPairs() {
        List<PairData> pairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.CLOSED);
        closedPairsGrid.setItems(pairs);
    }

    private void configureGrids() {
        // Настраиваем все таблицы с базовыми колонками
        configureColumnsSelected();
        getSelectedPairs();

        configureColumnsTrading();
        getTraidingPairs();

        configureColumnsClosed();
        getClosedPairs();

        // Добавляем кнопки действий для каждой таблицы
        addStartTradingButton();
        addStopTradingButtons();
    }

    private void configureColumnsSelected() {
        selectedPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true);
        selectedPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true);
        selectedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getZScoreCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP))
                .setHeader("Z-скор").setSortable(true);
        selectedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getCorrelationCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP))
                .setHeader("Корр.").setSortable(true);

        selectedPairsGrid.setHeight("300px");
        selectedPairsGrid.setWidthFull();
    }

    private void configureColumnsTrading() {
        tradingPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true);
        tradingPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true);
        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getZScoreEntry()).setScale(2, BigDecimal.ROUND_HALF_UP))
                .setHeader("Z-скор (entry)").setSortable(true);
        tradingPairsGrid.addColumn(PairData::getProfitChanges)
                .setHeader("Профит (%)")
                .setSortable(true);

        tradingPairsGrid.setHeight("300px");
        tradingPairsGrid.setWidthFull();
    }

    private void configureColumnsClosed() {
        closedPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true);
        closedPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true);
        closedPairsGrid.addColumn(PairData::getProfitChanges)
                .setHeader("Профит (%)")
                .setSortable(true);
        closedPairsGrid.addColumn(PairData::getExitReason)
                .setHeader("Причина выхода")
                .setSortable(true);

        closedPairsGrid.setHeight("300px");
        closedPairsGrid.setWidthFull();
    }

    private void addStartTradingButton() {
        selectedPairsGrid.addColumn(new ComponentRenderer<>(pair -> {
            Button actionButton = new Button("Торговать", event -> {

                //TODO: здесь открытие реальной сделки лонг/шорт

                testTradeProcessor.testTrade(pair);

                Notification.show(String.format(
                        "Статус пары %s/%s изменен на %s",
                        pair.getLongTicker(), pair.getShortTicker(), TradeStatus.TRADING
                ));
                getSelectedPairs();
                getTraidingPairs();
                getClosedPairs();
            });

            // Настраиваем цвет кнопки в зависимости от статуса
            String color = switch (TradeStatus.TRADING) {
                case TRADING -> "green";
                case CLOSED -> "red";
                default -> "black";
            };
            actionButton.getStyle().set("color", color);

            return actionButton;
        })).setHeader("Действие");
    }

    private void addStopTradingButtons() {
        tradingPairsGrid.addColumn(new ComponentRenderer<>(pair -> {
            Button actionButton = new Button("Закрыть", event -> {

                //TODO: здесь закрытие реальной сделки лонг/шорт

                pair.setStatus(TradeStatus.CLOSED);
                pairDataService.saveToDb(pair);

                Notification.show(String.format(
                        "Статус пары %s/%s изменен на %s",
                        pair.getLongTicker(), pair.getShortTicker(), TradeStatus.CLOSED
                ));
                getSelectedPairs();
                getTraidingPairs();
                getClosedPairs();
            });

            // Настраиваем цвет кнопки в зависимости от статуса
            String color = switch (TradeStatus.CLOSED) {
                case TRADING -> "green";
                case CLOSED -> "red";
                default -> "black";
            };
            actionButton.getStyle().set("color", color);

            return actionButton;
        })).setHeader("Действие");
    }
}