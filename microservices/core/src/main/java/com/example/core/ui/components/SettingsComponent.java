package com.example.core.ui.components;

import com.example.core.schedulers.TradeAndSimulationScheduler;
import com.example.core.services.SettingsService;
import com.example.core.services.CapitalCalculationService;
import com.example.core.services.PortfolioService;
import com.example.shared.models.Settings;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.validator.DoubleRangeValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringComponent
@UIScope
public class SettingsComponent extends VerticalLayout {

    private final SettingsService settingsService;
    private final TradeAndSimulationScheduler tradeAndSimulationScheduler;
    private final CapitalCalculationService capitalCalculationService;
    private final PortfolioService portfolioService;
    private final Binder<Settings> settingsBinder;

    private Settings currentSettings;
    private Checkbox autoTradingCheckbox;
    private Runnable autoTradingChangeCallback;
    
    // Поля для расчета депозита
    private Span capitalInfoSpan;

    public SettingsComponent(SettingsService settingsService,
                             TradeAndSimulationScheduler tradeAndSimulationScheduler,
                             CapitalCalculationService capitalCalculationService,
                             PortfolioService portfolioService) {
        this.settingsService = settingsService;
        this.tradeAndSimulationScheduler = tradeAndSimulationScheduler;
        this.capitalCalculationService = capitalCalculationService;
        this.portfolioService = portfolioService;
        this.settingsBinder = new Binder<>(Settings.class);

        initializeComponent();
        loadCurrentSettings();
        createSettingsForm();
        setupValidation();
    }

    @PostConstruct
    public void initSettings() {
        // Перезагружаем настройки из БД при каждом создании компонента
        refreshSettings();
        log.debug("🔄 SettingsComponent: Настройки инициализированы из БД - autoTrading={}",
                currentSettings.isAutoTradingEnabled());
    }

    private void initializeComponent() {
        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        H2 title = new H2("⚙️ Настройки торговли");
        title.addClassNames(LumoUtility.TextColor.PRIMARY, LumoUtility.FontSize.XLARGE);
        title.getStyle().set("margin-bottom", "2rem");
        title.getStyle().set("text-align", "center");

        add(title);
    }

    private void loadCurrentSettings() {
        try {
            currentSettings = settingsService.getSettings();
        } catch (Exception e) {
            log.error("❌ Ошибка загрузки настроек", e);
            currentSettings = new Settings(); // fallback to default
        }
    }

    private void createSettingsForm() {
        createAutoTradingToggle();
        createSettingsFormSections();
        createSaveButton();
    }

    private void createAutoTradingToggle() {
        Div autoTradingCard = new Div();
        autoTradingCard.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.LARGE);
        autoTradingCard.getStyle().set("padding", "1.5rem").set("margin-bottom", "2rem");

        HorizontalLayout toggleHeader = new HorizontalLayout();
        toggleHeader.setAlignItems(HorizontalLayout.Alignment.CENTER);
        toggleHeader.setWidthFull();

        Icon icon = new Icon(VaadinIcon.AUTOMATION);
        icon.addClassNames(LumoUtility.TextColor.SUCCESS);

        Div titleSection = new Div();
        H3 title = new H3("Автотрейдинг");
        title.getStyle().set("margin", "0").set("color", "var(--lumo-primary-text-color)");

        Div description = new Div();
        description.setText("Автоматическое выполнение торговых операций");
        description.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        titleSection.add(title, description);

        autoTradingCheckbox = new Checkbox();
        autoTradingCheckbox.setValue(currentSettings.isAutoTradingEnabled());
        autoTradingCheckbox.getStyle().set("transform", "scale(1.3)");

        toggleHeader.add(icon, titleSection, autoTradingCheckbox);
        toggleHeader.setFlexGrow(1, titleSection);

        autoTradingCard.add(toggleHeader);
        add(autoTradingCard);

        autoTradingCheckbox.addValueChangeListener(event -> {
            try {
                Settings settings = settingsService.getSettings();
                settings.setAutoTradingEnabled(event.getValue());
                settingsService.save(settings);

                log.info(event.getValue() ? "Автотрейдинг включен" : "Автотрейдинг отключен");
                Notification.show(event.getValue() ? "Автотрейдинг включен" : "Автотрейдинг отключен");

//                if (event.getValue()) { //todo будем ждать шедуллера
//                    log.debug("🚀 UI: Запускаем maintainPairs() асинхронно");
//                    // Запускаем maintainPairs() асинхронно, чтобы не блокировать UI
//                    CompletableFuture.runAsync(() -> {
//                        try {
//                            tradeAndSimulationScheduler.maintainPairs();
//                        } catch (Exception e) {
//                            log.error("❌ Ошибка при асинхронном запуске maintainPairs()", e);
//                        }
//                    });
//                }

                // Уведомляем об изменении состояния автотрейдинга
                if (autoTradingChangeCallback != null) {
                    log.debug("🔄 SettingsComponent: Вызываем autoTradingChangeCallback для autoTrading={}", event.getValue());
                    autoTradingChangeCallback.run();
                } else {
                    log.debug("⚠️ SettingsComponent: autoTradingChangeCallback не установлен!");
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при изменении режима автотрейдинга", e);
                Notification.show("Ошибка при изменении режима автотрейдинга: " + e.getMessage());
            }
        });
    }

