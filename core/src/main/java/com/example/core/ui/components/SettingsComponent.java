package com.example.core.ui.components;

import com.example.core.schedulers.UpdateTradesScheduler;
import com.example.core.services.*;
import com.example.core.ui.utils.PeriodOptions;
import com.example.core.ui.utils.TimeframeOptions;
import com.example.shared.events.GlobalSettingsUpdatedEvent;
import com.example.shared.models.Settings;
import com.example.shared.services.GlobalSettingsEventPublisher;
import com.example.shared.services.TimeframeAndPeriodService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
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
import org.springframework.context.event.EventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@SpringComponent
@UIScope
public class SettingsComponent extends VerticalLayout {

    private final SettingsService settingsService;
    private final CapitalCalculationService capitalCalculationService;
    private final PortfolioService portfolioService;
    private final AutoVolumeService autoVolumeService;
    private final SchedulerControlService schedulerControlService;
    private final TimeframeAndPeriodService timeframeAndPeriodService;
    private final GlobalSettingsEventPublisher globalSettingsEventPublisher;
    private final Binder<Settings> settingsBinder;

    private Settings currentSettings;
    private Checkbox autoTradingCheckbox;
    private Runnable autoTradingChangeCallback;

    // Поля для расчета депозита и автообъема
    private Span capitalInfoSpan;
    private NumberField autoVolumeLongField;
    private NumberField autoVolumeShortField;

    // Чекбоксы для глобальных настроек свечей
    private Map<String, Checkbox> globalTimeframeCheckboxes;
    private Map<String, Checkbox> globalPeriodCheckboxes;

    // Выпадающие списки в секции "Анализ и фильтры"
    private Select<String> analysisTimeframeSelect;
    private Select<String> analysisPeriodSelect;

