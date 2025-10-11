package com.example.core.ui.views;

import com.example.core.services.CandleCacheManagementService;
import com.example.core.ui.layout.MainLayout;
import com.example.core.ui.utils.PeriodOptions;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Вкладка "Кэш свечей" для управления кэшем свечей
 */
@Slf4j
@PageTitle("Кэш свечей")
@Route(value = "candle-cache", layout = MainLayout.class)
public class CandleCacheView extends VerticalLayout {

    private final CandleCacheManagementService candleCacheManagementService;

    // Настройки
    private ComboBox<String> exchangeSelect;
    private MultiSelectComboBox<String> timeframeSelect;
    private IntegerField threadCountField;
    private Select<String> forceLoadPeriodField;

    // Принудительная загрузка
    private TextField tickersField;
    private Button forceLoadButton;

    // Статистика
    private Grid<TimeframeStats> statsGrid;
    private Span totalCandlesSpan;
    private Span todayAddedSpan;
    private Button cleanupButton;

    // Расписания шедуллеров
    private TextField preloadScheduleField;
    private TextField dailyUpdateScheduleField;

    // Доступные таймфреймы
    private final List<String> availableTimeframes = Arrays.asList(
//            "1m",
//            "5m",
            "15m"
//            "1H",
//            "4H",
//            "1D",
//            "1W",
//            "1M"
    );

    // Доступные биржи
    private final List<String> availableExchanges = Arrays.asList(
            "OKX", "BINANCE", "BYBIT"
    );

    public CandleCacheView(CandleCacheManagementService candleCacheManagementService) {
        this.candleCacheManagementService = candleCacheManagementService;
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        createHeader();
        createSettingsSection();
        createForceLoadSection();
        createStatisticsSection();
        createSchedulerSection();

//        refreshStatistics();
    }

    private void createHeader() {
        H2 title = new H2("Управление кэшем свечей");
        title.getStyle().set("margin", "0").set("color", "var(--lumo-primary-color)");
        add(title);
    }

    private void createSettingsSection() {
        H3 settingsTitle = new H3("🔧 Настройки");
        settingsTitle.getStyle().set("margin-top", "20px").set("margin-bottom", "10px");

        // Выбор биржи
        exchangeSelect = new ComboBox<>("Биржа");
        exchangeSelect.setItems(availableExchanges);
        exchangeSelect.setValue("OKX");
        exchangeSelect.setWidth("200px");

        // Выбор таймфреймов
        timeframeSelect = new MultiSelectComboBox<>("Активные таймфреймы");
        timeframeSelect.setItems(availableTimeframes);
        timeframeSelect.setValue(new HashSet<>(Arrays.asList("15m")));
        timeframeSelect.setWidth("300px");

        // Количество потоков
        threadCountField = new IntegerField("Количество потоков загрузки");
        threadCountField.setValue(5);
        threadCountField.setMin(1);
        threadCountField.setMax(10);
        threadCountField.setWidth("200px");
        threadCountField.setStepButtonsVisible(true);

        // Период принудительной загрузки
        forceLoadPeriodField = new Select<>();
        forceLoadPeriodField.setLabel("Период");
        forceLoadPeriodField.setItems(PeriodOptions.getAll().keySet());
        forceLoadPeriodField.setValue(PeriodOptions.getDefault());
        forceLoadPeriodField.setWidth("200px");
        forceLoadPeriodField.setHelperText("Выберите период для анализа данных");

        HorizontalLayout settingsLayout = new HorizontalLayout(exchangeSelect, timeframeSelect, threadCountField, forceLoadPeriodField);
        settingsLayout.setSpacing(true);
        settingsLayout.setAlignItems(FlexComponent.Alignment.END);

        add(settingsTitle, settingsLayout);
    }

    private void createForceLoadSection() {
        H3 forceLoadTitle = new H3("⚡ Принудительная загрузка свечей");
        forceLoadTitle.getStyle().set("margin-top", "20px").set("margin-bottom", "10px");

        // Поле для тикеров
        tickersField = new TextField("Тикеры (через запятую)");
        tickersField.setPlaceholder("BTC-USDT-SWAP, ETH-USDT-SWAP или оставьте пустым для всех");
        tickersField.setWidth("400px");

        // Кнопка принудительной загрузки
        forceLoadButton = new Button("Запустить загрузку", new Icon(VaadinIcon.DOWNLOAD));
        forceLoadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        forceLoadButton.addClickListener(e -> executeForceLoad());

        HorizontalLayout forceLoadLayout = new HorizontalLayout(tickersField, forceLoadButton);
        forceLoadLayout.setSpacing(true);
        forceLoadLayout.setAlignItems(FlexComponent.Alignment.END);

        add(forceLoadTitle, forceLoadLayout);
    }