    private void createSettingsFormSections() {
        // Create form fields
        TextField timeframeField = new TextField("Таймфрейм");
        NumberField candleLimitField = new NumberField("Свечей (шт)");
        NumberField minZField = new NumberField("Min Z");
        NumberField minRSquaredField = new NumberField("Min R-Squared");
        NumberField minWindowSizeField = new NumberField("Min windowSize");
        NumberField minPValueField = new NumberField("Min pValue");
        NumberField maxAdfValueField = new NumberField("Max adfValue");
        NumberField minCorrelationField = new NumberField("Min corr");
        NumberField minVolumeField = new NumberField("Min Vol (млн $)");
        NumberField checkIntervalField = new NumberField("Обновление (мин)");

        // Create filter checkboxes
        Checkbox useMinZFilterCheckbox = new Checkbox("Использовать Min Z фильтр");
        Checkbox useMinRSquaredFilterCheckbox = new Checkbox("Использовать Min R-Squared фильтр");
        Checkbox useMinPValueFilterCheckbox = new Checkbox("Использовать Min pValue фильтр");
        Checkbox useMaxAdfValueFilterCheckbox = new Checkbox("Использовать Max adfValue фильтр");
        Checkbox useMinCorrelationFilterCheckbox = new Checkbox("Использовать Min Correlation фильтр");
        Checkbox useMinVolumeFilterCheckbox = new Checkbox("Использовать Min Volume фильтр");

        // Minimum lot blacklist field
        TextArea minimumLotBlacklistField = new TextArea("Блэклист мин. лота");
        minimumLotBlacklistField.setPlaceholder("Тикеры через запятую (ETH-USDT-SWAP,BTC-USDT-SWAP)");
        minimumLotBlacklistField.setHelperText("Тикеры с высокими требованиями к минимальному лоту");

        NumberField maxShortMarginSize = new NumberField("Размер риска шорт ($)");
        NumberField maxLongMarginSize = new NumberField("Размер риска лонг ($)");
        NumberField capitalShortField = new NumberField("Позиция шорт ($)");
        NumberField leverageField = new NumberField("Плечо");

        NumberField exitTakeField = new NumberField("Exit Тейк (%)");
        NumberField exitStopField = new NumberField("Exit Стоп (%)");
        NumberField exitZMinField = new NumberField("Exit Мин Z");
        NumberField exitZMaxField = new NumberField("Exit Макс Z");
        NumberField exitZMaxPercentField = new NumberField("Exit Макс Z (%)");
        NumberField exitTimeMinutesField = new NumberField("Exit Таймаут (мин)");
        NumberField exitBreakEvenPercentField = new NumberField("Профит для БУ (%)");
        NumberField exitNegativeZMinProfitPercentField = new NumberField("Мин. профит при Z<0 (%)");

        // Create exit strategy checkboxes
        Checkbox useExitTakeCheckbox = new Checkbox("Использовать Exit Тейк");
        Checkbox useExitStopCheckbox = new Checkbox("Использовать Exit Стоп");
        Checkbox useExitZMinCheckbox = new Checkbox("Использовать Exit Мин Z");
        Checkbox useExitZMaxCheckbox = new Checkbox("Использовать Exit Макс Z");
        Checkbox useExitZMaxPercentCheckbox = new Checkbox("Использовать Exit Макс Z (%)");
        Checkbox useExitTimeMinutesCheckbox = new Checkbox("Использовать Exit Таймаут");
        Checkbox useExitBreakEvenPercentCheckbox = new Checkbox("Использовать уровень профита для БУ");
        Checkbox useExitNegativeZMinProfitPercentCheckbox = new Checkbox("Выход при Z<0 с мин. профитом");

        NumberField usePairsField = new NumberField("Кол-во пар");

        // Set step and min values for number fields
        setNumberFieldProperties(candleLimitField, 1, 1);
        setNumberFieldProperties(minZField, 0.1, 0.0);
        setNumberFieldProperties(minRSquaredField, 0.1, 0.5);
        setNumberFieldProperties(minWindowSizeField, 1, 1);
        setNumberFieldProperties(minPValueField, 0.001, 0.0);
        setNumberFieldProperties(maxAdfValueField, 0.001, 0.0);
        setNumberFieldProperties(minCorrelationField, 0.01, -1.0);
        setNumberFieldProperties(minVolumeField, 1, 0.0);
        setNumberFieldProperties(checkIntervalField, 1, 1);
        setNumberFieldProperties(maxShortMarginSize, 1.0, 0.0);
        setNumberFieldProperties(maxLongMarginSize, 1.0, 0.0);
        setNumberFieldProperties(capitalShortField, 1.0, 0.0);
        setNumberFieldProperties(leverageField, 1, 1);
        setNumberFieldProperties(exitTakeField, 0.1, 0.0);
        setNumberFieldProperties(exitStopField, 0.1, -10.0);
        setNumberFieldProperties(exitZMinField, 0.01, -10.0);
        setNumberFieldProperties(exitZMaxField, 0.1, 0.0);
        setNumberFieldProperties(exitZMaxPercentField, 0.1, 0.0);
        setNumberFieldProperties(exitTimeMinutesField, 1, 1);
        setNumberFieldProperties(exitNegativeZMinProfitPercentField, 0.1, 0.0);
        setNumberFieldProperties(usePairsField, 1, 1);

        // Create sections
        add(createAnalysisSection(timeframeField, candleLimitField, minZField, minRSquaredField, minWindowSizeField,
                minPValueField, maxAdfValueField, minCorrelationField, minVolumeField,
                checkIntervalField, minimumLotBlacklistField, useMinZFilterCheckbox, useMinRSquaredFilterCheckbox,
                useMinPValueFilterCheckbox, useMaxAdfValueFilterCheckbox, useMinCorrelationFilterCheckbox,
                useMinVolumeFilterCheckbox));

        // Создаем поля для усреднения (депозит берется из OKX через PortfolioService)
        
        // Создаем поля для усреднения
        Checkbox autoAveragingCheckbox = new Checkbox("Автоусреднение");
        NumberField averagingDrawdownThresholdField = new NumberField("Просадка для срабатывания (%)");
        NumberField averagingVolumeMultiplierField = new NumberField("Множитель объема");
        NumberField averagingDrawdownMultiplierField = new NumberField("Множитель просадки для срабатывания");
        NumberField maxAveragingCountField = new NumberField("Max кол-во усреднений");
        
        // Настраиваем свойства полей усреднения
        setNumberFieldProperties(averagingDrawdownThresholdField, 0.1, 0.1);
        setNumberFieldProperties(averagingVolumeMultiplierField, 0.1, 1.0);
        setNumberFieldProperties(averagingDrawdownMultiplierField, 0.1, 1.0);
        setNumberFieldProperties(maxAveragingCountField, 1, 1);

        add(createCapitalManagementSection(usePairsField, maxShortMarginSize, maxLongMarginSize, 
                leverageField, autoAveragingCheckbox, averagingDrawdownThresholdField,
                averagingVolumeMultiplierField, averagingDrawdownMultiplierField, maxAveragingCountField));

        add(createExitStrategySection(
                exitTakeField,
                exitStopField,
                exitZMinField,
                exitZMaxField,
                exitZMaxPercentField,
                exitTimeMinutesField,
                exitBreakEvenPercentField,
                exitNegativeZMinProfitPercentField,
                useExitTakeCheckbox,
                useExitStopCheckbox,
                useExitZMinCheckbox,
                useExitZMaxCheckbox,
                useExitZMaxPercentCheckbox,
                useExitTimeMinutesCheckbox,
                useExitBreakEvenPercentCheckbox,
                useExitNegativeZMinProfitPercentCheckbox
        ));

        add(createScoringWeightsSection());

        // Bind fields to settings object
        bindFields(
                timeframeField,
                candleLimitField,
                minZField,
                minRSquaredField,
                minWindowSizeField,
                minPValueField,
                maxAdfValueField,
                checkIntervalField,
                minCorrelationField,
                minVolumeField,
                usePairsField,
                minimumLotBlacklistField,
                maxShortMarginSize,
                maxLongMarginSize,
                leverageField,
                exitTakeField,
                exitStopField,
                exitZMinField,
                exitZMaxField,
                exitZMaxPercentField,
                exitTimeMinutesField,
                exitBreakEvenPercentField,
                exitNegativeZMinProfitPercentField,
                useMinZFilterCheckbox,
                useMinRSquaredFilterCheckbox,
                useMinPValueFilterCheckbox,
                useMaxAdfValueFilterCheckbox,
                useMinCorrelationFilterCheckbox,
                useMinVolumeFilterCheckbox,
                useExitTakeCheckbox,
                useExitStopCheckbox,
                useExitZMinCheckbox,
                useExitZMaxCheckbox,
                useExitZMaxPercentCheckbox,
                useExitTimeMinutesCheckbox,
                useExitBreakEvenPercentCheckbox,
                useExitNegativeZMinProfitPercentCheckbox);
        
        // Привязываем поля усреднения отдельно
        bindAveragingFields(autoAveragingCheckbox, averagingDrawdownThresholdField,
                averagingVolumeMultiplierField, averagingDrawdownMultiplierField, maxAveragingCountField);

        settingsBinder.readBean(currentSettings);
    }

