package com.example.statarbitrage.vaadin.views;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.TradeStatisticsDto;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.services.StatisticsService;
import com.example.statarbitrage.vaadin.processors.FetchPairsProcessor;
import com.example.statarbitrage.vaadin.processors.TestTradeProcessor;
import com.example.statarbitrage.vaadin.schedulers.PairMaintainerScheduler;
import com.example.statarbitrage.vaadin.schedulers.TradeAndSimulationScheduler;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.example.statarbitrage.constant.Constants.EXIT_REASON_MANUALLY;

//todo bug в трейдинг таблице zentry= 2.5 а zcurrent=-2 практически сразу!!!

@Slf4j
@Route("") // Maps to root URL
public class MainView extends VerticalLayout {
    private final Grid<PairData> selectedPairsGrid = new Grid<>(PairData.class, false);
    private final Grid<PairData> tradingPairsGrid = new Grid<>(PairData.class, false);
    private final Grid<PairData> closedPairsGrid = new Grid<>(PairData.class, false);
    private VerticalLayout statisticsLayout; // Добавь в поля класса


    private final Binder<Settings> settingsBinder = new Binder<>(Settings.class);
    private final TestTradeProcessor testTradeProcessor;
    private Settings currentSettings;

    private FetchPairsProcessor fetchPairsProcessor;
    private SettingsService settingsService;
    private PairDataService pairDataService;
    private StatisticsService statisticsService; // добей в поле класса
    private PairMaintainerScheduler pairMaintainerScheduler; // добей в поле класса

    private Checkbox simulationCheckbox;
    private ScheduledExecutorService uiUpdateExecutor;
    private TradeAndSimulationScheduler tradeAndSimulationScheduler;

    public MainView(FetchPairsProcessor fetchPairsProcessor, SettingsService settingsService, PairDataService pairDataService, TestTradeProcessor testTradeProcessor, StatisticsService statisticsService, PairMaintainerScheduler pairMaintainerScheduler) {
        this.fetchPairsProcessor = fetchPairsProcessor;
        this.settingsService = settingsService;
        this.pairDataService = pairDataService;
        this.testTradeProcessor = testTradeProcessor;
        this.statisticsService = statisticsService;
        this.pairMaintainerScheduler = pairMaintainerScheduler;

        add(new H1("Welcome to StatArbitrage"));

        // Инициализируем чекбокс симуляции
        createSimulationCheckbox();

        // Загружаем текущие настройки
        currentSettings = settingsService.getSettingsFromDb();
        createSettingsForm();

        Button getCointPairsButton = new Button("Получить пары", new Icon(VaadinIcon.REFRESH), e -> {
            selectedPairsGrid.setItems(Collections.emptyList());
            int deleteAllByStatus = pairDataService.deleteAllByStatus(TradeStatus.SELECTED);
            log.info("Deleted all {} pairs from database", deleteAllByStatus);
            findSelectedPairs();
        });
        Button saveSettingsButton = new Button("Сохранить настройки", e -> saveSettings());

        configureGrids();

        add(simulationCheckbox);

        statisticsLayout = createStatisticsBlock(); // создаём layout

        add(
                new H2("Настройки торговли"),
                saveSettingsButton,
                createSettingsForm(),
                new H2("Отобранные пары (SELECTED)"),
                getCointPairsButton,
                selectedPairsGrid,
                new H2("Торгуемые пары (TRADING)"),
                tradingPairsGrid,
                new H2("Закрытые пары (CLOSED)"),
                closedPairsGrid,
                new H2("📊 Статистика трейдов"),
                statisticsLayout

        );

        // Запускаем обновление UI каждые 5 секунд
        startUiUpdater();
    }

    private void updateStatisticsBlock() {
        statisticsLayout.removeAll(); // очищаем
        TradeStatisticsDto stats = statisticsService.collectStatistics();

        Grid<StatisticRow> grid = new Grid<>();
        grid.setAllRowsVisible(true);
        grid.addColumn(StatisticRow::name).setHeader("Показатель");
        grid.addColumn(StatisticRow::today).setHeader("Сегодня");
        grid.addColumn(StatisticRow::total).setHeader("Всего");

        grid.setItems(List.of(
                new StatisticRow("Сделки", stats.getTradesToday(), stats.getTradesTotal()),
                new StatisticRow("Avg Профит (%)", format(stats.getAvgProfitToday()), format(stats.getAvgProfitTotal())),
                new StatisticRow("Сумма Профита (%)", format(stats.getSumProfitToday()), format(stats.getSumProfitTotal())),
                new StatisticRow("Выход: STOP", stats.getExitByStopToday(), stats.getExitByStopTotal()),
                new StatisticRow("Выход: TAKE", stats.getExitByTakeToday(), stats.getExitByTakeTotal()),
                new StatisticRow("Выход: Z MIN", stats.getExitByZMinToday(), stats.getExitByZMinTotal()),
                new StatisticRow("Выход: Z MAX", stats.getExitByZMaxToday(), stats.getExitByZMaxTotal()),
                new StatisticRow("Выход: TIME", stats.getExitByTimeToday(), stats.getExitByTimeTotal())
        ));

        statisticsLayout.add(grid);
    }