    private void createStatisticsSection() {
        H3 statsTitle = new H3("📊 Статистика кэша");
        statsTitle.getStyle().set("margin-top", "20px").set("margin-bottom", "10px");

        // Общая статистика
        totalCandlesSpan = new Span("Загружается...");
        totalCandlesSpan.getStyle().set("font-weight", "bold").set("font-size", "16px");

        todayAddedSpan = new Span("Загружается...");
        todayAddedSpan.getStyle().set("color", "var(--lumo-success-color)").set("font-size", "14px");

        Button refreshButton = new Button("Обновить", new Icon(VaadinIcon.REFRESH));
        refreshButton.addClickListener(e -> refreshStatistics());

        // Кнопка очистки неиспользуемых таймфреймов
        cleanupButton = new Button("Очистить неиспользуемые ТФ", new Icon(VaadinIcon.TRASH));
        cleanupButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        cleanupButton.addClickListener(e -> executeCleanup());
        cleanupButton.getStyle().set("margin-left", "auto");

        HorizontalLayout summaryLayout = new HorizontalLayout(
                new Div(totalCandlesSpan, new Div(todayAddedSpan)),
                refreshButton,
                cleanupButton
        );
        summaryLayout.setSpacing(true);
        summaryLayout.setAlignItems(FlexComponent.Alignment.CENTER);

        // Таблица статистики по таймфреймам
        statsGrid = new Grid<>(TimeframeStats.class, false);
        statsGrid.setHeightFull();
        statsGrid.setMinHeight("400px");
        statsGrid.setColumnReorderingAllowed(true);
        statsGrid.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("background-color", "var(--lumo-base-color)");

        // Таблица будет заполнена при вызове refreshStatistics()

        statsGrid.addColumn(TimeframeStats::getTimeframe)
                .setHeader("Таймфрейм")
                .setWidth("120px")
                .setSortable(true);

        statsGrid.addColumn(stats -> formatNumber(stats.getTotalCandles()))
                .setHeader("Всего свечей")
                .setWidth("150px")
                .setSortable(true);

        statsGrid.addColumn(stats -> formatNumber(stats.getTodayAdded()))
                .setHeader("За сегодня")
                .setWidth("120px")
                .setSortable(true);

        statsGrid.addColumn(stats -> formatNumber(stats.getAvgCandles()))
                .setHeader("Среднее")
                .setWidth("120px")
                .setSortable(true);

        statsGrid.addColumn(TimeframeStats::getLastUpdate)
                .setHeader("Последнее обновление")
                .setWidth("200px")
                .setSortable(true);

        add(statsTitle, summaryLayout, statsGrid);
    }

    private void createSchedulerSection() {
        H3 schedulerTitle = new H3("⏰ Расписания шедуллеров");
        schedulerTitle.getStyle().set("margin-top", "20px").set("margin-bottom", "10px");

        // Расписание предзагрузки
        preloadScheduleField = new TextField("Полная предзагрузка");
        preloadScheduleField.setValue("0 0 2 * * SUN"); // Каждое воскресенье в 2:00
        preloadScheduleField.setWidth("300px");
        preloadScheduleField.setPlaceholder("Cron выражение");

        // Расписание ежедневного обновления
        dailyUpdateScheduleField = new TextField("Ежедневное обновление");
        dailyUpdateScheduleField.setValue("0 */30 * * * *"); // Каждые 30 минут
        dailyUpdateScheduleField.setWidth("300px");
        dailyUpdateScheduleField.setPlaceholder("Cron выражение");

        Button saveSchedulesButton = new Button("Сохранить расписания", new Icon(VaadinIcon.CHECK));
        saveSchedulesButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        saveSchedulesButton.addClickListener(e -> saveSchedules());

        HorizontalLayout schedulerLayout = new HorizontalLayout(
                preloadScheduleField,
                dailyUpdateScheduleField,
                saveSchedulesButton
        );
        schedulerLayout.setSpacing(true);
        schedulerLayout.setAlignItems(FlexComponent.Alignment.END);

        add(schedulerTitle, schedulerLayout);
    }