    private Details createAnalysisSection(TextField timeframeField, NumberField candleLimitField,
                                          NumberField minZField, NumberField minRSquaredField,
                                          NumberField minWindowSizeField, NumberField minPValueField,
                                          NumberField maxAdfValueField, NumberField minCorrelationField,
                                          NumberField minVolumeField, NumberField checkIntervalField,
                                          TextArea minimumLotBlacklistField,
                                          Checkbox useMinZFilterCheckbox,
                                          Checkbox useMinRSquaredFilterCheckbox, Checkbox useMinPValueFilterCheckbox,
                                          Checkbox useMaxAdfValueFilterCheckbox, Checkbox useMinCorrelationFilterCheckbox,
                                          Checkbox useMinVolumeFilterCheckbox) {

        FormLayout analysisForm = createFormLayout();

        // Создаем компоненты фильтров с чекбоксами
        HorizontalLayout minZLayout = createFilterLayout(useMinZFilterCheckbox, minZField);
        HorizontalLayout minRSquaredLayout = createFilterLayout(useMinRSquaredFilterCheckbox, minRSquaredField);
        HorizontalLayout minPValueLayout = createFilterLayout(useMinPValueFilterCheckbox, minPValueField);
        HorizontalLayout maxAdfValueLayout = createFilterLayout(useMaxAdfValueFilterCheckbox, maxAdfValueField);
        HorizontalLayout minCorrelationLayout = createFilterLayout(useMinCorrelationFilterCheckbox, minCorrelationField);
        HorizontalLayout minVolumeLayout = createFilterLayout(useMinVolumeFilterCheckbox, minVolumeField);

        analysisForm.add(
                timeframeField, candleLimitField, checkIntervalField,
                minZLayout, minRSquaredLayout, minWindowSizeField, minPValueLayout,
                maxAdfValueLayout, minCorrelationLayout, minVolumeLayout,
                minimumLotBlacklistField
        );

        Details analysisSection = createDetailsCard("🔍 Анализ и фильтры",
                "Параметры для поиска и анализа торговых пар", analysisForm);
        analysisSection.setOpened(true);
        return analysisSection;
    }

    private Details createCapitalSection(NumberField maxShortMarginSize, NumberField maxLongMarginSize, NumberField leverageField
    ) {
        FormLayout capitalForm = createFormLayout();
        capitalForm.add(
                maxShortMarginSize,
                maxLongMarginSize,
                leverageField);

        return createDetailsCard("💰 Управление капиталом",
                "Настройки депозита и управления рисками", capitalForm);
    }

    private Details createExitStrategySection(
            NumberField exitTakeField,
            NumberField exitStopField,
            NumberField exitZMinField,
            NumberField exitZMaxField,
            NumberField exitZMaxPercentField,
            NumberField exitTimeMinutesField,
            NumberField exitBreakEvenPercentField,
            NumberField exitNegativeZMinProfitPercentField,
            Checkbox useExitTakeCheckbox,
            Checkbox useExitStopCheckbox,
            Checkbox useExitZMinCheckbox,
            Checkbox useExitZMaxCheckbox,
            Checkbox useExitZMaxPercentCheckbox,
            Checkbox useExitTimeMinutesCheckbox,
            Checkbox useExitBreakEvenPercentCheckbox,
            Checkbox useExitNegativeZMinProfitPercentCheckbox
    ) {

        FormLayout exitForm = createFormLayout();

        // Создаем компоненты стратегий выхода с чекбоксами
        HorizontalLayout exitTakeLayout = createFilterLayout(useExitTakeCheckbox, exitTakeField);
        HorizontalLayout exitStopLayout = createFilterLayout(useExitStopCheckbox, exitStopField);
        HorizontalLayout exitZMinLayout = createFilterLayout(useExitZMinCheckbox, exitZMinField);
        HorizontalLayout exitZMaxLayout = createFilterLayout(useExitZMaxCheckbox, exitZMaxField);
        HorizontalLayout exitZMaxPercentLayout = createFilterLayout(useExitZMaxPercentCheckbox, exitZMaxPercentField);
        HorizontalLayout exitTimeMinutesLayout = createFilterLayout(useExitTimeMinutesCheckbox, exitTimeMinutesField);
        HorizontalLayout exitBreakEvenPercentLayout = createFilterLayout(useExitBreakEvenPercentCheckbox, exitBreakEvenPercentField);
        HorizontalLayout exitNegativeZMinProfitPercentLayout = createFilterLayout(useExitNegativeZMinProfitPercentCheckbox, exitNegativeZMinProfitPercentField);

        exitForm.add(
                exitTakeLayout,
                exitStopLayout,
                exitZMinLayout,
                exitZMaxLayout,
                exitZMaxPercentLayout,
                exitTimeMinutesLayout,
                exitBreakEvenPercentLayout,
                exitNegativeZMinProfitPercentLayout
        );

        return createDetailsCard("🚪 Стратегии выхода",
                "Условия закрытия позиций и управления рисками", exitForm);
    }

