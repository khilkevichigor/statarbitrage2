package com.example.core.ui.views;

import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.services.PairService;
import com.example.core.services.StablePairsScreenerSettingsService;
import com.example.core.ui.components.ZScoreChartDialog;
import com.example.core.ui.layout.MainLayout;
import com.example.shared.models.Pair;
import com.example.shared.models.StablePairsScreenerSettings;
import com.example.shared.utils.TimeFormatterUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Скриннер стабильных коинтегрированных пар
 */
@Slf4j
@PageTitle("Стабильные пары")
@Route(value = "stable-pairs", layout = MainLayout.class)
public class StablePairsView extends VerticalLayout {

    private final PairService pairService;
    private final ZScoreChartDialog zScoreChartDialog;
    private final StablePairsScreenerSettingsService settingsService;

    // Элементы формы поиска - мульти-селекты
    private MultiSelectComboBox<String> timeframeMultiSelect;
    private MultiSelectComboBox<String> periodMultiSelect;

    // Доступные варианты
    private final List<String> availableTimeframes = Arrays.asList(
            "1m", "5m", "15m", "1H", "4H", "1D", "1W", "1M"
    );
    private final List<String> availablePeriods = Arrays.asList(
            "день", "неделя", "месяц", "6 месяцев", "1 год", "2 года", "3 года"
    );
    private Checkbox minCorrelationEnabled;
    private NumberField minCorrelationField;
    private Checkbox minWindowSizeEnabled;
    private NumberField minWindowSizeField;
    private Checkbox maxAdfValueEnabled;
    private NumberField maxAdfValueField;
    private Checkbox minRSquaredEnabled;
    private NumberField minRSquaredField;
    private Checkbox maxPValueEnabled;
    private NumberField maxPValueField;

    // Новое поле для фильтрации по минимальному объему
    private Checkbox minVolumeEnabled;
    private NumberField minVolumeField;

    // Новое поле для фильтрации по тикерам
    private Checkbox searchTickersEnabled;
    private TextArea searchTickersField;
    
    // Чекбокс для использования кэша
    private Checkbox useCacheCheckbox;

    private Button searchButton;
    private Button clearAllButton;
    private ProgressBar progressBar;

    // Новые элементы для настроек
    private Checkbox runOnScheduleCheckbox;
    private Button saveSettingsButton;
    private Button loadSettingsButton;
    private ComboBox<StablePairsScreenerSettings> savedSettingsCombo;

    // Таблицы
    private Grid<Pair> foundPairsGrid;
    private Grid<Pair> monitoringPairsGrid;

    // Статистика
    private Span statsLabel;

    public StablePairsView(PairService pairService, ZScoreChartDialog zScoreChartDialog,
                           StablePairsScreenerSettingsService settingsService) {
        this.pairService = pairService;
        this.zScoreChartDialog = zScoreChartDialog;
        this.settingsService = settingsService;

        initializeLayout();
        loadData();
    }

    private void initializeLayout() {
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        add(
                createTitle(),
                createSearchForm(),
                createProgressSection(),
                createFoundPairsSection(),
                createMonitoringSection()
        );
    }

    private H2 createTitle() {
        H2 title = new H2("🔍 Скриннер стабильных пар");
        title.getStyle().set("margin", "0 0 20px 0");
        return title;
    }

    private VerticalLayout createSearchForm() {
        VerticalLayout formLayout = new VerticalLayout();
        formLayout.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius)")
                .set("padding", "20px")
                .set("background", "var(--lumo-base-color)");

        H3 formTitle = new H3("Настройки поиска");
        formTitle.getStyle().set("margin-top", "0");

        // Первая строка: Таймфрейм и Период
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setAlignItems(FlexComponent.Alignment.END);
        row1.setWidthFull();

        timeframeMultiSelect = new MultiSelectComboBox<>("Таймфрейм");
        timeframeMultiSelect.setItems(availableTimeframes);
        timeframeMultiSelect.setValue(new HashSet<>(Arrays.asList("15m"))); //by default
        timeframeMultiSelect.setWidth("250px");

        periodMultiSelect = new MultiSelectComboBox<>("Период");
        periodMultiSelect.setItems(availablePeriods);
        periodMultiSelect.setValue(new HashSet<>(Arrays.asList("1 год"))); //by default
        periodMultiSelect.setWidth("250px");

        row1.add(timeframeMultiSelect, periodMultiSelect);

        // Вторая строка: Фильтрация по тикерам
        HorizontalLayout row2 = createSearchTickersRow();

        // Третья строка: Настройки фильтров
        HorizontalLayout row3 = createFilterRow1();
        HorizontalLayout row4 = createFilterRow2();

        // Пятая строка: Настройки автоматизации
        HorizontalLayout row5 = createAutomationRow();

        // Кнопки
        HorizontalLayout buttonRow = new HorizontalLayout();
        buttonRow.setAlignItems(FlexComponent.Alignment.CENTER);