    private void executeForceLoad() {
        try {
            String selectedExchange = exchangeSelect.getValue();
            Set<String> selectedTimeframes = timeframeSelect.getValue();
            String tickersInput = tickersField.getValue();
            Integer threadCount = threadCountField.getValue();
            String selectedPeriod = forceLoadPeriodField.getValue();

            if (selectedExchange == null || selectedTimeframes.isEmpty()) {
                showNotification("Выберите биржу и таймфреймы!", NotificationVariant.LUMO_ERROR);
                return;
            }

            if (selectedPeriod == null || selectedPeriod.trim().isEmpty()) {
                showNotification("Выберите период загрузки!", NotificationVariant.LUMO_ERROR);
                return;
            }

            // Конвертируем период в количество дней
            int forceLoadPeriod = PeriodOptions.calculateCandleLimit("1D", selectedPeriod);

            // Парсим тикеры
            List<String> tickersList = new ArrayList<>();
            if (tickersInput != null && !tickersInput.trim().isEmpty()) {
                tickersList = Arrays.stream(tickersInput.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            forceLoadButton.setEnabled(false);
            forceLoadButton.setText("Загружаем...");

            log.info("🚀 Принудительная загрузка: биржа={}, таймфреймы={}, тикеры={}, потоки={}, период={} ({} дней)",
                    selectedExchange, selectedTimeframes, tickersList.size(), threadCount, selectedPeriod, forceLoadPeriod);

            // Вызов сервиса для принудительной загрузки
            candleCacheManagementService.forceLoadCandles(selectedExchange, selectedTimeframes,
                    tickersList, threadCount, forceLoadPeriod);

            showNotification("Загрузка запущена! Проверьте логи для отслеживания прогресса.",
                    NotificationVariant.LUMO_SUCCESS);

            // Обновляем статистику через 5 секунд
            getUI().ifPresent(ui -> ui.access(() -> {
                try {
                    Thread.sleep(5000);
//                    refreshStatistics();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                forceLoadButton.setEnabled(true);
                forceLoadButton.setText("Запустить загрузку");
            }));

        } catch (Exception e) {
            log.error("❌ Ошибка при принудительной загрузке: {}", e.getMessage(), e);
            showNotification("Ошибка: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
            forceLoadButton.setEnabled(true);
            forceLoadButton.setText("Запустить загрузку");
        }
    }

    private void refreshStatistics() {
        try {
            String selectedExchange = exchangeSelect.getValue();
            if (selectedExchange == null) selectedExchange = "OKX";

            log.info("🔄 Обновляем статистику для биржи: {}", selectedExchange);

            // Получаем статистику из сервиса
            Map<String, Object> stats = candleCacheManagementService.getCacheStatistics(selectedExchange);
            log.info("📊 Получена статистика из сервиса: {}", stats);

            // Парсим статистику и создаем данные для таблицы
            List<TimeframeStats> parsedStats = parseStatisticsToTimeframeStats(stats);
            log.info("📈 Распарсено {} записей статистики", parsedStats.size());

            if (parsedStats.isEmpty()) {
                log.warn("⚠️ Нет данных статистики");
            }

            statsGrid.setItems(parsedStats);
            log.info("✅ Данные загружены в таблицу: {} записей", parsedStats.size());

            // Подсчитываем общую статистику
            long totalCandles = parsedStats.stream().mapToLong(TimeframeStats::getTotalCandles).sum();
            long totalTodayAdded = parsedStats.stream().mapToLong(TimeframeStats::getTodayAdded).sum();

            totalCandlesSpan.setText(String.format("Всего свечей в кэше: %s", formatNumber(totalCandles)));
            todayAddedSpan.setText(String.format("За сегодня добавлено: +%s", formatNumber(totalTodayAdded)));

            log.info("📊 Статистика обновлена: всего {} свечей, за сегодня +{}, записей в таблице: {}",
                    totalCandles, totalTodayAdded, parsedStats.size());

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении статистики: {}", e.getMessage(), e);
            showNotification("Ошибка получения статистики: " + e.getMessage(), NotificationVariant.LUMO_ERROR);

            // Очищаем таблицу при ошибке
            statsGrid.setItems();
        }
    }

    private void saveSchedules() {
        try {
            String preloadSchedule = preloadScheduleField.getValue();
            String dailySchedule = dailyUpdateScheduleField.getValue();

            if (preloadSchedule == null || preloadSchedule.trim().isEmpty() ||
                    dailySchedule == null || dailySchedule.trim().isEmpty()) {
                showNotification("Заполните оба расписания!", NotificationVariant.LUMO_ERROR);
                return;
            }

            // Сохраняем расписания через сервис
            candleCacheManagementService.saveSchedulerSettings(preloadSchedule, dailySchedule);

            showNotification("Расписания сохранены!", NotificationVariant.LUMO_SUCCESS);

        } catch (Exception e) {
            log.error("❌ Ошибка при сохранении расписаний: {}", e.getMessage(), e);
            showNotification("Ошибка сохранения: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void executeCleanup() {
        try {
            Set<String> activeTimeframes = timeframeSelect.getValue();
            String selectedExchange = exchangeSelect.getValue();

            if (activeTimeframes.isEmpty()) {
                showNotification("Выберите активные таймфреймы для сохранения!", NotificationVariant.LUMO_ERROR);
                return;
            }

            if (selectedExchange == null) {
                selectedExchange = "OKX";
            }

            // Получаем статистику для отображения информации о том, что будет удалено
            Map<String, Object> stats = candleCacheManagementService.getCacheStatistics(selectedExchange);
            List<TimeframeStats> parsedStats = parseStatisticsToTimeframeStats(stats);

            // Подсчитываем количество свечей для удаления
            long candlesToDelete = parsedStats.stream()
                    .filter(stat -> !activeTimeframes.contains(stat.getTimeframe()))
                    .mapToLong(TimeframeStats::getTotalCandles)
                    .sum();

            if (candlesToDelete == 0) {
                showNotification("Нет данных для удаления. Все таймфреймы активны.", NotificationVariant.LUMO_PRIMARY);
                return;
            }

            // Создаем модальное окно подтверждения
            showCleanupConfirmDialog(selectedExchange, activeTimeframes, candlesToDelete);

        } catch (Exception e) {
            log.error("❌ Ошибка при подготовке очистки: {}", e.getMessage(), e);
            showNotification("Ошибка: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void executeActualCleanup(String exchange, Set<String> activeTimeframes, long candlesToDelete) {
        try {
            log.info("🧹 Начинаем очистку неиспользуемых таймфреймов: биржа={}, активные ТФ={}, к удалению={} свечей",
                    exchange, activeTimeframes, candlesToDelete);

            // Здесь должен быть вызов сервиса для удаления
            candleCacheManagementService.cleanupInactiveTimeframes(exchange, activeTimeframes);

            // Пока что симулируем удаление
            showNotification(String.format("✅ Успешно удалено %s свечей неактивных таймфреймов!",
                    formatNumber(candlesToDelete)), NotificationVariant.LUMO_SUCCESS);

            // Восстанавливаем кнопку
            cleanupButton.setText("Очистить неиспользуемые ТФ");
            cleanupButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
            cleanupButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            // Обновляем статистику
//            refreshStatistics();

        } catch (Exception e) {
            log.error("❌ Ошибка при очистке кэша: {}", e.getMessage(), e);
            showNotification("Ошибка очистки: " + e.getMessage(), NotificationVariant.LUMO_ERROR);

            // Восстанавливаем кнопку при ошибке
            cleanupButton.setText("Очистить неиспользуемые ТФ");
            cleanupButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
            cleanupButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        }
    }

    @SuppressWarnings("unchecked")
    private List<TimeframeStats> parseStatisticsToTimeframeStats(Map<String, Object> stats) {
        List<TimeframeStats> result = new ArrayList<>();

        try {
            // Получаем данные из byExchange -> OKX (или выбранной биржи)
            Object byExchangeObj = stats.get("byExchange");
            Object todayByExchangeObj = stats.get("todayByExchange");

            if (byExchangeObj instanceof Map) {
                Map<String, Object> byExchangeMap = (Map<String, Object>) byExchangeObj;
                Map<String, Object> todayByExchangeMap = new HashMap<>();

                // Получаем данные за сегодня если они есть
                if (todayByExchangeObj instanceof Map) {
                    todayByExchangeMap = (Map<String, Object>) todayByExchangeObj;
                }

                String selectedExchange = exchangeSelect.getValue();
                if (selectedExchange == null) selectedExchange = "OKX";

                Object exchangeDataObj = byExchangeMap.get(selectedExchange);
                Object todayExchangeDataObj = todayByExchangeMap.get(selectedExchange);

                if (exchangeDataObj instanceof Map) {
                    Map<String, Object> exchangeData = (Map<String, Object>) exchangeDataObj;
                    Map<String, Object> todayExchangeData = new HashMap<>();

                    if (todayExchangeDataObj instanceof Map) {
                        todayExchangeData = (Map<String, Object>) todayExchangeDataObj;
                    }

                    for (Map.Entry<String, Object> entry : exchangeData.entrySet()) {
                        String timeframe = entry.getKey();
                        Object countObj = entry.getValue();

                        long totalCandles = 0;
                        if (countObj instanceof Number) {
                            totalCandles = ((Number) countObj).longValue();
                        }

                        // Получаем реальные данные за сегодня
                        long todayAdded = 0;
                        Object todayCountObj = todayExchangeData.get(timeframe);
                        if (todayCountObj instanceof Number) {
                            todayAdded = ((Number) todayCountObj).longValue();
                        }

                        long avgCandles = totalCandles > 0 ? totalCandles / Math.max(1, getEstimatedTickerCount()) : 0;

                        result.add(new TimeframeStats(timeframe, totalCandles, todayAdded, avgCandles, getCurrentTime()));
                    }
                }
            }

        } catch (Exception e) {
            log.warn("⚠️ Ошибка при парсинге статистики: {}", e.getMessage());
            // В случае ошибки возвращаем пустой список
        }

        // Сортируем по таймфреймам для лучшего отображения
        result.sort((a, b) -> {
            List<String> order = Arrays.asList("1m", "5m", "15m", "1H", "4H", "1D", "1W", "1M");
            int indexA = order.indexOf(a.getTimeframe());
            int indexB = order.indexOf(b.getTimeframe());
            return Integer.compare(indexA != -1 ? indexA : 999, indexB != -1 ? indexB : 999);
        });

        return result;
    }


    private String getCurrentTime() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private int getEstimatedTickerCount() {
        return 100; // Примерное количество тикеров для расчета среднего
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        } else {
            return String.valueOf(number);
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }

    /**
     * Показывает модальное окно подтверждения для очистки неактивных таймфреймов
     */
    private void showCleanupConfirmDialog(String exchange, Set<String> activeTimeframes, long candlesToDelete) {
        // Формируем список неактивных таймфреймов для отображения
        List<String> allTimeframes = Arrays.asList("1m", "5m", "15m", "1H", "4H", "1D", "1W", "1M");
        List<String> inactiveTimeframes = allTimeframes.stream()
                .filter(tf -> !activeTimeframes.contains(tf))
                .collect(Collectors.toList());

        // Создаем детальное описание
        String inactiveTimeframesText = inactiveTimeframes.isEmpty() ? "Нет" : String.join(", ", inactiveTimeframes);
        String activeTimeframesText = String.join(", ", activeTimeframes);

        // Создаем модальное окно
        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setHeader("🗑️ Подтверждение очистки кэша");

        // Создаем детальное содержимое диалога
        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        Span warningText = new Span("⚠️ ВНИМАНИЕ! Эта операция необратима!");
        warningText.getStyle().set("color", "var(--lumo-error-color)")
                .set("font-weight", "bold")
                .set("font-size", "16px");

        Div detailsDiv = new Div();
        detailsDiv.add(
                new Span("Биржа: " + exchange),
                new Div(),
                new Span("Активные таймфреймы (сохранятся): " + activeTimeframesText),
                new Div(),
                new Span("Неактивные таймфреймы (будут удалены): " + inactiveTimeframesText),
                new Div(),
                new Span("Количество свечей к удалению: " + formatNumber(candlesToDelete))
        );
        detailsDiv.getStyle().set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin", "var(--lumo-space-s) 0");

        content.add(warningText, detailsDiv);
        confirmDialog.setText(content);

        // Настраиваем кнопки
        confirmDialog.setCancelable(true);
        confirmDialog.setCancelText("Отмена");
        confirmDialog.setConfirmText("Удалить " + formatNumber(candlesToDelete) + " свечей");
        confirmDialog.setConfirmButtonTheme("error primary");

        // Обработчики событий
        confirmDialog.addConfirmListener(event -> {
            executeActualCleanup(exchange, activeTimeframes, candlesToDelete);
        });

        confirmDialog.addCancelListener(event -> {
            showNotification("Очистка отменена", NotificationVariant.LUMO_PRIMARY);
        });

        // Показываем диалог
        confirmDialog.open();
    }

    /**
     * Класс для представления статистики по таймфрейму
     */
    public static class TimeframeStats {
        private String timeframe;
        private long totalCandles;
        private long todayAdded;
        private long avgCandles;
        private String lastUpdate;

        public TimeframeStats(String timeframe, long totalCandles, long todayAdded, long avgCandles, String lastUpdate) {
            this.timeframe = timeframe;
            this.totalCandles = totalCandles;
            this.todayAdded = todayAdded;
            this.avgCandles = avgCandles;
            this.lastUpdate = lastUpdate;
        }

        // Геттеры
        public String getTimeframe() {
            return timeframe;
        }

        public long getTotalCandles() {
            return totalCandles;
        }

        public long getTodayAdded() {
            return todayAdded;
        }

        public long getAvgCandles() {
            return avgCandles;
        }

        public String getLastUpdate() {
            return lastUpdate;
        }
    }
}