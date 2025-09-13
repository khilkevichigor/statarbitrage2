package com.example.core.ui.views;

import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.services.StablePairService;
import com.example.core.ui.components.ZScoreChartDialog;
import com.example.core.ui.layout.MainLayout;
import com.example.shared.models.StablePair;
import com.example.shared.utils.TimeFormatterUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Скриннер стабильных коинтегрированных пар
 */
@Slf4j
@PageTitle("Стабильные пары")
@Route(value = "stable-pairs", layout = MainLayout.class)
public class StablePairsView extends VerticalLayout {

    private final StablePairService stablePairService;
    private final ZScoreChartDialog zScoreChartDialog;

    // Элементы формы поиска
    private ComboBox<String> timeframeComboBox;
    private ComboBox<String> periodComboBox;
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

    private Button searchButton;
    private Button clearAllButton;
    private ProgressBar progressBar;

    // Таблицы
    private Grid<StablePair> foundPairsGrid;
    private Grid<StablePair> monitoringPairsGrid;

    // Статистика
    private Span statsLabel;

    public StablePairsView(StablePairService stablePairService, ZScoreChartDialog zScoreChartDialog) {
        this.stablePairService = stablePairService;
        this.zScoreChartDialog = zScoreChartDialog;

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

        timeframeComboBox = new ComboBox<>("Таймфрейм");
        timeframeComboBox.setItems("1m", "5m", "15m", "1H", "4H", "1D", "1W", "1M");
        timeframeComboBox.setValue("1D");
        timeframeComboBox.setWidth("150px");

        periodComboBox = new ComboBox<>("Период");
        periodComboBox.setItems("день", "неделя", "месяц", "1 год", "2 года", "3 года");
        periodComboBox.setValue("месяц");
        periodComboBox.setWidth("150px");

        row1.add(timeframeComboBox, periodComboBox);

        // Вторая строка: Настройки фильтров
        HorizontalLayout row2 = createFilterRow1();
        HorizontalLayout row3 = createFilterRow2();

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

        buttonRow.add(searchButton, clearAllButton);

        formLayout.add(formTitle, row1, row2, row3, buttonRow);
        return formLayout;
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

        HorizontalLayout rSquaredGroup = new HorizontalLayout(minRSquaredEnabled, minRSquaredField);
        rSquaredGroup.setSpacing(false);
        rSquaredGroup.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout pValueGroup = new HorizontalLayout(maxPValueEnabled, maxPValueField);
        pValueGroup.setSpacing(false);
        pValueGroup.setAlignItems(FlexComponent.Alignment.END);

        row.add(rSquaredGroup, pValueGroup);
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

    private Grid<StablePair> createFoundPairsGrid() {
        Grid<StablePair> grid = new Grid<>(StablePair.class, false);
        grid.setHeight("300px");
        grid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_ROW_STRIPES);

        // Основные колонки
        grid.addColumn(StablePair::getPairName).setHeader("Пара").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getTotalScore() != null ? pair.getTotalScore().toString() : "-")).setHeader("Скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(StablePair::getStabilityRating).setHeader("Рейтинг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getIsTradeable() != null && pair.getIsTradeable() ? "Да" : "Нет")).setHeader("Торгуемая").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getDataPoints() != null ? pair.getDataPoints().toString() : "-")).setHeader("Точки").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getCandleCount() != null ? pair.getCandleCount().toString() : "-")).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(StablePair::getTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(StablePair::getPeriod).setHeader("Период").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getSearchDate() != null ? TimeFormatterUtil.formatDateTime(pair.getSearchDate()) : "-")).setHeader("Дата поиска").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        // Колонка действий
        grid.addColumn(new ComponentRenderer<>(this::createFoundPairActions)).setHeader("Действия").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        return grid;
    }

    private Grid<StablePair> createMonitoringPairsGrid() {
        Grid<StablePair> grid = new Grid<>(StablePair.class, false);
        grid.setHeight("200px");
        grid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_ROW_STRIPES);

        // Основные колонки
        grid.addColumn(StablePair::getPairName).setHeader("Пара").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getTotalScore() != null ? pair.getTotalScore().toString() : "-")).setHeader("Скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(StablePair::getStabilityRating).setHeader("Рейтинг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getCandleCount() != null ? pair.getCandleCount().toString() : "-")).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(StablePair::getTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(StablePair::getPeriod).setHeader("Период").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(new TextRenderer<>(pair -> pair.getCreatedAt() != null ? TimeFormatterUtil.formatDateTime(pair.getCreatedAt()) : "-")).setHeader("Добавлена").setAutoWidth(true).setFlexGrow(0);
        // Колонка действий
        grid.addColumn(new ComponentRenderer<>(this::createMonitoringPairActions)).setHeader("Действия").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        return grid;
    }

    private HorizontalLayout createFoundPairActions(StablePair pair) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.setSpacing(true);

        Button addButton = new Button("Добавить", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        addButton.addClickListener(e -> addToMonitoring(pair));

        Button chartButton = new Button(VaadinIcon.LINE_CHART.create());
        chartButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        chartButton.getElement().setAttribute("title", "Рассчитать Z-Score и показать график");
        chartButton.addClickListener(e -> calculateZScore(pair));

        Button deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        deleteButton.getElement().setAttribute("title", "Удалить");
        deleteButton.addClickListener(e -> deleteFoundPair(pair));

        actions.add(addButton, chartButton, deleteButton);
        return actions;
    }

    private HorizontalLayout createMonitoringPairActions(StablePair pair) {
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
            String timeframe = timeframeComboBox.getValue();
            String period = periodComboBox.getValue();
            Map<String, Object> searchSettings = buildSearchSettings();

            log.info("🔍 Запуск поиска стабильных пар: TF={}, Period={}", timeframe, period);

            // Выполняем поиск в фоновом потоке
            getUI().ifPresent(ui -> {
                Thread searchThread = new Thread(() -> {
                    try {
                        StabilityResponseDto response = stablePairService.searchStablePairs(
                                timeframe, period, searchSettings);

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

        return settings;
    }

    private void clearAllResults() {
        ConfirmDialog dialog = new ConfirmDialog(
                "Очистка результатов",
                "Вы уверены, что хотите удалить все найденные пары? " +
                        "Пары в мониторинге НЕ будут удалены.",
                "Очистить", event -> {
            try {
                int deletedCount = stablePairService.clearAllFoundPairs();
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

    private void addToMonitoring(StablePair pair) {
        try {
            stablePairService.addToMonitoring(pair.getId());
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

    private void removeFromMonitoring(StablePair pair) {
        try {
            stablePairService.removeFromMonitoring(pair.getId());
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

    private void deleteFoundPair(StablePair pair) {
        ConfirmDialog dialog = new ConfirmDialog(
                "Удаление пары",
                String.format("Вы уверены, что хотите удалить пару %s?", pair.getPairName()),
                "Удалить", event -> {
            try {
                stablePairService.deleteFoundPair(pair.getId());
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

    private void calculateZScore(StablePair pair) {
        try {
            log.info("🧮 Расчет Z-Score для пары {}", pair.getPairName());
            
            // Выполняем расчет в фоновом потоке
            getUI().ifPresent(ui -> {
                Thread calculateThread = new Thread(() -> {
                    try {
                        com.example.shared.models.TradingPair calculatedTradingPair = 
                                stablePairService.calculateZScoreForStablePair(pair);

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
            List<StablePair> pairs = stablePairService.getAllFoundPairs();
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
            List<StablePair> pairs = stablePairService.getMonitoringPairs();
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
            Map<String, Object> stats = stablePairService.getSearchStatistics();
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
}