    private VerticalLayout createStatisticsBlock() {
        TradeStatisticsDto stats = statisticsService.collectStatistics();

        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(false);
        layout.setPadding(false);

        layout.add(new H2("Сегодня / Всего"));

        Grid<StatisticRow> grid = new Grid<>();
        grid.setAllRowsVisible(true);
        grid.addColumn(StatisticRow::name).setHeader("Показатель");
        grid.addColumn(StatisticRow::today).setHeader("Сегодня");
        grid.addColumn(StatisticRow::total).setHeader("Всего");

        grid.setItems(List.of(
                new StatisticRow("Сделки", stats.getTradesToday(), stats.getTradesTotal()),
                new StatisticRow("Avg Профит (%)", format(stats.getAvgProfitToday()), format(stats.getAvgProfitTotal())),
                new StatisticRow("Сумма Профита (%)", format(stats.getSumProfitToday()), format(stats.getSumProfitTotal())),
                new StatisticRow("Выход: STOP", stats.getExitByStopToday(), stats.getExitByStopTotal()),
                new StatisticRow("Выход: TAKE", stats.getExitByTakeToday(), stats.getExitByTakeTotal()),
                new StatisticRow("Выход: Z MIN", stats.getExitByZMinToday(), stats.getExitByZMinTotal()),
                new StatisticRow("Выход: Z MAX", stats.getExitByZMaxToday(), stats.getExitByZMaxTotal()),
                new StatisticRow("Выход: TIME", stats.getExitByTimeToday(), stats.getExitByTimeTotal())
        ));

        layout.add(grid);
        return layout;
    }