    private Details createScoringWeightsSection() {
        FormLayout scoringForm = createFormLayout();

        // Создаем поля для весов скоринга
        NumberField zScoreWeightField = new NumberField("Z-Score сила (очки)");
        NumberField pixelSpreadWeightField = new NumberField("Пиксельный спред (очки)");
        NumberField cointegrationWeightField = new NumberField("Коинтеграция (очки)");
        NumberField modelQualityWeightField = new NumberField("Качество модели (очки)");
        NumberField statisticsWeightField = new NumberField("Статистика (очки)");
        NumberField bonusWeightField = new NumberField("Бонусы (очки)");

        // Создаем чекбоксы для включения/выключения компонентов
        Checkbox useZScoreScoringCheckbox = new Checkbox("Использовать Z-Score скоринг");
        Checkbox usePixelSpreadScoringCheckbox = new Checkbox("Использовать пиксельный спред скоринг");
        Checkbox useCointegrationScoringCheckbox = new Checkbox("Использовать коинтеграцию скоринг");
        Checkbox useModelQualityScoringCheckbox = new Checkbox("Использовать качество модели скоринг");
        Checkbox useStatisticsScoringCheckbox = new Checkbox("Использовать статистику скоринг");
        Checkbox useBonusScoringCheckbox = new Checkbox("Использовать бонусы скоринг");

        // Настраиваем свойства полей
        setNumberFieldProperties(zScoreWeightField, 1.0, 0.0);
        setNumberFieldProperties(pixelSpreadWeightField, 1.0, 0.0);
        setNumberFieldProperties(cointegrationWeightField, 1.0, 0.0);
        setNumberFieldProperties(modelQualityWeightField, 1.0, 0.0);
        setNumberFieldProperties(statisticsWeightField, 1.0, 0.0);
        setNumberFieldProperties(bonusWeightField, 1.0, 0.0);

        // Создаем компоновки с чекбоксами
        HorizontalLayout zScoreLayout = createFilterLayout(useZScoreScoringCheckbox, zScoreWeightField);
        HorizontalLayout pixelSpreadLayout = createFilterLayout(usePixelSpreadScoringCheckbox, pixelSpreadWeightField);
        HorizontalLayout cointegrationLayout = createFilterLayout(useCointegrationScoringCheckbox, cointegrationWeightField);
        HorizontalLayout modelQualityLayout = createFilterLayout(useModelQualityScoringCheckbox, modelQualityWeightField);
        HorizontalLayout statisticsLayout = createFilterLayout(useStatisticsScoringCheckbox, statisticsWeightField);
        HorizontalLayout bonusLayout = createFilterLayout(useBonusScoringCheckbox, bonusWeightField);

        scoringForm.add(
                zScoreLayout,
                pixelSpreadLayout,
                cointegrationLayout,
                modelQualityLayout,
                statisticsLayout,
                bonusLayout
        );

        // Привязываем поля к настройкам
        bindScoringFields(
                zScoreWeightField, pixelSpreadWeightField, cointegrationWeightField,
                modelQualityWeightField, statisticsWeightField, bonusWeightField,
                useZScoreScoringCheckbox, usePixelSpreadScoringCheckbox, useCointegrationScoringCheckbox,
                useModelQualityScoringCheckbox, useStatisticsScoringCheckbox, useBonusScoringCheckbox
        );

        return createDetailsCard("🎯 Веса системы скоринга",
                "Настройка весов компонентов для оценки качества торговых пар", scoringForm);
    }

    private Details createAveragingSection() {
        FormLayout averagingForm = createFormLayout();

        // Создаем поля для усреднения
        Checkbox autoAveragingCheckbox = new Checkbox("Автоусреднение");
        NumberField averagingDrawdownThresholdField = new NumberField("Просадка для срабатывания (%)");
        NumberField averagingVolumeMultiplierField = new NumberField("Множитель объема");
        NumberField averagingDrawdownMultiplierField = new NumberField("Множитель просадки для срабатывания");
        NumberField maxAveragingCountField = new NumberField("Max кол-во усреднений");

        // Настраиваем свойства полей
        setNumberFieldProperties(averagingDrawdownThresholdField, 0.1, 0.1);
        setNumberFieldProperties(averagingVolumeMultiplierField, 0.1, 1.0);
        setNumberFieldProperties(averagingDrawdownMultiplierField, 0.1, 1.0);
        setNumberFieldProperties(maxAveragingCountField, 1, 1);

        // Настраиваем placeholder и helper text
        averagingDrawdownThresholdField.setPlaceholder("10.0");
        averagingDrawdownThresholdField.setHelperText("Порог просадки в процентах для первого автоматического усреднения");

        averagingVolumeMultiplierField.setPlaceholder("1.5");
        averagingVolumeMultiplierField.setHelperText("Множитель объема для каждой позиции усреднения");

        averagingDrawdownMultiplierField.setPlaceholder("1.5");
        averagingDrawdownMultiplierField.setHelperText("Множитель для расчета следующего порога просадки");

        maxAveragingCountField.setPlaceholder("3");
        maxAveragingCountField.setHelperText("Максимальное количество усреднений для одной пары");

        // Логика активации/деактивации полей
        boolean isAutoAveragingEnabled = currentSettings.isAutoAveragingEnabled();
        averagingDrawdownThresholdField.setEnabled(isAutoAveragingEnabled);
        averagingVolumeMultiplierField.setEnabled(isAutoAveragingEnabled);
        averagingDrawdownMultiplierField.setEnabled(isAutoAveragingEnabled);
        maxAveragingCountField.setEnabled(isAutoAveragingEnabled);

        autoAveragingCheckbox.addValueChangeListener(event -> {
            boolean enabled = event.getValue();
            averagingDrawdownThresholdField.setEnabled(enabled);
            averagingVolumeMultiplierField.setEnabled(enabled);
            averagingDrawdownMultiplierField.setEnabled(enabled);
            maxAveragingCountField.setEnabled(enabled);
        });

        averagingForm.add(
                autoAveragingCheckbox,
                averagingDrawdownThresholdField,
                averagingVolumeMultiplierField,
                averagingDrawdownMultiplierField,
                maxAveragingCountField
        );

        // Привязываем поля к настройкам
        bindAveragingFields(autoAveragingCheckbox, averagingDrawdownThresholdField,
                averagingVolumeMultiplierField, averagingDrawdownMultiplierField, maxAveragingCountField);

        return createDetailsCard("🎯 Усреднение",
                "Настройки автоматического и ручного усреднения позиций", averagingForm);
    }