    public SettingsComponent(SettingsService settingsService,
                             CapitalCalculationService capitalCalculationService,
                             PortfolioService portfolioService,
                             AutoVolumeService autoVolumeService,
                             SchedulerControlService schedulerControlService,
                             TimeframeAndPeriodService timeframeAndPeriodService,
                             GlobalSettingsEventPublisher globalSettingsEventPublisher) {
        this.settingsService = settingsService;
        this.capitalCalculationService = capitalCalculationService;
        this.portfolioService = portfolioService;
        this.autoVolumeService = autoVolumeService;
        this.schedulerControlService = schedulerControlService;
        this.timeframeAndPeriodService = timeframeAndPeriodService;
        this.globalSettingsEventPublisher = globalSettingsEventPublisher;
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
        // Create form fields - создаем выпадающие списки с взаимодействием
        Select<String> timeframeField = createTimeframeSelect();
        Select<String> periodField = createPeriodSelect();

        // Добавляем логику взаимодействия между таймфреймом и периодом
        setupTimeframePeriodInteraction(timeframeField, periodField);
        NumberField minZField = new NumberField("Min Z");
        NumberField minRSquaredField = new NumberField("Min R-Squared");
        NumberField minWindowSizeField = new NumberField("Min windowSize");
        NumberField minPValueField = new NumberField("Min pValue");
        NumberField maxAdfValueField = new NumberField("Max adfValue");
        NumberField minCorrelationField = new NumberField("Min corr");
        NumberField minVolumeField = new NumberField("Min Vol (млн $)");
        NumberField checkIntervalField = new NumberField("Обновление (мин)");

        // Create filter checkboxes
        Checkbox useMinZFilterCheckbox = new Checkbox("");
        Checkbox useMinRSquaredFilterCheckbox = new Checkbox("");
        Checkbox useMinPValueFilterCheckbox = new Checkbox("");
        Checkbox useMaxAdfValueFilterCheckbox = new Checkbox("");
        Checkbox useMinCorrelationFilterCheckbox = new Checkbox("");
        Checkbox useMinVolumeFilterCheckbox = new Checkbox("");
        Checkbox useMinIntersectionsFilterCheckbox = new Checkbox("");
        Checkbox useZScoreDeclineFilterCheckbox = new Checkbox("");
        Checkbox useScoreFilteringCheckbox = new Checkbox("");
        
        NumberField zScoreDeclineCandlesCountField = new NumberField("Вход по снижению zScore");
        zScoreDeclineCandlesCountField.setHelperText("Количество последних точек");
        zScoreDeclineCandlesCountField.setStep(1.0);
        zScoreDeclineCandlesCountField.setMin(1.0);
        zScoreDeclineCandlesCountField.setMax(10.0);
        zScoreDeclineCandlesCountField.setValue(4.0);
        zScoreDeclineCandlesCountField.setStepButtonsVisible(true);
        Checkbox useStablePairsForMonitoringCheckbox = new Checkbox("Искать из Постоянный список для мониторинга");
        Checkbox useFoundStablePairsCheckbox = new Checkbox("Искать из Найденные стабильные пары");

        NumberField minStabilityScoreField = new NumberField("Искать по Скор");
        minStabilityScoreField.setHelperText("Минимальный скор стабильности для отбора пар");
        setNumberFieldProperties(minStabilityScoreField, 1, 0);
        
        // Логика активации чекбокса "Искать по Скор" только когда активны чекбоксы мониторинга или найденных пар
        Runnable updateScoreFilteringState = () -> {
            boolean canUseScoreFiltering = useStablePairsForMonitoringCheckbox.getValue() || useFoundStablePairsCheckbox.getValue();
            useScoreFilteringCheckbox.setEnabled(canUseScoreFiltering);
            minStabilityScoreField.setEnabled(canUseScoreFiltering && useScoreFilteringCheckbox.getValue());
        };
        
        useStablePairsForMonitoringCheckbox.addValueChangeListener(e -> updateScoreFilteringState.run());
        useFoundStablePairsCheckbox.addValueChangeListener(e -> updateScoreFilteringState.run());
        useScoreFilteringCheckbox.addValueChangeListener(e -> updateScoreFilteringState.run());

        // Min intersections field
        NumberField minIntersectionsField = new NumberField("Искать по кол-ву пересечений");
        minIntersectionsField.setHelperText("Минимальное количество пересечений нормализованных цен");
        setNumberFieldProperties(minIntersectionsField, 1, 1);

        // Minimum lot blacklist field
        TextArea minimumLotBlacklistField = new TextArea("Блэклист тикеров мин. лота");
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
        Checkbox useExitTakeCheckbox = new Checkbox("");
        Checkbox useExitStopCheckbox = new Checkbox("");
        Checkbox useExitZMinCheckbox = new Checkbox("");
        Checkbox useExitZMaxCheckbox = new Checkbox("");
        Checkbox useExitZMaxPercentCheckbox = new Checkbox("");
        Checkbox useExitTimeMinutesCheckbox = new Checkbox("");
        Checkbox useExitBreakEvenPercentCheckbox = new Checkbox("");
        Checkbox useExitNegativeZMinProfitPercentCheckbox = new Checkbox("");

        NumberField usePairsField = new NumberField("Кол-во пар");

        // Set step and min values for number fields
        // Убрано поле candleLimitField - теперь используется periodField (Select)
        setNumberFieldProperties(minZField, 0.1, 0.0);
        setNumberFieldProperties(minRSquaredField, 0.1, 0.1);
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
        setNumberFieldProperties(minIntersectionsField, 1, 0);

        // Create sections
        // СНАЧАЛА добавляем секцию Свечи в самом верху
        add(createCandleGlobalSettingsSection());

        add(createAnalysisSection(timeframeField, periodField, minZField, minRSquaredField, minWindowSizeField,
                minPValueField, maxAdfValueField, minCorrelationField, minVolumeField,
                checkIntervalField, minimumLotBlacklistField, useMinZFilterCheckbox, useMinRSquaredFilterCheckbox,
                useMinPValueFilterCheckbox, useMaxAdfValueFilterCheckbox, useMinCorrelationFilterCheckbox,
                useMinVolumeFilterCheckbox, useMinIntersectionsFilterCheckbox, minIntersectionsField,
                useZScoreDeclineFilterCheckbox, zScoreDeclineCandlesCountField,
                useStablePairsForMonitoringCheckbox, useFoundStablePairsCheckbox, useScoreFilteringCheckbox, minStabilityScoreField));

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

        // Создаем поля автообъема
        Checkbox autoVolumeCheckbox = new Checkbox("Автообъем");
        autoVolumeLongField = new NumberField("Текущий автообъем лонг ($)");
        autoVolumeShortField = new NumberField("Текущий автообъем шорт ($)");

        // Настраиваем поля автообъема как readonly
        autoVolumeLongField.setReadOnly(true);
        autoVolumeShortField.setReadOnly(true);
        autoVolumeLongField.setHelperText("Рассчитанный автоматически размер позиции лонг");
        autoVolumeShortField.setHelperText("Рассчитанный автоматически размер позиции шорт");

        add(createCapitalManagementSection(usePairsField, maxShortMarginSize, maxLongMarginSize,
                leverageField, autoAveragingCheckbox, averagingDrawdownThresholdField,
                averagingVolumeMultiplierField, averagingDrawdownMultiplierField, maxAveragingCountField,
                autoVolumeCheckbox, autoVolumeLongField, autoVolumeShortField));

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

        // Создаем секцию анализа волатильности BTC
        add(createBtcAnalysisSection());

        add(createScoringWeightsSection());

        // Создаем секцию управления расписанием шедуллеров
        add(createSchedulerControlSection());

        // Bind fields to settings object
        bindFields(
                timeframeField,
                periodField,
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
                useMinIntersectionsFilterCheckbox,
                minIntersectionsField,
                useZScoreDeclineFilterCheckbox,
                zScoreDeclineCandlesCountField,
                useStablePairsForMonitoringCheckbox,
                useFoundStablePairsCheckbox,
                useScoreFilteringCheckbox,
                minStabilityScoreField,
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

        // Привязываем поля автообъема
        bindAutoVolumeFields(autoVolumeCheckbox);

        settingsBinder.readBean(currentSettings);
    }

    /**
     * Создает секцию глобальных настроек свечей с чекбоксами для таймфреймов и периодов
     */
    private Details createCandleGlobalSettingsSection() {
        FormLayout candleForm = createFormLayout();

        // Создаем заголовки для групп чекбоксов
        H4 timeframesTitle = new H4("🕒 Активные таймфреймы");
        timeframesTitle.getStyle().set("margin", "0 0 0.5rem 0").set("color", "var(--lumo-primary-text-color)");

        H4 periodsTitle = new H4("📅 Активные периоды");
        periodsTitle.getStyle().set("margin", "1rem 0 0.5rem 0").set("color", "var(--lumo-primary-text-color)");

        // Получаем все доступные таймфреймы и периоды из сервиса
        Map<String, String> allTimeframes = timeframeAndPeriodService.getAllTimeframes();
        Map<String, String> allPeriods = timeframeAndPeriodService.getAllPeriods();

        // Получаем текущие активные таймфреймы и периоды
        String currentTimeframes = currentSettings.getGlobalActiveTimeframes();
        String currentPeriods = currentSettings.getGlobalActivePeriods();

        List<String> activeTimeframes = currentTimeframes != null ?
                List.of(currentTimeframes.split(",")) : List.of("15m");
        List<String> activePeriods = currentPeriods != null ?
                List.of(currentPeriods.split(",")) : List.of("1 месяц", "3 месяца", "6 месяцев", "1 год");

        // Создаем чекбоксы для таймфреймов
        VerticalLayout timeframeCheckboxes = new VerticalLayout();
        timeframeCheckboxes.setSpacing(false);
        timeframeCheckboxes.setPadding(false);

        Map<String, Checkbox> timeframeCheckboxMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : allTimeframes.entrySet()) {
            String displayName = entry.getKey();
            String apiCode = entry.getValue();
            Checkbox checkbox = new Checkbox(displayName);
            checkbox.setValue(activeTimeframes.contains(apiCode));
            checkbox.getStyle().set("margin", "0.2rem 0");

            timeframeCheckboxMap.put(apiCode, checkbox);
            timeframeCheckboxes.add(checkbox);
        }

        // Создаем чекбоксы для периодов
        VerticalLayout periodCheckboxes = new VerticalLayout();
        periodCheckboxes.setSpacing(false);
        periodCheckboxes.setPadding(false);

        Map<String, Checkbox> periodCheckboxMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : allPeriods.entrySet()) {
            String periodName = entry.getKey();
            Checkbox checkbox = new Checkbox(periodName);
            checkbox.setValue(activePeriods.contains(periodName));
            checkbox.getStyle().set("margin", "0.2rem 0");

            periodCheckboxMap.put(periodName, checkbox);
            periodCheckboxes.add(checkbox);
        }

        // Добавляем компоненты в форму
        candleForm.add(timeframesTitle);
        candleForm.add(timeframeCheckboxes);
        candleForm.add(periodsTitle);
        candleForm.add(periodCheckboxes);

        // Привязываем чекбоксы к настройкам через отдельный метод
        bindCandleGlobalSettings(timeframeCheckboxMap, periodCheckboxMap);