    private String format(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).toString();
    }

    //для обновления UI
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI.getCurrent().getSession().setAttribute(MainView.class, this);
    }

    //для обновления UI
    public void handleUiUpdateRequest() {
        getUI().ifPresent(ui -> ui.access(this::updateUI));
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
                updateStatisticsBlock(); // ⬅️ вот здесь!
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
            if (event.getValue()) {
//                pairMaintainerScheduler.maintainActivePairs();
//                tradeAndSimulationScheduler.updateTradesAndMaintainPairs();
            }
            log.info(event.getValue() ? "Симуляция включена" : "Симуляция отключена");
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
        NumberField candleLimitField = new NumberField("Свечей (шт)");
        NumberField minZField = new NumberField("Min Z");
        NumberField minWindowSizeField = new NumberField("Min windowSize");
        NumberField minPValueField = new NumberField("Min pValue");
        NumberField minAdfValueField = new NumberField("Min adfValue");
        NumberField minCorrelationField = new NumberField("Min corr");
        NumberField minVolumeField = new NumberField("Min Vol (млн $)");
        NumberField checkIntervalField = new NumberField("Обновление (мин)");

        // Настройки капитала
        NumberField capitalLongField = new NumberField("Depo лонг ($)");
        NumberField capitalShortField = new NumberField("Depo шорт ($)");
        NumberField leverageField = new NumberField("Depo Leverage");
        NumberField feePctPerTradeField = new NumberField("Depo Комиссия (%)");

        // Настройки выхода
        NumberField exitTakeField = new NumberField("Exit Тейк (%)");
        NumberField exitStopField = new NumberField("Exit Стоп (%)");
        NumberField exitZMinField = new NumberField("Exit Мин Z");
        NumberField exitZMaxPercentField = new NumberField("Exit Макс Z (%)");
        NumberField exitTimeHoursField = new NumberField("Exit Таймаут (ч)");

        // Фильтры

        NumberField usePairs = new NumberField("Кол-во пар");

        // Добавляем все поля в форму
        settingsForm.add(
                timeframeField, candleLimitField,
                minZField, minWindowSizeField, minPValueField, minAdfValueField, checkIntervalField, minCorrelationField, minVolumeField, usePairs,
                capitalLongField, capitalShortField, leverageField, feePctPerTradeField,
                exitTakeField, exitStopField, exitZMinField, exitZMaxPercentField, exitTimeHoursField
        );

        // Настраиваем привязку данных
        settingsBinder.forField(timeframeField).bind(Settings::getTimeframe, Settings::setTimeframe);
        settingsBinder.forField(candleLimitField).bind(Settings::getCandleLimit, Settings::setCandleLimit);
        settingsBinder.forField(minZField).bind(Settings::getMinZ, Settings::setMinZ);
        settingsBinder.forField(minWindowSizeField).bind(Settings::getMinWindowSize, Settings::setMinWindowSize);
        settingsBinder.forField(minPValueField).bind(Settings::getMinPvalue, Settings::setMinPvalue);
        settingsBinder.forField(minAdfValueField).bind(Settings::getMinAdfValue, Settings::setMinAdfValue);
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
        settingsBinder.forField(usePairs).bind(Settings::getUsePairs, Settings::setUsePairs);

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
        List<PairData> pairs = fetchPairsProcessor.fetchPairs(null);
        selectedPairsGrid.setItems(pairs);
    }

    private void getSelectedPairs() {
        List<PairData> pairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.SELECTED);
        selectedPairsGrid.setItems(pairs);
    }

    private void clearSelectedPairs() {
        selectedPairsGrid.setItems(Collections.emptyList());
    }

    private void getTraidingPairs() {
        List<PairData> pairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        tradingPairsGrid.setItems(pairs);
    }

    private void getClosedPairs() {
        List<PairData> pairs = pairDataService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.CLOSED);
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

        selectedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getZScoreCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Z-скор").setSortable(true);

        selectedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getPValueCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("PValue (curr)").setSortable(true);
        selectedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getAdfPvalueCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("AdfValue (curr)").setSortable(true);

        selectedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getCorrelationCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Корр.").setSortable(true);

        selectedPairsGrid.setHeight("300px");
        selectedPairsGrid.setWidthFull();
    }

    private void configureColumnsTrading() {
        tradingPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true);
        tradingPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true);

        tradingPairsGrid.addColumn(PairData::getProfitChanges).setHeader("Профит (%)").setSortable(true);

        tradingPairsGrid.addColumn(p -> p.getLongChanges().setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Long (%)").setSortable(true);
        tradingPairsGrid.addColumn(p -> p.getShortChanges().setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Short (%)").setSortable(true);

        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getTimeInMinutesSinceEntryToMin()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Min Long Time (min)").setSortable(true);
        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getTimeInMinutesSinceEntryToMax()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Max Short Time (min)").setSortable(true);

        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getZScoreEntry()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Z-скор (entry)").setSortable(true);
        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getZScoreCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Z-скор (curr)").setSortable(true);

        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getPValueEntry()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Pvalue (entry)").setSortable(true);
        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getPValueCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Pvalue (curr)").setSortable(true);

        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getAdfPvalueEntry()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("AdfValue (entry)").setSortable(true);
        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getAdfPvalueCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("AdfValue (curr)").setSortable(true);

        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getCorrelationEntry()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Corr (entry)").setSortable(true);
        tradingPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getCorrelationCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Corr (curr)").setSortable(true);

        tradingPairsGrid.setHeight("300px");
        tradingPairsGrid.setWidthFull();
    }

    private void configureColumnsClosed() {
        closedPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true);
        closedPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true);

        closedPairsGrid.addColumn(PairData::getProfitChanges).setHeader("Профит (%)").setSortable(true);

        closedPairsGrid.addColumn(p -> p.getLongChanges().setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Long (%)").setSortable(true);
        closedPairsGrid.addColumn(p -> p.getShortChanges().setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Short (%)").setSortable(true);

        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getTimeInMinutesSinceEntryToMin()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Min Long Time (min)").setSortable(true);
        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getTimeInMinutesSinceEntryToMax()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Max Short Time (min)").setSortable(true);

        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getZScoreEntry()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Z-скор (entry)").setSortable(true);
        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getZScoreCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Z-скор (curr)").setSortable(true);

        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getPValueEntry()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Pvalue (entry)").setSortable(true);
        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getPValueCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Pvalue (curr)").setSortable(true);

        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getAdfPvalueEntry()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("AdfValue (entry)").setSortable(true);
        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getAdfPvalueCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("AdfValue (curr)").setSortable(true);

        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getCorrelationEntry()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Corr (entry)").setSortable(true);
        closedPairsGrid.addColumn(p -> BigDecimal.valueOf(p.getCorrelationCurrent()).setScale(2, BigDecimal.ROUND_HALF_UP)).setHeader("Corr (curr)").setSortable(true);

        closedPairsGrid.addColumn(PairData::getExitReason).setHeader("Причина выхода").setSortable(true);

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

                testTradeProcessor.testTrade(pair); //сначала обновим пару полностью

                pair.setStatus(TradeStatus.CLOSED);
                pair.setExitReason(EXIT_REASON_MANUALLY);
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

    private record StatisticRow(String name, Object today, Object total) {
    }

}