    private Details createDetailsCard(String title, String description, FormLayout content) {
        Div cardContent = new Div();
        cardContent.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM);
        cardContent.getStyle().set("padding", "1.5rem");

        Div header = new Div();
        H4 cardTitle = new H4(title);
        cardTitle.getStyle().set("margin", "0 0 0.5rem 0").set("color", "var(--lumo-primary-text-color)");

        Div cardDescription = new Div();
        cardDescription.setText(description);
        cardDescription.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        cardDescription.getStyle().set("margin-bottom", "1rem");

        header.add(cardTitle, cardDescription);
        cardContent.add(header, content);

        Details details = new Details();
        details.setSummaryText(title);
        details.setContent(cardContent);
        details.getStyle().set("margin-bottom", "1rem");
        details.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderRadius.LARGE);

        return details;
    }

    private FormLayout createFormLayout() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2),
                new FormLayout.ResponsiveStep("800px", 3)
        );
        return form;
    }

    private void createSaveButton() {
        Button saveButton = new Button("💾 Сохранить настройки", e -> saveSettings());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        saveButton.getStyle().set("margin-top", "2rem").set("width", "100%");

        Div buttonContainer = new Div(saveButton);
        buttonContainer.getStyle().set("text-align", "center");

        add(buttonContainer);
    }

    private void setNumberFieldProperties(NumberField field, double step, double min) {
        field.setStep(step);
        field.setMin(min);
        field.setStepButtonsVisible(true);
    }

    private void bindFields(TextField timeframeField,
                            NumberField candleLimitField,
                            NumberField minZField,
                            NumberField minRSquaredField,
                            NumberField minWindowSizeField,
                            NumberField maxPValueField,
                            NumberField maxAdfValueField,
                            NumberField checkIntervalField,
                            NumberField minCorrelationField,
                            NumberField minVolumeField,
                            NumberField usePairsField,
                            TextArea minimumLotBlacklistField,
                            NumberField maxShortMarginSizeField,
                            NumberField maxLongMarginSizeField,
                            NumberField leverageField,
                            NumberField exitTakeField,
                            NumberField exitStopField,
                            NumberField exitZMinField,
                            NumberField exitZMaxField,
                            NumberField exitZMaxPercentField,
                            NumberField exitTimeMinutesField,
                            NumberField exitBreakEvenPercentField,
                            NumberField exitNegativeZMinProfitPercentField,
                            Checkbox useMinZFilterCheckbox,
                            Checkbox useMinRSquaredFilterCheckbox,
                            Checkbox useMinPValueFilterCheckbox,
                            Checkbox useMaxAdfValueFilterCheckbox,
                            Checkbox useMinCorrelationFilterCheckbox,
                            Checkbox useMinVolumeFilterCheckbox,
                            Checkbox useExitTakeCheckbox,
                            Checkbox useExitStopCheckbox,
                            Checkbox useExitZMinCheckbox,
                            Checkbox useExitZMaxCheckbox,
                            Checkbox useExitZMaxPercentCheckbox,
                            Checkbox useExitTimeMinutesCheckbox,
                            Checkbox useExitBreakEvenPercentCheckbox,
                            Checkbox useExitNegativeZMinProfitPercentCheckbox) {

        settingsBinder.forField(timeframeField)
                .withValidator(new StringLengthValidator("Таймфрейм не может быть пустым", 1, null))
                .bind(Settings::getTimeframe, Settings::setTimeframe);

        settingsBinder.forField(candleLimitField)
                .withValidator(new DoubleRangeValidator("Количество свечей должно быть больше 0", 1.0, Double.MAX_VALUE))
                .bind(Settings::getCandleLimit, Settings::setCandleLimit);

        settingsBinder.forField(minZField).bind(Settings::getMinZ, Settings::setMinZ);
        settingsBinder.forField(minRSquaredField).bind(Settings::getMinRSquared, Settings::setMinRSquared);
        settingsBinder.forField(minWindowSizeField).bind(Settings::getMinWindowSize, Settings::setMinWindowSize);
        settingsBinder.forField(maxPValueField).bind(Settings::getMaxPValue, Settings::setMaxPValue);
        settingsBinder.forField(maxAdfValueField).bind(Settings::getMaxAdfValue, Settings::setMaxAdfValue);
        settingsBinder.forField(checkIntervalField).bind(Settings::getCheckInterval, Settings::setCheckInterval);
        settingsBinder.forField(maxShortMarginSizeField).bind(Settings::getMaxShortMarginSize, Settings::setMaxShortMarginSize);
        settingsBinder.forField(maxLongMarginSizeField).bind(Settings::getMaxLongMarginSize, Settings::setMaxLongMarginSize);

        settingsBinder.forField(leverageField)
                .withValidator(new DoubleRangeValidator("Плечо должно быть больше 0", 0.1, Double.MAX_VALUE))
                .bind(Settings::getLeverage, Settings::setLeverage);


        settingsBinder.forField(exitTakeField).bind(Settings::getExitTake, Settings::setExitTake);
        settingsBinder.forField(exitStopField).bind(Settings::getExitStop, Settings::setExitStop);
        settingsBinder.forField(exitZMinField).bind(Settings::getExitZMin, Settings::setExitZMin);
        settingsBinder.forField(exitZMaxField).bind(Settings::getExitZMax, Settings::setExitZMax);
        settingsBinder.forField(exitZMaxPercentField).bind(Settings::getExitZMaxPercent, Settings::setExitZMaxPercent);
        settingsBinder.forField(exitTimeMinutesField).bind(Settings::getExitTimeMinutes, Settings::setExitTimeMinutes);
        settingsBinder.forField(exitBreakEvenPercentField).bind(Settings::getExitBreakEvenPercent, Settings::setExitBreakEvenPercent);
        settingsBinder.forField(exitNegativeZMinProfitPercentField).bind(Settings::getExitNegativeZMinProfitPercent, Settings::setExitNegativeZMinProfitPercent);
        settingsBinder.forField(minCorrelationField).bind(Settings::getMinCorrelation, Settings::setMinCorrelation);
        settingsBinder.forField(minVolumeField).bind(Settings::getMinVolume, Settings::setMinVolume);
        settingsBinder.forField(usePairsField).bind(Settings::getUsePairs, Settings::setUsePairs);

        // Bind minimum lot blacklist field
        settingsBinder.forField(minimumLotBlacklistField).bind(Settings::getMinimumLotBlacklist, Settings::setMinimumLotBlacklist);

        // Bind filter checkboxes
        settingsBinder.forField(useMinZFilterCheckbox).bind(Settings::isUseMinZFilter, Settings::setUseMinZFilter);
        settingsBinder.forField(useMinRSquaredFilterCheckbox).bind(Settings::isUseMinRSquaredFilter, Settings::setUseMinRSquaredFilter);
        settingsBinder.forField(useMinPValueFilterCheckbox).bind(Settings::isUseMaxPValueFilter, Settings::setUseMaxPValueFilter);
        settingsBinder.forField(useMaxAdfValueFilterCheckbox).bind(Settings::isUseMaxAdfValueFilter, Settings::setUseMaxAdfValueFilter);
        settingsBinder.forField(useMinCorrelationFilterCheckbox).bind(Settings::isUseMinCorrelationFilter, Settings::setUseMinCorrelationFilter);
        settingsBinder.forField(useMinVolumeFilterCheckbox).bind(Settings::isUseMinVolumeFilter, Settings::setUseMinVolumeFilter);

        // Bind exit strategy checkboxes
        settingsBinder.forField(useExitTakeCheckbox).bind(Settings::isUseExitTake, Settings::setUseExitTake);
        settingsBinder.forField(useExitStopCheckbox).bind(Settings::isUseExitStop, Settings::setUseExitStop);
        settingsBinder.forField(useExitZMinCheckbox).bind(Settings::isUseExitZMin, Settings::setUseExitZMin);
        settingsBinder.forField(useExitZMaxCheckbox).bind(Settings::isUseExitZMax, Settings::setUseExitZMax);
        settingsBinder.forField(useExitZMaxPercentCheckbox).bind(Settings::isUseExitZMaxPercent, Settings::setUseExitZMaxPercent);
        settingsBinder.forField(useExitTimeMinutesCheckbox).bind(Settings::isUseExitTimeMinutes, Settings::setUseExitTimeMinutes);
        settingsBinder.forField(useExitBreakEvenPercentCheckbox).bind(Settings::isUseExitBreakEvenPercent, Settings::setUseExitBreakEvenPercent);
        settingsBinder.forField(useExitNegativeZMinProfitPercentCheckbox).bind(Settings::isUseExitNegativeZMinProfitPercent, Settings::setUseExitNegativeZMinProfitPercent);
    }

    /**
     * Привязывает поля скоринга к настройкам
     */
    private void bindScoringFields(NumberField zScoreWeightField, NumberField pixelSpreadWeightField,
                                   NumberField cointegrationWeightField, NumberField modelQualityWeightField,
                                   NumberField statisticsWeightField, NumberField bonusWeightField,
                                   Checkbox useZScoreScoringCheckbox, Checkbox usePixelSpreadScoringCheckbox,
                                   Checkbox useCointegrationScoringCheckbox, Checkbox useModelQualityScoringCheckbox,
                                   Checkbox useStatisticsScoringCheckbox, Checkbox useBonusScoringCheckbox) {

        // Bind scoring weight fields
        settingsBinder.forField(zScoreWeightField)
                .withValidator(new DoubleRangeValidator("Вес Z-Score должен быть больше 0", 0.0, Double.MAX_VALUE))
                .bind(Settings::getZScoreScoringWeight, Settings::setZScoreScoringWeight);

        settingsBinder.forField(pixelSpreadWeightField)
                .withValidator(new DoubleRangeValidator("Вес пиксельного спреда должен быть больше 0", 0.0, Double.MAX_VALUE))
                .bind(Settings::getPixelSpreadScoringWeight, Settings::setPixelSpreadScoringWeight);

        settingsBinder.forField(cointegrationWeightField)
                .withValidator(new DoubleRangeValidator("Вес коинтеграции должен быть больше 0", 0.0, Double.MAX_VALUE))
                .bind(Settings::getCointegrationScoringWeight, Settings::setCointegrationScoringWeight);

        settingsBinder.forField(modelQualityWeightField)
                .withValidator(new DoubleRangeValidator("Вес качества модели должен быть больше 0", 0.0, Double.MAX_VALUE))
                .bind(Settings::getModelQualityScoringWeight, Settings::setModelQualityScoringWeight);

        settingsBinder.forField(statisticsWeightField)
                .withValidator(new DoubleRangeValidator("Вес статистики должен быть больше 0", 0.0, Double.MAX_VALUE))
                .bind(Settings::getStatisticsScoringWeight, Settings::setStatisticsScoringWeight);

        settingsBinder.forField(bonusWeightField)
                .withValidator(new DoubleRangeValidator("Вес бонусов должен быть больше 0", 0.0, Double.MAX_VALUE))
                .bind(Settings::getBonusScoringWeight, Settings::setBonusScoringWeight);

        // Bind scoring checkbox fields  
        settingsBinder.forField(useZScoreScoringCheckbox).bind(Settings::isUseZScoreScoring, Settings::setUseZScoreScoring);
        settingsBinder.forField(usePixelSpreadScoringCheckbox).bind(Settings::isUsePixelSpreadScoring, Settings::setUsePixelSpreadScoring);
        settingsBinder.forField(useCointegrationScoringCheckbox).bind(Settings::isUseCointegrationScoring, Settings::setUseCointegrationScoring);
        settingsBinder.forField(useModelQualityScoringCheckbox).bind(Settings::isUseModelQualityScoring, Settings::setUseModelQualityScoring);
        settingsBinder.forField(useStatisticsScoringCheckbox).bind(Settings::isUseStatisticsScoring, Settings::setUseStatisticsScoring);
        settingsBinder.forField(useBonusScoringCheckbox).bind(Settings::isUseBonusScoring, Settings::setUseBonusScoring);
    }

    /**
     * Привязывает поля усреднения к настройкам
     */
    private void bindAveragingFields(Checkbox autoAveragingCheckbox,
                                     NumberField averagingDrawdownThresholdField,
                                     NumberField averagingVolumeMultiplierField,
                                     NumberField averagingDrawdownMultiplierField,
                                     NumberField maxAveragingCountField) {

        // Bind averaging checkbox
        settingsBinder.forField(autoAveragingCheckbox)
                .bind(Settings::isAutoAveragingEnabled, Settings::setAutoAveragingEnabled);

        // Bind averaging drawdown threshold field
        settingsBinder.forField(averagingDrawdownThresholdField)
                .withValidator(new DoubleRangeValidator("Просадка должна быть больше 0.1%", 0.1, 100.0))
                .bind(Settings::getAveragingDrawdownThreshold, Settings::setAveragingDrawdownThreshold);

        // Bind averaging volume multiplier field
        settingsBinder.forField(averagingVolumeMultiplierField)
                .withValidator(new DoubleRangeValidator("Множитель объема должен быть больше 1.0", 1.0, 10.0))
                .bind(Settings::getAveragingVolumeMultiplier, Settings::setAveragingVolumeMultiplier);

        // Bind averaging drawdown multiplier field
        settingsBinder.forField(averagingDrawdownMultiplierField)
                .withValidator(new DoubleRangeValidator("Множитель просадки должен быть больше 1.0", 1.0, 10.0))
                .bind(Settings::getAveragingDrawdownMultiplier, Settings::setAveragingDrawdownMultiplier);

        // Bind max averaging count field
        settingsBinder.forField(maxAveragingCountField)
                .withValidator(new DoubleRangeValidator("Максимальное количество усреднений должно быть больше 1", 1.0, 20.0))
                .bind(settings -> (double) settings.getMaxAveragingCount(),
                        (settings, value) -> settings.setMaxAveragingCount(value.intValue()));
    }

    private void setupValidation() {
        settingsBinder.setStatusLabel(null);
    }

    private void saveSettings() {
        try {
            //todo баг - когда ставишь/снимаешь Автотрейдинг и жмешь Сохранить настройки - чекбокс сбрасывается! Возможно при сохранении мы берем настройки до изменения чекбокса!
            settingsBinder.writeBean(currentSettings);
            settingsService.save(currentSettings);
            Notification.show("Настройки сохранены успешно");
            log.info("Настройки сохранены успешно");
        } catch (ValidationException e) {
            String errorMessage = "Проверьте правильность заполнения полей: " +
                    e.getValidationErrors().stream()
                            .map(ValidationResult::getErrorMessage)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("Неизвестная ошибка");

            Notification.show(errorMessage);
            log.warn("⚠️ Настройки не прошли валидацию при сохранении: {}", errorMessage);
        } catch (Exception e) {
            String errorMessage = "Ошибка сохранения настроек: " + e.getMessage();
            Notification.show(errorMessage);
            log.error("❌ Ошибка сохранения настроек", e);
        }
    }

    public void refreshSettings() {
        loadCurrentSettings();
        autoTradingCheckbox.setValue(currentSettings.isAutoTradingEnabled());
        settingsBinder.readBean(currentSettings);
    }

    public void setAutoTradingChangeCallback(Runnable callback) {
        this.autoTradingChangeCallback = callback;
    }

    public boolean isAutoTradingEnabled() {
        return autoTradingCheckbox.getValue();
    }

    /**
     * Создает компоновку для фильтра с чекбоксом и полем ввода
     */
    private HorizontalLayout createFilterLayout(Checkbox checkbox, NumberField field) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        layout.setSpacing(true);

        // Устанавливаем начальное состояние поля в зависимости от чекбокса
        field.setEnabled(checkbox.getValue());

        // Добавляем listener для активации/деактивации поля
        checkbox.addValueChangeListener(event -> {
            field.setEnabled(event.getValue());
        });

        layout.add(checkbox, field);
        layout.setFlexGrow(0, checkbox);
        layout.setFlexGrow(1, field);

        return layout;
    }

    private Details createCapitalManagementSection(NumberField usePairsField,
                                                   NumberField maxShortMarginSize, 
                                                   NumberField maxLongMarginSize, 
                                                   NumberField leverageField,
                                                   Checkbox autoAveragingCheckbox,
                                                   NumberField averagingDrawdownThresholdField,
                                                   NumberField averagingVolumeMultiplierField,
                                                   NumberField averagingDrawdownMultiplierField,
                                                   NumberField maxAveragingCountField) {
        
        FormLayout capitalForm = createFormLayout();
        
        // Настраиваем placeholder и helper text для полей усреднения
        averagingDrawdownThresholdField.setPlaceholder("10.0");
        averagingDrawdownThresholdField.setHelperText("Порог просадки в процентах для первого автоматического усреднения");
        
        averagingVolumeMultiplierField.setPlaceholder("1.5");
        averagingVolumeMultiplierField.setHelperText("Множитель объема для каждой позиции усреднения");
        
        averagingDrawdownMultiplierField.setPlaceholder("1.5");
        averagingDrawdownMultiplierField.setHelperText("Множитель для расчета следующего порога просадки");
        
        maxAveragingCountField.setPlaceholder("3");
        maxAveragingCountField.setHelperText("Максимальное количество усреднений для одной пары");
        
        // Логика активации/деактивации полей усреднения
        boolean isAutoAveragingEnabled = currentSettings.isAutoAveragingEnabled();
        averagingDrawdownThresholdField.setEnabled(isAutoAveragingEnabled);
        averagingVolumeMultiplierField.setEnabled(isAutoAveragingEnabled);
        averagingDrawdownMultiplierField.setEnabled(isAutoAveragingEnabled);
        maxAveragingCountField.setEnabled(isAutoAveragingEnabled);
        
        autoAveragingCheckbox.addValueChangeListener(event -> {
            boolean enabled = event.getValue();
            averagingDrawdownThresholdField.setEnabled(enabled);
            averagingVolumeMultiplierField.setEnabled(enabled);
            averagingDrawdownMultiplierField.setEnabled(enabled);
            maxAveragingCountField.setEnabled(enabled);
            
            // Обновляем информацию о капитале при изменении настроек усреднения
            updateCapitalInfo();
        });
        
        // Слушатели для обновления информации о капитале
        usePairsField.addValueChangeListener(e -> updateCapitalInfo());
        maxShortMarginSize.addValueChangeListener(e -> updateCapitalInfo());
        maxLongMarginSize.addValueChangeListener(e -> updateCapitalInfo());
        averagingVolumeMultiplierField.addValueChangeListener(e -> updateCapitalInfo());
        maxAveragingCountField.addValueChangeListener(e -> updateCapitalInfo());
        
        // Создаем спан для отображения информации о капитале
        capitalInfoSpan = new Span();
        capitalInfoSpan.getStyle().set("font-weight", "bold").set("margin-top", "1rem").set("display", "block");
        
        capitalForm.add(
                usePairsField,
                maxShortMarginSize,
                maxLongMarginSize,
                leverageField,
                autoAveragingCheckbox,
                averagingDrawdownThresholdField,
                averagingVolumeMultiplierField,
                averagingDrawdownMultiplierField,
                maxAveragingCountField,
                capitalInfoSpan
        );
        
        Details section = createDetailsCard("💰 Управление капиталом", 
                "Настройки депозита, управления рисками и автоматического усреднения", capitalForm);
        section.setOpened(true);
        
        return section;
    }
    
    private void updateCapitalInfo() {
        try {
            // Получаем текущие значения из полей
            Settings tempSettings = Settings.builder()
                    .usePairs(getCurrentDoubleValue("usePairs", currentSettings.getUsePairs()))
                    .maxShortMarginSize(getCurrentDoubleValue("maxShortMarginSize", currentSettings.getMaxShortMarginSize()))
                    .maxLongMarginSize(getCurrentDoubleValue("maxLongMarginSize", currentSettings.getMaxLongMarginSize()))
                    .autoAveragingEnabled(getCurrentBooleanValue("autoAveragingEnabled", currentSettings.isAutoAveragingEnabled()))
                    .averagingVolumeMultiplier(getCurrentDoubleValue("averagingVolumeMultiplier", currentSettings.getAveragingVolumeMultiplier()))
                    .maxAveragingCount(getCurrentIntValue("maxAveragingCount", currentSettings.getMaxAveragingCount()))
                    .build();
            
            // Рассчитываем требуемый капитал
            CapitalCalculationService.CapitalRequirement requirement = 
                    capitalCalculationService.calculateRequiredCapitalAlternative(tempSettings);
            
            // Получаем реальный баланс с OKX
            double availableBalance = portfolioService.getBalanceUSDT().doubleValue();
            
            // Проверяем превышение депозита с реальным балансом
            CapitalCalculationService.DepositCheckResult result = 
                    capitalCalculationService.checkDeposit(requirement, availableBalance);
            
            // Форматируем сообщение
            String message = String.format("При данных настройках требуется: %.2f$ (базовый: %.2f$, усреднение: %.2f$)", 
                    requirement.getTotalRequiredCapital(),
                    requirement.getTotalBaseCapital(),
                    requirement.getTotalAveragingCapital());
            
            // Устанавливаем цвет в зависимости от превышения
            if (result.isExceeded()) {
                capitalInfoSpan.getStyle().set("color", "red");
                message = "❌ " + message + String.format(" - превышение на %.2f$ (доступно: %.2f$)", result.getDifference(), availableBalance);
            } else {
                capitalInfoSpan.getStyle().set("color", "green");
                message = "✅ " + message + String.format(" (доступно: %.2f$)", availableBalance);
            }
            
            capitalInfoSpan.setText(message);
            
        } catch (Exception e) {
            capitalInfoSpan.setText("⚠️ Ошибка расчета капитала: " + e.getMessage());
            capitalInfoSpan.getStyle().set("color", "orange");
        }
    }
    
    private double getCurrentDoubleValue(String fieldName, double defaultValue) {
        try {
            return settingsBinder.getBean() != null ? 
                    (Double) settingsBinder.getBean().getClass().getMethod("get" + capitalizeFirst(fieldName)).invoke(settingsBinder.getBean()) : 
                    defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private boolean getCurrentBooleanValue(String fieldName, boolean defaultValue) {
        try {
            return settingsBinder.getBean() != null ? 
                    (Boolean) settingsBinder.getBean().getClass().getMethod("is" + capitalizeFirst(fieldName)).invoke(settingsBinder.getBean()) : 
                    defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private int getCurrentIntValue(String fieldName, int defaultValue) {
        try {
            return settingsBinder.getBean() != null ? 
                    (Integer) settingsBinder.getBean().getClass().getMethod("get" + capitalizeFirst(fieldName)).invoke(settingsBinder.getBean()) : 
                    defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}