        Details candleSection = createDetailsCard("🕯️ Свечи",
                "Глобальные настройки активных таймфреймов и периодов для всей системы", candleForm);
        candleSection.setOpened(true);
        return candleSection;
    }

    /**
     * Привязывает чекбоксы глобальных настроек свечей к полям Settings
     */
    private void bindCandleGlobalSettings(Map<String, Checkbox> timeframeCheckboxMap, Map<String, Checkbox> periodCheckboxMap) {
        // Сохраняем ссылки на чекбоксы для обновления при сохранении настроек
        this.globalTimeframeCheckboxes = timeframeCheckboxMap;
        this.globalPeriodCheckboxes = periodCheckboxMap;

        // Добавляем логирование изменений
        for (Map.Entry<String, Checkbox> entry : timeframeCheckboxMap.entrySet()) {
            String apiCode = entry.getKey();
            Checkbox checkbox = entry.getValue();
            checkbox.addValueChangeListener(event -> {
                log.info("🕒 Таймфрейм {} {}", apiCode, event.getValue() ? "АКТИВИРОВАН" : "ДЕАКТИВИРОВАН");
                updateGlobalTimeframes();
            });
        }

        for (Map.Entry<String, Checkbox> entry : periodCheckboxMap.entrySet()) {
            String periodName = entry.getKey();
            Checkbox checkbox = entry.getValue();
            checkbox.addValueChangeListener(event -> {
                log.info("📅 Период '{}' {}", periodName, event.getValue() ? "АКТИВИРОВАН" : "ДЕАКТИВИРОВАН");
                updateGlobalPeriods();
            });
        }
    }

    /**
     * Обновляет глобальные таймфреймы на основе состояния чекбоксов
     */
    private void updateGlobalTimeframes() {
        if (globalTimeframeCheckboxes != null) {
            String activeTimeframes = globalTimeframeCheckboxes.entrySet().stream()
                    .filter(entry -> entry.getValue().getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining(","));
            currentSettings.setGlobalActiveTimeframes(activeTimeframes.isEmpty() ? "15m" : activeTimeframes);
        }
    }

    /**
     * Обновляет глобальные периоды на основе состояния чекбоксов
     */
    private void updateGlobalPeriods() {
        if (globalPeriodCheckboxes != null) {
            String activePeriods = globalPeriodCheckboxes.entrySet().stream()
                    .filter(entry -> entry.getValue().getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining(","));
            currentSettings.setGlobalActivePeriods(activePeriods.isEmpty() ? "1 год" : activePeriods);
        }
    }

    /**
     * Создает выпадающий список для выбора таймфрейма
     */
    private Select<String> createTimeframeSelect() {
        Select<String> timeframeSelect = new Select<>();
        timeframeSelect.setLabel("Таймфрейм");

        // Получаем активные таймфреймы из глобальных настроек
        List<String> activeTimeframes = timeframeAndPeriodService.getActiveTimeframes(
                currentSettings.getGlobalActiveTimeframes());
        timeframeSelect.setItems(activeTimeframes);

        // Устанавливаем значение по умолчанию на основе текущих настроек
        String currentTimeframeApi = currentSettings.getTimeframe();
        String currentTimeframeDisplay = TimeframeOptions.getDisplayName(currentTimeframeApi);

        // Проверяем, что текущий таймфрейм доступен в активных
        if (activeTimeframes.contains(currentTimeframeDisplay)) {
            timeframeSelect.setValue(currentTimeframeDisplay);
        } else if (!activeTimeframes.isEmpty()) {
            // Если текущий таймфрейм недоступен, выбираем первый доступный
            timeframeSelect.setValue(activeTimeframes.get(0));
        }

        timeframeSelect.setHelperText("Выберите временной интервал для анализа");

        // Сохраняем ссылку для обновления при изменении глобальных настроек
        this.analysisTimeframeSelect = timeframeSelect;

        return timeframeSelect;
    }

    /**
     * Создает выпадающий список для выбора периода анализа
     */
    private Select<String> createPeriodSelect() {
        Select<String> periodSelect = new Select<>();
        periodSelect.setLabel("Период");

        // Получаем активные периоды из глобальных настроек
        List<String> activePeriods = timeframeAndPeriodService.getActivePeriods(
                currentSettings.getGlobalActivePeriods());
        periodSelect.setItems(activePeriods);

        // Вычисляем текущий период на основе candleLimit и timeframe
        String currentPeriod = currentSettings.calculateCurrentPeriod();

        // Проверяем, что текущий период доступен в активных
        if (activePeriods.contains(currentPeriod)) {
            periodSelect.setValue(currentPeriod);
        } else if (!activePeriods.isEmpty()) {
            // Если текущий период недоступен, выбираем первый доступный
            periodSelect.setValue(activePeriods.get(0));
        }

        periodSelect.setHelperText("Выберите период для анализа данных");

        // Сохраняем ссылку для обновления при изменении глобальных настроек
        this.analysisPeriodSelect = periodSelect;

        return periodSelect;
    }

    /**
     * Вычисляет период на основе candleLimit и timeframe
     */
    private String calculatePeriodFromCandleLimit(Integer candleLimit, String timeframe) {
        try {
            // Приблизительный расчет периода
            int daysInPeriod = switch (timeframe) {
                case "15m" -> candleLimit / (24 * 4);
                default -> candleLimit / (24 * 4);
            };

            // Определяем ближайший период
            if (daysInPeriod <= 30) return "1 месяц";
            if (daysInPeriod <= 60) return "2 месяца";
            if (daysInPeriod <= 90) return "3 месяца";
            if (daysInPeriod <= 120) return "4 месяца";
            if (daysInPeriod <= 150) return "5 месяцев";
            if (daysInPeriod <= 180) return "6 месяцев";
            if (daysInPeriod <= 210) return "7 месяцев";
            if (daysInPeriod <= 240) return "8 месяцев";
            if (daysInPeriod <= 270) return "9 месяцев";
            if (daysInPeriod <= 300) return "10 месяцев";
            if (daysInPeriod <= 330) return "11 месяцев";
            if (daysInPeriod <= 365) return "1 год";
            return "1 год";

        } catch (Exception e) {
            log.warn("Ошибка при расчете периода из candleLimit: {}", e.getMessage());
            return "1 месяц";
        }
    }

    /**
     * Вычисляет текущий период на основе candleLimit и timeframe
     */
    private String calculateCurrentPeriod() { //todo перенес в Settings, можно удалить
        try {
            int candleLimit = (int) currentSettings.getCandleLimit();
            String timeframe = currentSettings.getTimeframe();

            // Приблизительный расчет периода
            int daysInPeriod = switch (timeframe) {
//                case "1m" -> candleLimit / (24 * 60);
//                case "5m" -> candleLimit / (24 * 12);
                case "15m" -> candleLimit / (24 * 4);
//                case "1H" -> candleLimit / 24;
//                case "4H" -> candleLimit / 6;
//                case "1D" -> candleLimit;
//                case "1W" -> candleLimit * 7;
//                case "1M" -> candleLimit * 30;
                default -> candleLimit / (24 * 4);
            };

            // Определяем ближайший период
//            if (daysInPeriod <= 1) return "день";
//            if (daysInPeriod <= 7) return "неделя";
//            if (daysInPeriod <= 30) return "месяц";
            if (daysInPeriod <= 365) return "1 год";
//            if (daysInPeriod <= 730) return "2 года";
//            return "3 года";
            return "1 год";

        } catch (Exception e) {
            log.warn("Ошибка при расчете текущего периода: {}", e.getMessage());
            return PeriodOptions.getDefault();
        }
    }

    /**
     * Настраивает взаимодействие между выпадающими списками таймфрейма и периода
     */
    private void setupTimeframePeriodInteraction(Select<String> timeframeField, Select<String> periodField) {
        // При изменении таймфрейма автоматически пересчитываем период
        timeframeField.addValueChangeListener(event -> {
            if (event.getValue() != null && periodField.getValue() != null) {
                // Получаем API код выбранного таймфрейма
                String timeframeApi = TimeframeOptions.getApiCode(event.getValue());
                String currentPeriod = periodField.getValue();

                // Рассчитываем новое значение candleLimit на основе нового таймфрейма и текущего периода
                int newCandleLimit = PeriodOptions.calculateCandleLimit(timeframeApi, currentPeriod);

                // Обновляем текущие настройки временно для пересчета
                currentSettings.setTimeframe(timeframeApi);
                currentSettings.setCandleLimit(newCandleLimit);

                log.debug("🔄 Таймфрейм изменен на '{}', пересчитан candleLimit: {}", event.getValue(), newCandleLimit);
            }
        });

        // При изменении периода автоматически пересчитываем candleLimit
        periodField.addValueChangeListener(event -> {
            if (event.getValue() != null && timeframeField.getValue() != null) {
                // Получаем API код текущего таймфрейма
                String timeframeApi = TimeframeOptions.getApiCode(timeframeField.getValue());
                String selectedPeriod = event.getValue();

                // Рассчитываем новое значение candleLimit
                int newCandleLimit = PeriodOptions.calculateCandleLimit(timeframeApi, selectedPeriod);

                // Обновляем текущие настройки временно для пересчета
                currentSettings.setCandleLimit(newCandleLimit);

                log.debug("🔄 Период изменен на '{}', пересчитан candleLimit: {}", event.getValue(), newCandleLimit);
            }
        });
    }

    private Details createAnalysisSection(Select<String> timeframeField, Select<String> periodField,
                                          NumberField minZField, NumberField minRSquaredField,
                                          NumberField minWindowSizeField, NumberField minPValueField,
                                          NumberField maxAdfValueField, NumberField minCorrelationField,
                                          NumberField minVolumeField, NumberField checkIntervalField,
                                          TextArea minimumLotBlacklistField,
                                          Checkbox useMinZFilterCheckbox,
                                          Checkbox useMinRSquaredFilterCheckbox, Checkbox useMinPValueFilterCheckbox,
                                          Checkbox useMaxAdfValueFilterCheckbox, Checkbox useMinCorrelationFilterCheckbox,
                                          Checkbox useMinVolumeFilterCheckbox, Checkbox useMinIntersectionsFilterCheckbox,
                                          NumberField minIntersectionsField, Checkbox useZScoreDeclineFilterCheckbox,
                                          NumberField zScoreDeclineCandlesCountField, Checkbox useStablePairsForMonitoringCheckbox,
                                          Checkbox useFoundStablePairsCheckbox, Checkbox useScoreFilteringCheckbox, 
                                          NumberField minStabilityScoreField) {

        FormLayout analysisForm = createSingleColumnFormLayout();

        // Создаем компоненты фильтров с чекбоксами
        HorizontalLayout minZLayout = createFilterLayout(useMinZFilterCheckbox, minZField);
        HorizontalLayout minRSquaredLayout = createFilterLayout(useMinRSquaredFilterCheckbox, minRSquaredField);
        HorizontalLayout minPValueLayout = createFilterLayout(useMinPValueFilterCheckbox, minPValueField);
        HorizontalLayout maxAdfValueLayout = createFilterLayout(useMaxAdfValueFilterCheckbox, maxAdfValueField);
        HorizontalLayout minCorrelationLayout = createFilterLayout(useMinCorrelationFilterCheckbox, minCorrelationField);
        HorizontalLayout minVolumeLayout = createFilterLayout(useMinVolumeFilterCheckbox, minVolumeField);
        HorizontalLayout minIntersectionsLayout = createFilterLayout(useMinIntersectionsFilterCheckbox, minIntersectionsField);
        HorizontalLayout zScoreDeclineLayout = createFilterLayout(useZScoreDeclineFilterCheckbox, zScoreDeclineCandlesCountField);
        
        // Создаем layout для фильтрации по скору
        HorizontalLayout scoreFilteringLayout = createFilterLayout(useScoreFilteringCheckbox, minStabilityScoreField);

        analysisForm.add(
                timeframeField, periodField, checkIntervalField,
                minZLayout, minRSquaredLayout, minWindowSizeField, minPValueLayout,
                maxAdfValueLayout, minCorrelationLayout, minVolumeLayout,
                minIntersectionsLayout, zScoreDeclineLayout, useStablePairsForMonitoringCheckbox, useFoundStablePairsCheckbox,
                scoreFilteringLayout, minimumLotBlacklistField
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

        FormLayout exitForm = createSingleColumnFormLayout();

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

    /**
     * Создает секцию анализа волатильности BTC
     */
    private Details createBtcAnalysisSection() {
        FormLayout btcForm = createSingleColumnFormLayout();

        // Создаем поля для анализа BTC
        Checkbox useBtcVolatilityFilterCheckbox = new Checkbox("Анализ BTC при автотрейдинге");
//        useBtcVolatilityFilterCheckbox.setHelperText("Блокировать автотрейдинг при повышенной волатильности Bitcoin");
        
        NumberField btcAtrThresholdMultiplierField = new NumberField("Порог ATR (множитель)");
        btcAtrThresholdMultiplierField.setHelperText("Множитель превышения среднего ATR (например, 1.3 = 30% превышение)");
        btcAtrThresholdMultiplierField.setValue(1.3);
        setNumberFieldProperties(btcAtrThresholdMultiplierField, 0.1, 1.0);
        
        NumberField btcDailyRangeMultiplierField = new NumberField("Порог дневного диапазона");
        btcDailyRangeMultiplierField.setHelperText("Множитель превышения среднего дневного диапазона");
        btcDailyRangeMultiplierField.setValue(1.3);
        setNumberFieldProperties(btcDailyRangeMultiplierField, 0.1, 1.0);
        
        NumberField maxBtcDailyChangePercentField = new NumberField("Макс. дневное изменение (%)");
        maxBtcDailyChangePercentField.setHelperText("Максимальное дневное изменение BTC в % (блокировка если превышено)");
        maxBtcDailyChangePercentField.setValue(5.0);
        setNumberFieldProperties(maxBtcDailyChangePercentField, 0.1, 0.0);

        // Логика активации полей настроек только при включенном фильтре
        Runnable updateBtcFieldsState = () -> {
            boolean isFilterEnabled = useBtcVolatilityFilterCheckbox.getValue();
            btcAtrThresholdMultiplierField.setEnabled(isFilterEnabled);
            btcDailyRangeMultiplierField.setEnabled(isFilterEnabled);
            maxBtcDailyChangePercentField.setEnabled(isFilterEnabled);
        };
        
        useBtcVolatilityFilterCheckbox.addValueChangeListener(e -> updateBtcFieldsState.run());
        updateBtcFieldsState.run(); // Инициализация состояния

        btcForm.add(
                useBtcVolatilityFilterCheckbox,
                btcAtrThresholdMultiplierField,
                btcDailyRangeMultiplierField,
                maxBtcDailyChangePercentField
        );

        // Привязываем поля к настройкам
        settingsBinder.forField(useBtcVolatilityFilterCheckbox).bind(Settings::isUseBtcVolatilityFilter, Settings::setUseBtcVolatilityFilter);
        settingsBinder.forField(btcAtrThresholdMultiplierField)
                .withValidator(new DoubleRangeValidator("Порог ATR должен быть больше 1.0", 1.0, 10.0))
                .bind(Settings::getBtcAtrThresholdMultiplier, Settings::setBtcAtrThresholdMultiplier);
        settingsBinder.forField(btcDailyRangeMultiplierField)
                .withValidator(new DoubleRangeValidator("Порог дневного диапазона должен быть больше 1.0", 1.0, 10.0))
                .bind(Settings::getBtcDailyRangeMultiplier, Settings::setBtcDailyRangeMultiplier);
        settingsBinder.forField(maxBtcDailyChangePercentField)
                .withValidator(new DoubleRangeValidator("Максимальное дневное изменение должно быть больше 0", 0.1, 50.0))
                .bind(Settings::getMaxBtcDailyChangePercent, Settings::setMaxBtcDailyChangePercent);

        return createDetailsCard("🪙 Анализ BTC",
                "Фильтрация автотрейдинга по волатильности Bitcoin", btcForm);
    }

    private Details createScoringWeightsSection() {
        FormLayout scoringForm = createSingleColumnFormLayout();

        // Создаем поля для весов скоринга
        NumberField zScoreWeightField = new NumberField("Z-Score сила (очки)");
        NumberField pixelSpreadWeightField = new NumberField("Пиксельный спред (очки)");
        NumberField cointegrationWeightField = new NumberField("Коинтеграция (очки)");
        NumberField modelQualityWeightField = new NumberField("Качество модели (очки)");
        NumberField statisticsWeightField = new NumberField("Статистика (очки)");
        NumberField bonusWeightField = new NumberField("Бонусы (очки)");

        // Создаем чекбоксы для включения/выключения компонентов
        Checkbox useZScoreScoringCheckbox = new Checkbox("");
        Checkbox usePixelSpreadScoringCheckbox = new Checkbox("");
        Checkbox useCointegrationScoringCheckbox = new Checkbox("");
        Checkbox useModelQualityScoringCheckbox = new Checkbox("");
        Checkbox useStatisticsScoringCheckbox = new Checkbox("");
        Checkbox useBonusScoringCheckbox = new Checkbox("");

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
        details.setOpened(true);
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

    private FormLayout createSingleColumnFormLayout() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1)
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

    private void bindFields(Select<String> timeframeField,
                            Select<String> periodField,
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
                            Checkbox useMinIntersectionsFilterCheckbox,
                            NumberField minIntersectionsField,
                            Checkbox useZScoreDeclineFilterCheckbox,
                            NumberField zScoreDeclineCandlesCountField,
                            Checkbox useStablePairsForMonitoringCheckbox,
                            Checkbox useFoundStablePairsCheckbox,
                            Checkbox useScoreFilteringCheckbox,
                            NumberField minStabilityScoreField,
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
                .withConverter(TimeframeOptions::getApiCode, TimeframeOptions::getDisplayName)
                .bind(Settings::getTimeframe, Settings::setTimeframe);

        settingsBinder.forField(periodField)
                .withValidator(new StringLengthValidator("Период не может быть пустым", 1, null))
                .withConverter(
                        // Конвертер из отображаемого периода в candleLimit
                        displayPeriod -> PeriodOptions.calculateCandleLimit(
                                TimeframeOptions.getApiCode(timeframeField.getValue()), displayPeriod),
                        // Конвертер из candleLimit обратно в отображаемый период
                        candleLimit -> calculatePeriodFromCandleLimit(candleLimit, TimeframeOptions.getApiCode(timeframeField.getValue()))
                )
                .bind(settings -> (int) settings.getCandleLimit(),
                        (settings, candleLimit) -> settings.setCandleLimit(candleLimit));

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

        // Bind intersection filter fields
        settingsBinder.forField(useMinIntersectionsFilterCheckbox).bind(Settings::isUseMinIntersections, Settings::setUseMinIntersections);
        settingsBinder.forField(minIntersectionsField)
                .withConverter(Double::intValue, Integer::doubleValue)
                .bind(Settings::getMinIntersections, Settings::setMinIntersections);

        // Bind stable pairs for monitoring checkbox
        settingsBinder.forField(useStablePairsForMonitoringCheckbox).bind(Settings::isUseStablePairsForMonitoring, Settings::setUseStablePairsForMonitoring);
        
        // Bind found stable pairs checkbox
        settingsBinder.forField(useFoundStablePairsCheckbox).bind(Settings::isUseFoundStablePairs, Settings::setUseFoundStablePairs);
        
        // Bind zScore decline filter checkbox and field
        settingsBinder.forField(useZScoreDeclineFilterCheckbox).bind(Settings::isUseZScoreDeclineFilter, Settings::setUseZScoreDeclineFilter);
        settingsBinder.forField(zScoreDeclineCandlesCountField)
                .withValidator(value -> value != null && value >= 1 && value <= 10, "Количество свечей должно быть от 1 до 10")
                .bind(settings -> (double) settings.getZScoreDeclineCandlesCount(),
                      (settings, value) -> settings.setZScoreDeclineCandlesCount(value.intValue()));

        // Bind score filtering checkbox and field
        settingsBinder.forField(useScoreFilteringCheckbox).bind(Settings::isUseScoreFiltering, Settings::setUseScoreFiltering);
        settingsBinder.forField(minStabilityScoreField)
                .withValidator(value -> value != null && value >= 0, "Минимальный скор должен быть больше или равен 0")
                .bind(settings -> (double) settings.getMinStabilityScore(), 
                      (settings, value) -> settings.setMinStabilityScore(value.intValue()));


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

    /**
     * Привязывает поля автообъема к настройкам
     */
    private void bindAutoVolumeFields(Checkbox autoVolumeCheckbox) {
        // Привязываем чекбокс автообъема
        settingsBinder.forField(autoVolumeCheckbox)
                .bind(Settings::isAutoVolumeEnabled, Settings::setAutoVolumeEnabled);

        // Инициализируем значения автообъема
        updateAutoVolumeValues();
    }

    /**
     * Обновляет значения полей автообъема
     */
    private void updateAutoVolumeValues() {
        try {
            // Получаем текущие настройки для расчета автообъема
            Settings settings = settingsService.getSettings();
            AutoVolumeService.AutoVolumeData autoVolumeData = autoVolumeService.calculateAutoVolume(settings);

            autoVolumeLongField.setValue(autoVolumeData.getLongVolume().doubleValue());
            autoVolumeShortField.setValue(autoVolumeData.getShortVolume().doubleValue());

            log.debug("📊 Обновлены значения автообъема: лонг={}, шорт={}",
                    autoVolumeData.getLongVolume(), autoVolumeData.getShortVolume());

        } catch (Exception e) {
            log.warn("⚠️ Ошибка при обновлении значений автообъема: {}", e.getMessage());
            autoVolumeLongField.setValue(0.0);
            autoVolumeShortField.setValue(0.0);
        }
    }

    private void setupValidation() {
        settingsBinder.setStatusLabel(null);
    }

    private void saveSettings() {
        try {
            //todo баг - когда ставишь/снимаешь Автотрейдинг и жмешь Сохранить настройки - чекбокс сбрасывается! Возможно при сохранении мы берем настройки до изменения чекбокса!
            settingsBinder.writeBean(currentSettings);
            settingsService.save(currentSettings);

            // Публикуем событие обновления глобальных настроек после успешного сохранения
            globalSettingsEventPublisher.publishGlobalSettingsUpdated(
                    currentSettings.getGlobalActiveTimeframes(),
                    currentSettings.getGlobalActivePeriods());

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

        // Обновляем состояние чекбоксов глобальных настроек свечей
        refreshGlobalCandleSettings();
    }

    /**
     * Обновляет состояние чекбоксов глобальных настроек свечей из текущих настроек
     */
    private void refreshGlobalCandleSettings() {
        if (globalTimeframeCheckboxes != null) {
            String timeframes = currentSettings.getGlobalActiveTimeframes();
            List<String> activeTimeframes = timeframes != null ?
                    List.of(timeframes.split(",")) : List.of("15m");

            globalTimeframeCheckboxes.forEach((apiCode, checkbox) ->
                    checkbox.setValue(activeTimeframes.contains(apiCode)));
        }

        if (globalPeriodCheckboxes != null) {
            String periods = currentSettings.getGlobalActivePeriods();
            List<String> activePeriods = periods != null ?
                    List.of(periods.split(",")) : List.of("1 месяц", "3 месяца", "6 месяцев", "1 год");

            globalPeriodCheckboxes.forEach((periodName, checkbox) ->
                    checkbox.setValue(activePeriods.contains(periodName)));
        }
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
                                                   NumberField maxAveragingCountField,
                                                   Checkbox autoVolumeCheckbox,
                                                   NumberField autoVolumeLongField,
                                                   NumberField autoVolumeShortField) {

        FormLayout capitalForm = createSingleColumnFormLayout();

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
        // averagingVolumeMultiplierField всегда активно для ручного усреднения
        averagingDrawdownMultiplierField.setEnabled(isAutoAveragingEnabled);
        maxAveragingCountField.setEnabled(isAutoAveragingEnabled);

        autoAveragingCheckbox.addValueChangeListener(event -> {
            boolean enabled = event.getValue();
            averagingDrawdownThresholdField.setEnabled(enabled);
            // averagingVolumeMultiplierField остается всегда активным для ручного усреднения
            averagingDrawdownMultiplierField.setEnabled(enabled);
            maxAveragingCountField.setEnabled(enabled);

            // Обновляем информацию о капитале при изменении настроек усреднения
            updateCapitalInfo(usePairsField, maxShortMarginSize, maxLongMarginSize,
                    autoAveragingCheckbox, averagingVolumeMultiplierField, maxAveragingCountField, autoVolumeCheckbox);
        });

        // Логика активации/деактивации полей автообъема
        boolean isAutoVolumeEnabled = currentSettings.isAutoVolumeEnabled();
        maxShortMarginSize.setEnabled(!isAutoVolumeEnabled);
        maxLongMarginSize.setEnabled(!isAutoVolumeEnabled);

        autoVolumeCheckbox.addValueChangeListener(event -> {
            boolean autoVolumeEnabled = event.getValue();

            // Деактивируем/активируем поля размера риска
            maxShortMarginSize.setEnabled(!autoVolumeEnabled);
            maxLongMarginSize.setEnabled(!autoVolumeEnabled);

            // Обновляем значения автообъема
            updateAutoVolumeValues();

            // Обновляем информацию о капитале
            updateCapitalInfo(usePairsField, maxShortMarginSize, maxLongMarginSize,
                    autoAveragingCheckbox, averagingVolumeMultiplierField, maxAveragingCountField, autoVolumeCheckbox);
        });

        // Слушатели для обновления информации о капитале
        usePairsField.addValueChangeListener(e -> updateCapitalInfo(usePairsField, maxShortMarginSize, maxLongMarginSize,
                autoAveragingCheckbox, averagingVolumeMultiplierField, maxAveragingCountField, autoVolumeCheckbox));
        maxShortMarginSize.addValueChangeListener(e -> updateCapitalInfo(usePairsField, maxShortMarginSize, maxLongMarginSize,
                autoAveragingCheckbox, averagingVolumeMultiplierField, maxAveragingCountField, autoVolumeCheckbox));
        maxLongMarginSize.addValueChangeListener(e -> updateCapitalInfo(usePairsField, maxShortMarginSize, maxLongMarginSize,
                autoAveragingCheckbox, averagingVolumeMultiplierField, maxAveragingCountField, autoVolumeCheckbox));
        averagingVolumeMultiplierField.addValueChangeListener(e -> updateCapitalInfo(usePairsField, maxShortMarginSize, maxLongMarginSize,
                autoAveragingCheckbox, averagingVolumeMultiplierField, maxAveragingCountField, autoVolumeCheckbox));
        maxAveragingCountField.addValueChangeListener(e -> updateCapitalInfo(usePairsField, maxShortMarginSize, maxLongMarginSize,
                autoAveragingCheckbox, averagingVolumeMultiplierField, maxAveragingCountField, autoVolumeCheckbox));

        // Создаем спан для отображения информации о капитале
        capitalInfoSpan = new Span();
        capitalInfoSpan.getStyle().set("font-weight", "bold").set("margin-top", "1rem").set("display", "block");

        capitalForm.add(
                usePairsField,
                maxShortMarginSize,
                maxLongMarginSize,
                leverageField,
                autoVolumeCheckbox,
                autoVolumeLongField,
                autoVolumeShortField,
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

    private void updateCapitalInfo(NumberField usePairsField, NumberField maxShortMarginSize, NumberField maxLongMarginSize,
                                   Checkbox autoAveragingCheckbox, NumberField averagingVolumeMultiplierField, NumberField maxAveragingCountField, 
                                   Checkbox autoVolumeCheckbox) {
        try {
            // Берем текущие настройки и обновляем их значениями из UI полей
            Settings tempSettings = Settings.builder()
                    .usePairs(usePairsField.getValue() != null ? usePairsField.getValue() : currentSettings.getUsePairs())
                    .maxShortMarginSize(maxShortMarginSize.getValue() != null ? maxShortMarginSize.getValue() : currentSettings.getMaxShortMarginSize())
                    .maxLongMarginSize(maxLongMarginSize.getValue() != null ? maxLongMarginSize.getValue() : currentSettings.getMaxLongMarginSize())
                    .autoAveragingEnabled(autoAveragingCheckbox.getValue())
                    .averagingVolumeMultiplier(averagingVolumeMultiplierField.getValue() != null ? averagingVolumeMultiplierField.getValue() : currentSettings.getAveragingVolumeMultiplier())
                    .maxAveragingCount(maxAveragingCountField.getValue() != null ? maxAveragingCountField.getValue().intValue() : currentSettings.getMaxAveragingCount())
                    .autoVolumeEnabled(autoVolumeCheckbox.getValue()) // Добавляем состояние автообъема
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

            // Обновляем значения автообъема при изменении настроек
            updateAutoVolumeValues();

        } catch (Exception e) {
            capitalInfoSpan.setText("⚠️ Ошибка расчета капитала: " + e.getMessage());
            capitalInfoSpan.getStyle().set("color", "orange");
        }
    }

    /**
     * Создает секцию управления шедуллерами
     */
    private Details createSchedulerControlSection() {
        FormLayout schedulerForm = createSingleColumnFormLayout();

        // Создаем чекбоксы для управления шедуллерами
        Checkbox updateTradesSchedulerCheckbox = new Checkbox("UpdateTrades");
        Checkbox stablePairsSchedulerCheckbox = new Checkbox("StablePairs");
        Checkbox monitoringPairsUpdateSchedulerCheckbox = new Checkbox("MonitoringPairs Update");
        Checkbox portfolioSnapshotSchedulerCheckbox = new Checkbox("Portfolio Snapshot");
        Checkbox portfolioCleanupSchedulerCheckbox = new Checkbox("Portfolio Cleanup");
        Checkbox candleCacheSyncSchedulerCheckbox = new Checkbox("CandleCache Sync");
        Checkbox candleCacheUpdateSchedulerCheckbox = new Checkbox("CandleCache Update");
        Checkbox candleCacheStatsSchedulerCheckbox = new Checkbox("CandleCache Stats");

        // Инициализируем значения чекбоксов из настроек (с проверкой на null)
        updateTradesSchedulerCheckbox.setValue(currentSettings.getSchedulerUpdateTradesEnabled() != null ? currentSettings.getSchedulerUpdateTradesEnabled() : true);
        stablePairsSchedulerCheckbox.setValue(currentSettings.getSchedulerStablePairsEnabled() != null ? currentSettings.getSchedulerStablePairsEnabled() : true);
        monitoringPairsUpdateSchedulerCheckbox.setValue(currentSettings.getSchedulerMonitoringPairsUpdateEnabled() != null ? currentSettings.getSchedulerMonitoringPairsUpdateEnabled() : true);
        portfolioSnapshotSchedulerCheckbox.setValue(currentSettings.getSchedulerPortfolioSnapshotEnabled() != null ? currentSettings.getSchedulerPortfolioSnapshotEnabled() : true);
        portfolioCleanupSchedulerCheckbox.setValue(currentSettings.getSchedulerPortfolioCleanupEnabled() != null ? currentSettings.getSchedulerPortfolioCleanupEnabled() : true);
        candleCacheSyncSchedulerCheckbox.setValue(currentSettings.getSchedulerCandleCacheSyncEnabled() != null ? currentSettings.getSchedulerCandleCacheSyncEnabled() : true);
        candleCacheUpdateSchedulerCheckbox.setValue(currentSettings.getSchedulerCandleCacheUpdateEnabled() != null ? currentSettings.getSchedulerCandleCacheUpdateEnabled() : true);
        candleCacheStatsSchedulerCheckbox.setValue(currentSettings.getSchedulerCandleCacheStatsEnabled() != null ? currentSettings.getSchedulerCandleCacheStatsEnabled() : true);

        // Создаем вертикальные компоновки для шедуллеров с CRON
        VerticalLayout stablePairsLayout = new VerticalLayout();
        stablePairsLayout.setSpacing(false);
        stablePairsLayout.setPadding(false);
        stablePairsLayout.add(stablePairsSchedulerCheckbox);

        VerticalLayout monitoringPairsUpdateLayout = new VerticalLayout();
        monitoringPairsUpdateLayout.setSpacing(false);
        monitoringPairsUpdateLayout.setPadding(false);
        monitoringPairsUpdateLayout.add(monitoringPairsUpdateSchedulerCheckbox);

        VerticalLayout portfolioCleanupLayout = new VerticalLayout();
        portfolioCleanupLayout.setSpacing(false);
        portfolioCleanupLayout.setPadding(false);
        portfolioCleanupLayout.add(portfolioCleanupSchedulerCheckbox);

        // Добавляем компоненты в форму
        schedulerForm.add(
                updateTradesSchedulerCheckbox,
                stablePairsLayout,
                monitoringPairsUpdateLayout,
                portfolioSnapshotSchedulerCheckbox,
                portfolioCleanupLayout,
                candleCacheSyncSchedulerCheckbox,
                candleCacheUpdateSchedulerCheckbox,
                candleCacheStatsSchedulerCheckbox
        );

        // Привязываем чекбоксы к настройкам
        bindSchedulerControlFields(
                updateTradesSchedulerCheckbox,
                stablePairsSchedulerCheckbox,
                monitoringPairsUpdateSchedulerCheckbox,
                portfolioSnapshotSchedulerCheckbox,
                portfolioCleanupSchedulerCheckbox,
                candleCacheSyncSchedulerCheckbox,
                candleCacheUpdateSchedulerCheckbox,
                candleCacheStatsSchedulerCheckbox
        );

        return createDetailsCard("📅 Управление шедуллерами",
                "Управление автоматическими задачами и их расписанием", schedulerForm);
    }

    /**
     * Привязывает поля управления шедуллерами к настройкам
     */
    private void bindSchedulerControlFields(Checkbox updateTradesSchedulerCheckbox,
                                            Checkbox stablePairsSchedulerCheckbox,
                                            Checkbox monitoringPairsUpdateSchedulerCheckbox,
                                            Checkbox portfolioSnapshotSchedulerCheckbox,
                                            Checkbox portfolioCleanupSchedulerCheckbox,
                                            Checkbox candleCacheSyncSchedulerCheckbox,
                                            Checkbox candleCacheUpdateSchedulerCheckbox,
                                            Checkbox candleCacheStatsSchedulerCheckbox) {

        // Привязываем чекбоксы шедуллеров к настройкам
        settingsBinder.forField(updateTradesSchedulerCheckbox)
                .bind(Settings::getSchedulerUpdateTradesEnabled, Settings::setSchedulerUpdateTradesEnabled);

        settingsBinder.forField(stablePairsSchedulerCheckbox)
                .bind(Settings::getSchedulerStablePairsEnabled, Settings::setSchedulerStablePairsEnabled);

        settingsBinder.forField(monitoringPairsUpdateSchedulerCheckbox)
                .bind(Settings::getSchedulerMonitoringPairsUpdateEnabled, Settings::setSchedulerMonitoringPairsUpdateEnabled);

        settingsBinder.forField(portfolioSnapshotSchedulerCheckbox)
                .bind(Settings::getSchedulerPortfolioSnapshotEnabled, Settings::setSchedulerPortfolioSnapshotEnabled);

        settingsBinder.forField(portfolioCleanupSchedulerCheckbox)
                .bind(Settings::getSchedulerPortfolioCleanupEnabled, Settings::setSchedulerPortfolioCleanupEnabled);

        settingsBinder.forField(candleCacheSyncSchedulerCheckbox)
                .bind(Settings::getSchedulerCandleCacheSyncEnabled, Settings::setSchedulerCandleCacheSyncEnabled);

        settingsBinder.forField(candleCacheUpdateSchedulerCheckbox)
                .bind(Settings::getSchedulerCandleCacheUpdateEnabled, Settings::setSchedulerCandleCacheUpdateEnabled);

        settingsBinder.forField(candleCacheStatsSchedulerCheckbox)
                .bind(Settings::getSchedulerCandleCacheStatsEnabled, Settings::setSchedulerCandleCacheStatsEnabled);

        // Добавляем логирование изменений
        updateTradesSchedulerCheckbox.addValueChangeListener(event ->
                log.info("📅 UpdateTradesScheduler {}", event.getValue() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));

        stablePairsSchedulerCheckbox.addValueChangeListener(event ->
                log.info("📅 StablePairsScheduler {}", event.getValue() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));

        monitoringPairsUpdateSchedulerCheckbox.addValueChangeListener(event ->
                log.info("📅 MonitoringPairsUpdateScheduler {}", event.getValue() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));

        portfolioSnapshotSchedulerCheckbox.addValueChangeListener(event ->
                log.info("📅 PortfolioSnapshotScheduler {}", event.getValue() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));

        portfolioCleanupSchedulerCheckbox.addValueChangeListener(event ->
                log.info("📅 PortfolioCleanupScheduler {}", event.getValue() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));

        candleCacheSyncSchedulerCheckbox.addValueChangeListener(event ->
                log.info("📅 CandleCacheSyncScheduler {}", event.getValue() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));

        candleCacheUpdateSchedulerCheckbox.addValueChangeListener(event ->
                log.info("📅 CandleCacheUpdateScheduler {}", event.getValue() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));

        candleCacheStatsSchedulerCheckbox.addValueChangeListener(event ->
                log.info("📅 CandleCacheStatsScheduler {}", event.getValue() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));
    }

    /**
     * Слушатель события обновления глобальных настроек.
     * Обновляет выпадающие списки в секции "Анализ и фильтры" согласно новым глобальным настройкам.
     */
    @EventListener
    public void handleGlobalSettingsUpdated(GlobalSettingsUpdatedEvent event) {
        try {
            log.debug("🔧 SettingsComponent: Получено событие обновления глобальных настроек");
            log.debug("📊 Новые таймфреймы: {}", event.getUpdatedGlobalTimeframes());
            log.debug("📅 Новые периоды: {}", event.getUpdatedGlobalPeriods());

            getUI().ifPresent(ui -> ui.access(() -> {
                try {
                    // Обновляем активные списки из новых глобальных настроек
                    List<String> newActiveTimeframes = timeframeAndPeriodService.getActiveTimeframes(
                            event.getUpdatedGlobalTimeframes());
                    List<String> newActivePeriods = timeframeAndPeriodService.getActivePeriods(
                            event.getUpdatedGlobalPeriods());

                    // Обновляем выпадающий список таймфреймов в секции "Анализ и фильтры"
                    if (analysisTimeframeSelect != null) {
                        String currentTimeframeValue = analysisTimeframeSelect.getValue();
                        analysisTimeframeSelect.setItems(newActiveTimeframes);

                        // Восстанавливаем выбранное значение если оно все еще доступно
                        if (currentTimeframeValue != null && newActiveTimeframes.contains(currentTimeframeValue)) {
                            analysisTimeframeSelect.setValue(currentTimeframeValue);
                        } else if (!newActiveTimeframes.isEmpty()) {
                            // Выбираем первый доступный таймфрейм
                            analysisTimeframeSelect.setValue(newActiveTimeframes.get(0));
                            log.debug("🔄 Таймфрейм в секции 'Анализ и фильтры' изменен: {} -> {}",
                                    currentTimeframeValue, newActiveTimeframes.get(0));
                        }
                    }

                    // Обновляем выпадающий список периодов в секции "Анализ и фильтры"
                    if (analysisPeriodSelect != null) {
                        String currentPeriodValue = analysisPeriodSelect.getValue();
                        analysisPeriodSelect.setItems(newActivePeriods);

                        // Восстанавливаем выбранное значение если оно все еще доступно
                        if (currentPeriodValue != null && newActivePeriods.contains(currentPeriodValue)) {
                            analysisPeriodSelect.setValue(currentPeriodValue);
                        } else if (!newActivePeriods.isEmpty()) {
                            // Выбираем первый доступный период
                            analysisPeriodSelect.setValue(newActivePeriods.get(0));
                            log.debug("🔄 Период в секции 'Анализ и фильтры' изменен: {} -> {}",
                                    currentPeriodValue, newActivePeriods.get(0));
                        }
                    }

                    log.debug("✅ SettingsComponent: Выпадающие списки в секции 'Анализ и фильтры' обновлены");

                } catch (Exception e) {
                    log.error("❌ Ошибка при обновлении UI SettingsComponent после изменения глобальных настроек: {}", e.getMessage(), e);
                    Notification.show("❌ Ошибка обновления секции 'Анализ и фильтры': " + e.getMessage(),
                                    3000, Notification.Position.TOP_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }));

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке события обновления глобальных настроек в SettingsComponent: {}", e.getMessage(), e);
        }
    }

}