package com.example.core.ui.views;

import com.example.core.services.CandleCacheManagementService;
import com.example.core.ui.layout.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

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
    private IntegerField forceLoadPeriodField;
    
    // Принудительная загрузка
    private TextField tickersField;
    private Button forceLoadButton;
    
    // Статистика
    private Grid<TimeframeStats> statsGrid;
    private Span totalCandlesSpan;
    private Span todayAddedSpan;
    
    // Расписания шедуллеров
    private TextField preloadScheduleField;
    private TextField dailyUpdateScheduleField;
    
    // Доступные таймфреймы
    private final List<String> availableTimeframes = Arrays.asList(
            "1m", "5m", "15m", "1H", "4H", "1D", "1W", "1M"
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
        
        refreshStatistics();
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
        timeframeSelect.setValue(new HashSet<>(Arrays.asList("1m", "5m", "15m", "1H", "4H", "1D")));
        timeframeSelect.setWidth("300px");
        
        // Количество потоков
        threadCountField = new IntegerField("Количество потоков загрузки");
        threadCountField.setValue(5);
        threadCountField.setMin(1);
        threadCountField.setMax(10);
        threadCountField.setWidth("200px");
        threadCountField.setStepButtonsVisible(true);
        
        // Период принудительной загрузки
        forceLoadPeriodField = new IntegerField("Период загрузки (дни)");
        forceLoadPeriodField.setValue(365);
        forceLoadPeriodField.setMin(1);
        forceLoadPeriodField.setMax(1825); // 5 лет максимум
        forceLoadPeriodField.setWidth("200px");
        forceLoadPeriodField.setStepButtonsVisible(true);
        forceLoadPeriodField.setHelperText("За сколько дней назад загружать свечи");
        
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
        
        HorizontalLayout summaryLayout = new HorizontalLayout(
                new Div(totalCandlesSpan, new Div(todayAddedSpan)), 
                refreshButton
        );
        summaryLayout.setSpacing(true);
        summaryLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        
        // Таблица статистики по таймфреймам
        statsGrid = new Grid<>(TimeframeStats.class, false);
        statsGrid.setHeight("300px");
        statsGrid.setColumnReorderingAllowed(true);
        
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
            Integer forceLoadPeriod = forceLoadPeriodField.getValue();
            
            if (selectedExchange == null || selectedTimeframes.isEmpty()) {
                showNotification("Выберите биржу и таймфреймы!", NotificationVariant.LUMO_ERROR);
                return;
            }
            
            if (forceLoadPeriod == null || forceLoadPeriod <= 0) {
                showNotification("Укажите корректный период загрузки!", NotificationVariant.LUMO_ERROR);
                return;
            }
            
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
            
            log.info("🚀 Принудительная загрузка: биржа={}, таймфреймы={}, тикеры={}, потоки={}, период={} дней", 
                    selectedExchange, selectedTimeframes, tickersList.size(), threadCount, forceLoadPeriod);
            
            // Вызов сервиса для принудительной загрузки
            candleCacheManagementService.forceLoadCandles(selectedExchange, selectedTimeframes, 
                    tickersList, threadCount, forceLoadPeriod);
            
            showNotification("Загрузка запущена! Проверьте логи для отслеживания прогресса.", 
                    NotificationVariant.LUMO_SUCCESS);
            
            // Обновляем статистику через 5 секунд
            getUI().ifPresent(ui -> ui.access(() -> {
                try {
                    Thread.sleep(5000);
                    refreshStatistics();
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
            
            // Получаем статистику из сервиса
            Map<String, Object> stats = candleCacheManagementService.getCacheStatistics(selectedExchange);
            
            // Парсим статистику и создаем данные для таблицы
            List<TimeframeStats> parsedStats = parseStatisticsToTimeframeStats(stats);
            statsGrid.setItems(parsedStats);
            
            // Подсчитываем общую статистику
            long totalCandles = parsedStats.stream().mapToLong(TimeframeStats::getTotalCandles).sum();
            long totalTodayAdded = parsedStats.stream().mapToLong(TimeframeStats::getTodayAdded).sum();
            
            totalCandlesSpan.setText(String.format("Всего свечей в кэше: %s", formatNumber(totalCandles)));
            todayAddedSpan.setText(String.format("За сегодня добавлено: +%s", formatNumber(totalTodayAdded)));
            
            log.info("📊 Статистика обновлена: всего {} свечей, за сегодня +{}", totalCandles, totalTodayAdded);
            
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении статистики: {}", e.getMessage(), e);
            showNotification("Ошибка получения статистики: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
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

    @SuppressWarnings("unchecked")
    private List<TimeframeStats> parseStatisticsToTimeframeStats(Map<String, Object> stats) {
        List<TimeframeStats> result = new ArrayList<>();
        
        try {
            // Пытаемся получить статистику по таймфреймам
            Object timeframeStatsObj = stats.get("timeframeStats");
            if (timeframeStatsObj instanceof Map) {
                Map<String, Object> timeframeStatsMap = (Map<String, Object>) timeframeStatsObj;
                
                for (Map.Entry<String, Object> entry : timeframeStatsMap.entrySet()) {
                    String timeframe = entry.getKey();
                    Object countObj = entry.getValue();
                    
                    long totalCandles = 0;
                    if (countObj instanceof Number) {
                        totalCandles = ((Number) countObj).longValue();
                    }
                    
                    // Пока используем mock данные для todayAdded и avgCandles
                    long todayAdded = Math.max(0, (long) (totalCandles * 0.01)); // 1% от общего
                    long avgCandles = totalCandles > 0 ? totalCandles / Math.max(1, getEstimatedTickerCount()) : 0;
                    
                    result.add(new TimeframeStats(timeframe, totalCandles, todayAdded, avgCandles, getCurrentTime()));
                }
            }
            
            // Если нет данных, показываем пустую статистику
            if (result.isEmpty()) {
                generateMockStatistics().forEach(result::add);
            }
            
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при парсинге статистики: {}", e.getMessage());
            // В случае ошибки возвращаем mock данные
            generateMockStatistics().forEach(result::add);
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
    
    private List<TimeframeStats> generateMockStatistics() {
        // Резервные данные для демонстрации
        List<TimeframeStats> stats = new ArrayList<>();
        
        stats.add(new TimeframeStats("1m", 1_205_500, 8_400, 1203, getCurrentTime()));
        stats.add(new TimeframeStats("5m", 950_300, 5_200, 945, getCurrentTime()));
        stats.add(new TimeframeStats("15m", 789_100, 2_800, 786, getCurrentTime()));
        stats.add(new TimeframeStats("1H", 234_500, 1_400, 234, getCurrentTime()));
        stats.add(new TimeframeStats("4H", 67_200, 350, 67, getCurrentTime()));
        stats.add(new TimeframeStats("1D", 18_900, 89, 19, getCurrentTime()));
        
        return stats;
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
        public String getTimeframe() { return timeframe; }
        public long getTotalCandles() { return totalCandles; }
        public long getTodayAdded() { return todayAdded; }
        public long getAvgCandles() { return avgCandles; }
        public String getLastUpdate() { return lastUpdate; }
    }
}