        searchButton = new Button("Искать стабильные пары", VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        //todo сделать асинхронно через кролика - отправляем эвент в cointegration, он считает и кидает свой эвент с результатами или пишет в бд и кидает эвент что бы мы забрали с бд
        searchButton.addClickListener(e -> performSearch());

        clearAllButton = new Button("Очистить все", VaadinIcon.TRASH.create());
        clearAllButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_CONTRAST);
        clearAllButton.addClickListener(e -> clearAllResults());

        saveSettingsButton = new Button("Сохранить настройки", VaadinIcon.DOWNLOAD.create());
        saveSettingsButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        saveSettingsButton.addClickListener(e -> saveCurrentSettings());

        loadSettingsButton = new Button("Загрузить настройки", VaadinIcon.UPLOAD.create());
        loadSettingsButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        loadSettingsButton.addClickListener(e -> loadSelectedSettings());

        buttonRow.add(searchButton, clearAllButton, saveSettingsButton, loadSettingsButton);

        formLayout.add(formTitle, row1, row2, row3, row4, row5, buttonRow);
        return formLayout;
    }

    private HorizontalLayout createSearchTickersRow() {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.START);
        row.setWidthFull();

        // Чекбокс для включения фильтрации по тикерам
        searchTickersEnabled = new Checkbox("Искать для");
        searchTickersEnabled.setValue(false);
        searchTickersEnabled.getStyle().set("align-self", "flex-start");
        searchTickersEnabled.getStyle().set("margin-top", "8px");

        // TextArea для ввода инструментов
        searchTickersField = new TextArea();
        searchTickersField.setPlaceholder("Введите названия инструментов через запятую (например: BTC-USDT-SWAP,ETH-USDT-SWAP,BTCUSDT,ETHUSDT)");
        searchTickersField.setWidth("500px");
        searchTickersField.setHeight("80px");
        searchTickersField.setEnabled(searchTickersEnabled.getValue());
        searchTickersField.getStyle().set("font-family", "monospace");

        // Связываем чекбокс с полем
        searchTickersEnabled.addValueChangeListener(e -> {
            searchTickersField.setEnabled(e.getValue());
            if (!e.getValue()) {
                searchTickersField.clear();
            }
        });

        // Добавляем валидацию для инструментов
        searchTickersField.addValueChangeListener(e -> {
            String value = e.getValue();
            if (value != null && !value.trim().isEmpty()) {
                // Очищаем и нормализуем названия инструментов
                String normalized = Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .filter(s -> !s.isEmpty())
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");

                if (!normalized.equals(value)) {
                    searchTickersField.setValue(normalized);
                }
            }
        });

        row.add(searchTickersEnabled, searchTickersField);
        return row;
    }

    private HorizontalLayout createFilterRow1() {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.END);
        row.setWidthFull();

        minCorrelationEnabled = new Checkbox("Min Correlation");
        minCorrelationEnabled.setValue(true);
        minCorrelationField = new NumberField();
        minCorrelationField.setValue(0.1);
        minCorrelationField.setWidth("120px");
        minCorrelationField.setEnabled(minCorrelationEnabled.getValue());
        minCorrelationEnabled.addValueChangeListener(e -> minCorrelationField.setEnabled(e.getValue()));

        minWindowSizeEnabled = new Checkbox("Min Window Size");
        minWindowSizeEnabled.setValue(true);
        minWindowSizeField = new NumberField();
        minWindowSizeField.setValue(100.0);
        minWindowSizeField.setWidth("120px");
        minWindowSizeField.setEnabled(minWindowSizeEnabled.getValue());
        minWindowSizeEnabled.addValueChangeListener(e -> minWindowSizeField.setEnabled(e.getValue()));

        maxAdfValueEnabled = new Checkbox("Max ADF Value");
        maxAdfValueEnabled.setValue(true);
        maxAdfValueField = new NumberField();
        maxAdfValueField.setValue(0.1);
        maxAdfValueField.setWidth("120px");
        maxAdfValueField.setEnabled(maxAdfValueEnabled.getValue());
        maxAdfValueEnabled.addValueChangeListener(e ->
                maxAdfValueField.setEnabled(e.getValue()));

        HorizontalLayout corrGroup = new HorizontalLayout(minCorrelationEnabled, minCorrelationField);
        corrGroup.setSpacing(false);
        corrGroup.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout windowGroup = new HorizontalLayout(minWindowSizeEnabled, minWindowSizeField);
        windowGroup.setSpacing(false);
        windowGroup.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout adfGroup = new HorizontalLayout(maxAdfValueEnabled, maxAdfValueField);
        adfGroup.setSpacing(false);
        adfGroup.setAlignItems(FlexComponent.Alignment.END);

        row.add(corrGroup, windowGroup, adfGroup);
        return row;
    }

    private HorizontalLayout createFilterRow2() {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.END);
        row.setWidthFull();

        minRSquaredEnabled = new Checkbox("Min R2");
        minRSquaredEnabled.setValue(true);
        minRSquaredField = new NumberField();
        minRSquaredField.setValue(0.1);
        minRSquaredField.setWidth("120px");
        minRSquaredField.setEnabled(minRSquaredEnabled.getValue());
        minRSquaredEnabled.addValueChangeListener(e -> minRSquaredField.setEnabled(e.getValue()));

        maxPValueEnabled = new Checkbox("Max P-Value");
        maxPValueEnabled.setValue(true);
        maxPValueField = new NumberField();
        maxPValueField.setValue(0.1);
        maxPValueField.setWidth("120px");
        maxPValueField.setEnabled(maxPValueEnabled.getValue());
        maxPValueEnabled.addValueChangeListener(e -> maxPValueField.setEnabled(e.getValue()));

        // Фильтр по минимальному объему (переиспользуем код из настроек)
        minVolumeEnabled = new Checkbox("Min Vol (млн $)");
        minVolumeEnabled.setValue(false);
        minVolumeField = new NumberField();
        minVolumeField.setValue(1.0);
        minVolumeField.setStep(1.0);
        minVolumeField.setMin(0.0);
        minVolumeField.setStepButtonsVisible(true);
        minVolumeField.setWidth("120px");
        minVolumeField.setEnabled(minVolumeEnabled.getValue());
        minVolumeEnabled.addValueChangeListener(e -> minVolumeField.setEnabled(e.getValue()));

        HorizontalLayout rSquaredGroup = new HorizontalLayout(minRSquaredEnabled, minRSquaredField);
        rSquaredGroup.setSpacing(false);
        rSquaredGroup.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout pValueGroup = new HorizontalLayout(maxPValueEnabled, maxPValueField);
        pValueGroup.setSpacing(false);
        pValueGroup.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout minVolumeGroup = new HorizontalLayout(minVolumeEnabled, minVolumeField);
        minVolumeGroup.setSpacing(false);
        minVolumeGroup.setAlignItems(FlexComponent.Alignment.END);
        
        // Чекбокс для использования кэша
        useCacheCheckbox = new Checkbox("Использовать КЭШ");
        useCacheCheckbox.setValue(true);
        useCacheCheckbox.getElement().setAttribute("title", "Использовать кэшированные свечи из базы данных. Если выключено - загружать свечи напрямую с OKX (может занять несколько часов)");

        row.add(rSquaredGroup, pValueGroup, minVolumeGroup, useCacheCheckbox);
        return row;
    }

    private HorizontalLayout createAutomationRow() {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.END);
        row.setWidthFull();

        runOnScheduleCheckbox = new Checkbox("Запускать по расписанию");
        runOnScheduleCheckbox.setValue(false);
        runOnScheduleCheckbox.getElement().setAttribute("title", "Включить автоматический поиск каждую ночь в 2:00");

        savedSettingsCombo = new ComboBox<>("Сохранённые настройки");
        savedSettingsCombo.setItemLabelGenerator(StablePairsScreenerSettings::getName);
        savedSettingsCombo.setWidth("300px");

        // Загружаем существующие настройки при инициализации
        loadAvailableSettings();

        row.add(runOnScheduleCheckbox, savedSettingsCombo);
        return row;
    }

    private VerticalLayout createProgressSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);

        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidthFull();

        statsLabel = new Span();
        statsLabel.getStyle().set("font-size", "var(--lumo-font-size-s)");

        section.add(progressBar, statsLabel);
        return section;
    }

    private VerticalLayout createFoundPairsSection() {
        VerticalLayout section = new VerticalLayout();
        section.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius)")
                .set("padding", "15px");

        H3 title = new H3("📊 Найденные стабильные пары");
        title.getStyle().set("margin", "0 0 15px 0");

        foundPairsGrid = createFoundPairsGrid();

        section.add(title, foundPairsGrid);
        return section;
    }

    private VerticalLayout createMonitoringSection() {
        VerticalLayout section = new VerticalLayout();
        section.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius)")
                .set("padding", "15px");

        H3 title = new H3("👁️ Постоянный список мониторинга");
        title.getStyle().set("margin", "0 0 15px 0");

        monitoringPairsGrid = createMonitoringPairsGrid();

        section.add(title, monitoringPairsGrid);
        return section;
    }

    private Grid<Pair> createFoundPairsGrid() {
        Grid<Pair> grid = new Grid<>(Pair.class, false);
        grid.setHeight("300px");
        grid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_ROW_STRIPES);

        // Основные колонки
        grid.addColumn(Pair::getPairName).setHeader("Пара").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        Grid.Column<Pair> scoreColumn = grid.addColumn(new TextRenderer<>(pair -> pair.getTotalScore() != null ? pair.getTotalScore().toString() : "-"));
        scoreColumn.setHeader("Скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        scoreColumn.setComparator((pair1, pair2) -> {
            Integer score1 = pair1.getTotalScore();
            Integer score2 = pair2.getTotalScore();
            if (score1 == null && score2 == null) return 0;
            if (score1 == null) return 1; // null values go to end
            if (score2 == null) return -1; // null values go to end
            return score2.compareTo(score1); // Descending order (higher scores first)
        });
        grid.addColumn(Pair::getStabilityRating).setHeader("Рейтинг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.isTradeable() ? "Да" : "Нет")).setHeader("Торгуемая").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getDataPoints() != null ? pair.getDataPoints().toString() : "-")).setHeader("Точки").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getCandleCount() != null ? pair.getCandleCount().toString() : "-")).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(Pair::getTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(Pair::getPeriod).setHeader("Период").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getSearchDate() != null ? TimeFormatterUtil.formatDateTime(pair.getSearchDate()) : "-")).setHeader("Дата поиска").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        // Колонка действий
        grid.addColumn(new ComponentRenderer<>(this::createFoundPairActions)).setHeader("Действия").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        return grid;
    }

    private Grid<Pair> createMonitoringPairsGrid() {
        Grid<Pair> grid = new Grid<>(Pair.class, false);
        grid.setHeight("200px");
        grid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_ROW_STRIPES);

        // Основные колонки
        grid.addColumn(Pair::getPairName).setHeader("Пара").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        Grid.Column<Pair> scoreColumnMonitoring = grid.addColumn(new TextRenderer<>(pair -> pair.getTotalScore() != null ? pair.getTotalScore().toString() : "-"));
        scoreColumnMonitoring.setHeader("Скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        scoreColumnMonitoring.setComparator((pair1, pair2) -> {
            Integer score1 = pair1.getTotalScore();
            Integer score2 = pair2.getTotalScore();
            if (score1 == null && score2 == null) return 0;
            if (score1 == null) return 1; // null values go to end
            if (score2 == null) return -1; // null values go to end
            return score2.compareTo(score1); // Descending order (higher scores first)
        });
        grid.addColumn(Pair::getStabilityRating).setHeader("Рейтинг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getCandleCount() != null ? pair.getCandleCount().toString() : "-")).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(Pair::getTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(Pair::getPeriod).setHeader("Период").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getCreatedAt() != null ? TimeFormatterUtil.formatDateTime(pair.getCreatedAt()) : "-")).setHeader("Добавлена").setAutoWidth(true).setFlexGrow(0);
        // Колонка действий
        grid.addColumn(new ComponentRenderer<>(this::createMonitoringPairActions)).setHeader("Действия").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        return grid;
    }

    private HorizontalLayout createFoundPairActions(Pair pair) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.setSpacing(true);

        Button addButton = new Button("Добавить", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        addButton.addClickListener(e -> addToMonitoring(pair));

        Button addTickersButton = new Button("Добавить тикеры", VaadinIcon.TAGS.create());
        addTickersButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        addTickersButton.getElement().setAttribute("title", "Добавить инструменты пары в поле поиска");
        addTickersButton.addClickListener(e -> addTickersToSearch(pair));

        Button chartButton = new Button(VaadinIcon.LINE_CHART.create());
        chartButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        chartButton.getElement().setAttribute("title", "Рассчитать Z-Score и показать график");
        chartButton.addClickListener(e -> calculateZScore(pair));

        Button deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        deleteButton.getElement().setAttribute("title", "Удалить");
        deleteButton.addClickListener(e -> deleteFoundPair(pair));

        actions.add(addButton, addTickersButton, chartButton, deleteButton);
        return actions;
    }

    private HorizontalLayout createMonitoringPairActions(Pair pair) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.setSpacing(true);

        Button chartButton = new Button(VaadinIcon.LINE_CHART.create());
        chartButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        chartButton.getElement().setAttribute("title", "Рассчитать Z-Score и показать график");
        chartButton.addClickListener(e -> calculateZScore(pair));

        Button removeButton = new Button(VaadinIcon.MINUS.create());
        removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        removeButton.getElement().setAttribute("title", "Удалить из мониторинга");
        removeButton.addClickListener(e -> removeFromMonitoring(pair));

        actions.add(chartButton, removeButton);
        return actions;
    }

    private void performSearch() {
        searchButton.setEnabled(false);
        progressBar.setVisible(true);

        try {
            Set<String> timeframes = timeframeMultiSelect.getValue();
            Set<String> periods = periodMultiSelect.getValue();

            if (timeframes.isEmpty()) {
                Notification.show("❌ Выберите хотя бы один таймфрейм", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            if (periods.isEmpty()) {
                Notification.show("❌ Выберите хотя бы один период", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            Map<String, Object> searchSettings = buildSearchSettings();

            log.info("🔍 Запуск поиска стабильных пар: TF={}, Period={}", timeframes, periods);

            // Выполняем поиск в фоновом потоке
            getUI().ifPresent(ui -> {
                Thread searchThread = new Thread(() -> {
                    try {
                        StabilityResponseDto response = pairService.searchStablePairs(
                                timeframes, periods, searchSettings);

                        ui.access(() -> {
                            progressBar.setVisible(false);
                            searchButton.setEnabled(true);

                            if (response.getSuccess()) {
                                Notification.show(
                                                String.format("✅ Поиск завершен! Найдено %d торгуемых пар из %d проанализированных",
                                                        response.getTradeablePairsFound(),
                                                        response.getTotalPairsAnalyzed()),
                                                5000, Notification.Position.TOP_CENTER)
                                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                                loadFoundPairs();
                                updateStatistics();
                            } else {
                                Notification.show("❌ Ошибка при поиске пар", 3000, Notification.Position.TOP_CENTER)
                                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                            }
                        });

                    } catch (Exception e) {
                        log.error("Ошибка при поиске пар: {}", e.getMessage(), e);
                        ui.access(() -> {
                            progressBar.setVisible(false);
                            searchButton.setEnabled(true);
                            Notification.show("❌ Ошибка: " + e.getMessage(), 5000, Notification.Position.TOP_CENTER)
                                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        });
                    }
                });
                searchThread.start();
            });

        } catch (Exception e) {
            log.error("Ошибка при инициации поиска: {}", e.getMessage(), e);
            progressBar.setVisible(false);
            searchButton.setEnabled(true);
            Notification.show("❌ Ошибка: " + e.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private Map<String, Object> buildSearchSettings() {
        Map<String, Object> settings = new HashMap<>();

        if (minCorrelationEnabled.getValue() && minCorrelationField.getValue() != null) {
            settings.put("minCorrelation", minCorrelationField.getValue());
        }
        if (minWindowSizeEnabled.getValue() && minWindowSizeField.getValue() != null) {
            settings.put("minWindowSize", minWindowSizeField.getValue().intValue());
        }
        if (maxAdfValueEnabled.getValue() && maxAdfValueField.getValue() != null) {
            settings.put("maxAdfValue", maxAdfValueField.getValue());
        }
        if (minRSquaredEnabled.getValue() && minRSquaredField.getValue() != null) {
            settings.put("minRSquared", minRSquaredField.getValue());
        }
        if (maxPValueEnabled.getValue() && maxPValueField.getValue() != null) {
            settings.put("maxPValue", maxPValueField.getValue());
        }

        // Добавляем фильтрацию по минимальному объему
        if (minVolumeEnabled.getValue() && minVolumeField.getValue() != null) {
            settings.put("minVolume", minVolumeField.getValue());
            log.info("💰 Добавлен фильтр по минимальному объему: {} млн $", minVolumeField.getValue());
        }

        // Добавляем фильтрацию по тикерам
        if (searchTickersEnabled.getValue() && searchTickersField.getValue() != null && !searchTickersField.getValue().trim().isEmpty()) {
            Set<String> tickers = getSearchTickersSet();
            settings.put("searchTickers", tickers);
            log.info("🎯 Добавлен фильтр по тикерам: {}", tickers);
        }
        
        // Добавляем настройку использования кэша
        boolean useCache = useCacheCheckbox.getValue();
        settings.put("useCache", useCache);
        log.info("💾 Использование кэша: {}", useCache ? "включено" : "выключено");

        return settings;
    }

    /**
     * Получить набор инструментов из UI поля
     */
    private Set<String> getSearchTickersSet() {
        if (searchTickersField.getValue() == null || searchTickersField.getValue().trim().isEmpty()) {
            return new HashSet<>();
        }

        Set<String> instruments = new HashSet<>();
        String[] instrumentArray = searchTickersField.getValue().split(",");
        for (String instrument : instrumentArray) {
            String trimmed = instrument.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                instruments.add(trimmed);
            }
        }
        return instruments;
    }

    private void clearAllResults() {
        ConfirmDialog dialog = new ConfirmDialog(
                "Очистка результатов",
                "Вы уверены, что хотите удалить все найденные пары? " +
                        "Пары в мониторинге НЕ будут удалены.",
                "Очистить", event -> {
            try {
                int deletedCount = pairService.clearAllFoundPairs();
                Notification.show(
                                String.format("🧹 Удалено %d найденных пар", deletedCount),
                                3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadFoundPairs();
                updateStatistics();
            } catch (Exception e) {
                log.error("Ошибка при очистке пар: {}", e.getMessage(), e);
                Notification.show("❌ Ошибка при очистке: " + e.getMessage(),
                                3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        },
                "Отмена", event -> {
        });
        dialog.open();
    }

    private void addToMonitoring(Pair pair) {
        try {
            pairService.addToMonitoring(pair.getId());
            Notification.show(
                            String.format("➕ Пара %s добавлена в мониторинг", pair.getPairName()),
                            3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadData(); // Перезагружаем обе таблицы
        } catch (Exception e) {
            log.error("Ошибка при добавлении в мониторинг: {}", e.getMessage(), e);
            Notification.show("❌ Ошибка: " + e.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void removeFromMonitoring(Pair pair) {
        try {
            pairService.removeFromMonitoring(pair.getId());
            Notification.show(
                            String.format("➖ Пара %s удалена из мониторинга", pair.getPairName()),
                            3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadData(); // Перезагружаем обе таблицы
        } catch (Exception e) {
            log.error("Ошибка при удалении из мониторинга: {}", e.getMessage(), e);
            Notification.show("❌ Ошибка: " + e.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteFoundPair(Pair pair) {
        ConfirmDialog dialog = new ConfirmDialog(
                "Удаление пары",
                String.format("Вы уверены, что хотите удалить пару %s?", pair.getPairName()),
                "Удалить", event -> {
            try {
                pairService.deleteFoundPair(pair.getId());
                Notification.show(
                                String.format("🗑️ Пара %s удалена", pair.getPairName()),
                                3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadFoundPairs();
                updateStatistics();
            } catch (Exception e) {
                log.error("Ошибка при удалении пары: {}", e.getMessage(), e);
                Notification.show("❌ Ошибка: " + e.getMessage(),
                                3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        },
                "Отмена", event -> {
        });
        dialog.open();
    }

    private void addTickersToSearch(Pair pair) {
        try {
            // Извлекаем тикеры из названия пары
            String pairName = pair.getPairName();
            if (pairName == null || pairName.trim().isEmpty()) {
                Notification.show("❌ Не удалось получить название пары", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // Извлекаем полные названия инструментов из пары
            Set<String> instruments = extractInstrumentsFromPairName(pairName);

            if (instruments.isEmpty()) {
                Notification.show("❌ Не удалось извлечь инструменты из названия пары", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // Включаем фильтр по тикерам
            searchTickersEnabled.setValue(true);
            searchTickersField.setEnabled(true);

            // Добавляем новые инструменты к существующим
            Set<String> existingInstruments = getSearchTickersSet();
            existingInstruments.addAll(instruments);

            // Обновляем поле с инструментами
            String instrumentsString = String.join(",", existingInstruments);
            searchTickersField.setValue(instrumentsString);

            log.info("📝 Добавлены инструменты из пары {}: {}", pairName, instruments);
            Notification.show(
                            String.format("✅ Добавлены инструменты из пары %s: %s", pairName, String.join(", ", instruments)),
                            3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (Exception e) {
            log.error("Ошибка при добавлении тикеров из пары {}: {}", pair.getPairName(), e.getMessage(), e);
            Notification.show("❌ Ошибка: " + e.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Извлекает полные названия инструментов из названия пары
     * Поддерживает форматы:
     * - "ENJ-USDT-SWAP/LUNA-USDT-SWAP" -> [ENJ-USDT-SWAP, LUNA-USDT-SWAP]
     * - "BTC-ETH" -> [BTC, ETH]
     * - "BTCUSDT-ETHUSDT" -> [BTCUSDT, ETHUSDT]
     */
    private Set<String> extractInstrumentsFromPairName(String pairName) {
        Set<String> instruments = new HashSet<>();

        try {
            // Разделяем по слешу для получения отдельных инструментов
            String[] parts = pairName.split("/");

            for (String part : parts) {
                String instrument = part.trim().toUpperCase();
                if (!instrument.isEmpty()) {
                    instruments.add(instrument);
                }
            }

            log.debug("🔍 Извлечены инструменты из '{}': {}", pairName, instruments);

        } catch (Exception e) {
            log.error("❌ Ошибка извлечения инструментов из '{}': {}", pairName, e.getMessage(), e);
        }

        return instruments;
    }

    /**
     * Извлекает базовые тикеры из названия пары (DEPRECATED - используется только для совместимости)
     * Поддерживает форматы:
     * - "ENJ-USDT-SWAP/LUNA-USDT-SWAP" -> [ENJ, LUNA]
     * - "BTC-ETH" -> [BTC, ETH]
     * - "BTCUSDT-ETHUSDT" -> [BTC, ETH]
     */
    private Set<String> extractTickersFromPairName(String pairName) {
        Set<String> tickers = new HashSet<>();

        try {
            // Разделяем по слешу для получения отдельных инструментов
            String[] instruments = pairName.split("/");

            for (String instrument : instruments) {
                String ticker = extractBaseTickerFromInstrument(instrument.trim());
                if (!ticker.isEmpty()) {
                    tickers.add(ticker);
                }
            }

            log.debug("🔍 Извлечены тикеры из '{}': {}", pairName, tickers);

        } catch (Exception e) {
            log.error("❌ Ошибка извлечения тикеров из '{}': {}", pairName, e.getMessage(), e);
        }

        return tickers;
    }

    /**
     * Извлекает базовый тикер из названия инструмента
     * Примеры:
     * - "ENJ-USDT-SWAP" -> "ENJ"
     * - "BTCUSDT" -> "BTC"
     * - "ETH" -> "ETH"
     */
    private String extractBaseTickerFromInstrument(String instrument) {
        if (instrument == null || instrument.isEmpty()) {
            return "";
        }

        String upper = instrument.toUpperCase();

        // Для форматов типа "ENJ-USDT-SWAP", "BTC-USDT", "ETH-USD" 
        if (upper.contains("-")) {
            // Берем первую часть до первого дефиса
            String baseTicker = upper.split("-")[0];
            return baseTicker.trim();
        }

        // Для форматов типа "BTCUSDT", "ETHUSDC"
        // Убираем известные суффиксы-валюты
        String[] knownSuffixes = {"USDT", "USDC", "USD", "BTC", "ETH", "BNB", "BUSD"};
        for (String suffix : knownSuffixes) {
            if (upper.endsWith(suffix) && upper.length() > suffix.length()) {
                return upper.substring(0, upper.length() - suffix.length()).trim();
            }
        }

        // Если ничего не подошло, возвращаем как есть
        return upper.trim();
    }

    private void calculateZScore(Pair pair) {
        try {
            log.info("🧮 Расчет Z-Score для пары {}", pair.getPairName());

            // Выполняем расчет в фоновом потоке
            getUI().ifPresent(ui -> {
                Thread calculateThread = new Thread(() -> {
                    try {
                        Pair calculatedTradingPair =
                                pairService.calculateZScoreForStablePair(pair);

                        ui.access(() -> {
                            if (calculatedTradingPair != null) {
                                Notification.show(
                                                String.format("✅ Z-Score рассчитан для пары %s! Показываю график...",
                                                        pair.getPairName()),
                                                3000, Notification.Position.BOTTOM_CENTER)
                                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                                // Показываем график с рассчитанными данными
                                zScoreChartDialog.showChart(calculatedTradingPair);
                            } else {
                                Notification.show(
                                                String.format("❌ Не удалось рассчитать Z-Score для пары %s",
                                                        pair.getPairName()),
                                                3000, Notification.Position.TOP_CENTER)
                                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                            }
                        });

                    } catch (Exception e) {
                        log.error("Ошибка при расчете Z-Score для пары {}: {}", pair.getPairName(), e.getMessage(), e);
                        ui.access(() -> {
                            Notification.show("❌ Ошибка расчета: " + e.getMessage(),
                                            5000, Notification.Position.TOP_CENTER)
                                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        });
                    }
                });
                calculateThread.start();
            });

        } catch (Exception e) {
            log.error("Ошибка при инициации расчета Z-Score для пары {}: {}", pair.getPairName(), e.getMessage(), e);
            Notification.show("❌ Ошибка: " + e.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }


    private void loadData() {
        loadFoundPairs();
        loadMonitoringPairs();
        updateStatistics();
    }

    private void loadFoundPairs() {
        try {
            List<Pair> pairs = pairService.getAllFoundPairs();
            foundPairsGrid.setItems(pairs);
            log.debug("Загружено {} найденных пар", pairs.size());
        } catch (Exception e) {
            log.error("Ошибка при загрузке найденных пар: {}", e.getMessage(), e);
            Notification.show("❌ Ошибка при загрузке данных", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void loadMonitoringPairs() {
        try {
            List<Pair> pairs = pairService.getMonitoringPairs();
            monitoringPairsGrid.setItems(pairs);
            log.debug("Загружено {} пар в мониторинге", pairs.size());
        } catch (Exception e) {
            log.error("Ошибка при загрузке пар мониторинга: {}", e.getMessage(), e);
            Notification.show("❌ Ошибка при загрузке данных мониторинга", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateStatistics() {
        try {
            Map<String, Object> stats = pairService.getSearchStatistics();
            int totalFound = ((Number) stats.get("totalFound")).intValue();
            int totalInMonitoring = ((Number) stats.get("totalInMonitoring")).intValue();

            statsLabel.setText(String.format(
                    "📊 Найдено пар: %d | В мониторинге: %d",
                    totalFound, totalInMonitoring));
        } catch (Exception e) {
            log.error("Ошибка при обновлении статистики: {}", e.getMessage(), e);
            statsLabel.setText("📊 Статистика недоступна");
        }
    }

    // ======== МЕТОДЫ ДЛЯ РАБОТЫ С НАСТРОЙКАМИ СКРИННЕРА ========

    private void loadAvailableSettings() {
        try {
            List<StablePairsScreenerSettings> allSettings = settingsService.getAllSettings();
            savedSettingsCombo.setItems(allSettings);

            // Автоматически выбираем настройки по умолчанию, если они есть
            allSettings.stream()
                    .filter(StablePairsScreenerSettings::isDefault)
                    .findFirst()
                    .ifPresent(defaultSettings -> {
                        savedSettingsCombo.setValue(defaultSettings);
                        loadSettingsIntoUI(defaultSettings);
                    });

        } catch (Exception e) {
            log.error("Ошибка при загрузке доступных настроек: {}", e.getMessage(), e);
            Notification.show("❌ Ошибка при загрузке настроек", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void loadSettingsIntoUI(StablePairsScreenerSettings settings) {
        try {
            log.debug("🔄 Загрузка настроек в UI: {}", settings.getName());

            // Загружаем таймфреймы и периоды
            timeframeMultiSelect.setValue(settings.getSelectedTimeframesSet());
            periodMultiSelect.setValue(settings.getSelectedPeriodsSet());

            // Загружаем настройки фильтров
            minCorrelationEnabled.setValue(settings.isMinCorrelationEnabled());
            minCorrelationField.setValue(settings.getMinCorrelationValue());
            minCorrelationField.setEnabled(settings.isMinCorrelationEnabled());

            minWindowSizeEnabled.setValue(settings.isMinWindowSizeEnabled());
            minWindowSizeField.setValue(settings.getMinWindowSizeValue());
            minWindowSizeField.setEnabled(settings.isMinWindowSizeEnabled());

            maxAdfValueEnabled.setValue(settings.isMaxAdfValueEnabled());
            maxAdfValueField.setValue(settings.getMaxAdfValue());
            maxAdfValueField.setEnabled(settings.isMaxAdfValueEnabled());

            minRSquaredEnabled.setValue(settings.isMinRSquaredEnabled());
            minRSquaredField.setValue(settings.getMinRSquaredValue());
            minRSquaredField.setEnabled(settings.isMinRSquaredEnabled());

            maxPValueEnabled.setValue(settings.isMaxPValueEnabled());
            maxPValueField.setValue(settings.getMaxPValue());
            maxPValueField.setEnabled(settings.isMaxPValueEnabled());

            // Загружаем настройки фильтрации по минимальному объему
            minVolumeEnabled.setValue(settings.isMinVolumeEnabled());
            minVolumeField.setValue(settings.getMinVolumeValue());
            minVolumeField.setEnabled(settings.isMinVolumeEnabled());

            // Загружаем настройки фильтрации по тикерам
            searchTickersEnabled.setValue(settings.isSearchTickersEnabled());
            if (settings.getSearchTickers() != null && !settings.getSearchTickers().trim().isEmpty()) {
                searchTickersField.setValue(settings.getSearchTickers());
            } else {
                searchTickersField.clear();
            }
            searchTickersField.setEnabled(settings.isSearchTickersEnabled());

            // Загружаем настройки автоматизации
            runOnScheduleCheckbox.setValue(settings.isRunOnSchedule());
            
            // Загружаем настройку использования кэша (по умолчанию включено)
            useCacheCheckbox.setValue(settings.getUseCache() != null ? settings.getUseCache() : true);

            log.info("✅ Настройки '{}' загружены в UI", settings.getName());

        } catch (Exception e) {
            log.error("Ошибка при загрузке настроек в UI: {}", e.getMessage(), e);
            Notification.show("❌ Ошибка при загрузке настроек: " + e.getMessage(),
                            3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void saveCurrentSettings() {
        try {
            // Запрашиваем название у пользователя
            com.vaadin.flow.component.textfield.TextField nameField = new com.vaadin.flow.component.textfield.TextField("Название настроек");
            nameField.setValue("Настройки " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            nameField.setWidth("300px");

            com.vaadin.flow.component.orderedlayout.VerticalLayout dialogContent = new com.vaadin.flow.component.orderedlayout.VerticalLayout(nameField);

            com.vaadin.flow.component.confirmdialog.ConfirmDialog dialog = new com.vaadin.flow.component.confirmdialog.ConfirmDialog();
            dialog.setHeader("Сохранение настроек");
            dialog.add(dialogContent);
            dialog.setConfirmText("Сохранить");
            dialog.setCancelText("Отмена");
            dialog.addConfirmListener(event -> {
                try {
                    String settingsName = nameField.getValue();
                    if (settingsName == null || settingsName.trim().isEmpty()) {
                        Notification.show("❌ Название не может быть пустым", 3000, Notification.Position.TOP_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }

                    // Создаем настройки из текущего состояния UI
                    StablePairsScreenerSettings settings = settingsService.createFromUIParams(
                            settingsName.trim(),
                            timeframeMultiSelect.getValue(),
                            periodMultiSelect.getValue(),
                            minCorrelationEnabled.getValue(), minCorrelationField.getValue(),
                            minWindowSizeEnabled.getValue(), minWindowSizeField.getValue(),
                            maxAdfValueEnabled.getValue(), maxAdfValueField.getValue(),
                            minRSquaredEnabled.getValue(), minRSquaredField.getValue(),
                            maxPValueEnabled.getValue(), maxPValueField.getValue(),
                            minVolumeEnabled.getValue(), minVolumeField.getValue(),
                            searchTickersEnabled.getValue(), getSearchTickersSet(),
                            runOnScheduleCheckbox.getValue(),
                            useCacheCheckbox.getValue()
                    );

                    // Сохраняем
                    StablePairsScreenerSettings saved = settingsService.saveSettings(settings);

                    Notification.show(
                                    String.format("💾 Настройки '%s' сохранены", saved.getName()),
                                    3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                    // Обновляем список доступных настроек
                    loadAvailableSettings();
                    savedSettingsCombo.setValue(saved);

                } catch (Exception e) {
                    log.error("Ошибка при сохранении настроек: {}", e.getMessage(), e);
                    Notification.show("❌ Ошибка при сохранении: " + e.getMessage(),
                                    3000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });

            dialog.open();

        } catch (Exception e) {
            log.error("Ошибка при инициации сохранения настроек: {}", e.getMessage(), e);
            Notification.show("❌ Ошибка: " + e.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void loadSelectedSettings() {
        try {
            StablePairsScreenerSettings selected = savedSettingsCombo.getValue();
            if (selected == null) {
                Notification.show("❌ Выберите настройки для загрузки", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            loadSettingsIntoUI(selected);

            // Отмечаем настройки как использованные
            settingsService.markAsUsed(selected.getId());

            Notification.show(
                            String.format("📁 Настройки '%s' загружены", selected.getName()),
                            3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (Exception e) {
            log.error("Ошибка при загрузке выбранных настроек: {}", e.getMessage(), e);
            Notification.show("❌ Ошибка при загрузке: " + e.getMessage(),
                            3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}