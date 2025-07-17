package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.schedulers.TradeAndSimulationScheduler;
import com.example.statarbitrage.core.services.SettingsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
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

import java.util.concurrent.CompletableFuture;

@Slf4j
@SpringComponent
@UIScope
public class SettingsComponent extends VerticalLayout {

    private final SettingsService settingsService;
    private final TradeAndSimulationScheduler tradeAndSimulationScheduler;
    private final Binder<Settings> settingsBinder;

    private Settings currentSettings;
    private Checkbox autoTradingCheckbox;
    private Runnable autoTradingChangeCallback;

    public SettingsComponent(SettingsService settingsService,
                             TradeAndSimulationScheduler tradeAndSimulationScheduler) {
        this.settingsService = settingsService;
        this.tradeAndSimulationScheduler = tradeAndSimulationScheduler;
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
            log.error("Error loading settings", e);
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

                if (event.getValue()) {
                    log.info("🚀 UI: Запускаем maintainPairs() асинхронно");
                    // Запускаем maintainPairs() асинхронно, чтобы не блокировать UI
                    CompletableFuture.runAsync(() -> {
                        try {
                            tradeAndSimulationScheduler.maintainPairs();
                        } catch (Exception e) {
                            log.error("❌ Ошибка при асинхронном запуске maintainPairs()", e);
                        }
                    });
                }

                // Уведомляем об изменении состояния автотрейдинга
                if (autoTradingChangeCallback != null) {
                    log.info("🔄 SettingsComponent: Вызываем autoTradingChangeCallback для autoTrading={}", event.getValue());
                    autoTradingChangeCallback.run();
                } else {
                    log.warn("⚠️ SettingsComponent: autoTradingChangeCallback не установлен!");
                }
            } catch (Exception e) {
                log.error("Error updating autoTrading mode", e);
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
        NumberField minAdfValueField = new NumberField("Min adfValue");
        NumberField minCorrelationField = new NumberField("Min corr");
        NumberField minVolumeField = new NumberField("Min Vol (млн $)");
        NumberField checkIntervalField = new NumberField("Обновление (мин)");

        // Create filter checkboxes
        Checkbox useMinZFilterCheckbox = new Checkbox("Использовать Min Z фильтр");
        Checkbox useMinRSquaredFilterCheckbox = new Checkbox("Использовать Min R-Squared фильтр");
        Checkbox useMinPValueFilterCheckbox = new Checkbox("Использовать Min pValue фильтр");
        Checkbox useMinAdfValueFilterCheckbox = new Checkbox("Использовать Min adfValue фильтр");
        Checkbox useMinCorrelationFilterCheckbox = new Checkbox("Использовать Min Correlation фильтр");
        Checkbox useMinVolumeFilterCheckbox = new Checkbox("Использовать Min Volume фильтр");

        NumberField maxShortMarginSize = new NumberField("Размер риска шорт ($)");
        NumberField maxLongMarginSize = new NumberField("Размер риска лонг ($)");
        NumberField capitalShortField = new NumberField("Позиция шорт ($)");
        NumberField leverageField = new NumberField("Плечо");

        NumberField exitTakeField = new NumberField("Exit Тейк (%)");
        NumberField exitStopField = new NumberField("Exit Стоп (%)");
        NumberField exitZMinField = new NumberField("Exit Мин Z");
        NumberField exitZMaxField = new NumberField("Exit Макс Z");
        NumberField exitZMaxPercentField = new NumberField("Exit Макс Z (%)");
        NumberField exitTimeHoursField = new NumberField("Exit Таймаут (ч)");

        // Create exit strategy checkboxes
        Checkbox useExitTakeCheckbox = new Checkbox("Использовать Exit Тейк");
        Checkbox useExitStopCheckbox = new Checkbox("Использовать Exit Стоп");
        Checkbox useExitZMinCheckbox = new Checkbox("Использовать Exit Мин Z");
        Checkbox useExitZMaxCheckbox = new Checkbox("Использовать Exit Макс Z");
        Checkbox useExitZMaxPercentCheckbox = new Checkbox("Использовать Exit Макс Z (%)");
        Checkbox useExitTimeHoursCheckbox = new Checkbox("Использовать Exit Таймаут");

        NumberField usePairsField = new NumberField("Кол-во пар");

        // Set step and min values for number fields
        setNumberFieldProperties(candleLimitField, 1, 1);
        setNumberFieldProperties(minZField, 0.1, 0.0);
        setNumberFieldProperties(minRSquaredField, 0.1, 0.5);
        setNumberFieldProperties(minWindowSizeField, 1, 1);
        setNumberFieldProperties(minPValueField, 0.001, 0.0);
        setNumberFieldProperties(minAdfValueField, 0.001, 0.0);
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
        setNumberFieldProperties(exitTimeHoursField, 1, 1);
        setNumberFieldProperties(usePairsField, 1, 1);

        // Create sections
        add(createAnalysisSection(timeframeField, candleLimitField, minZField, minRSquaredField, minWindowSizeField,
                minPValueField, minAdfValueField, minCorrelationField, minVolumeField,
                checkIntervalField, usePairsField, useMinZFilterCheckbox, useMinRSquaredFilterCheckbox,
                useMinPValueFilterCheckbox, useMinAdfValueFilterCheckbox, useMinCorrelationFilterCheckbox,
                useMinVolumeFilterCheckbox));

        add(createCapitalSection(
                maxShortMarginSize,
                maxLongMarginSize,
                leverageField
        ));

        add(createExitStrategySection(exitTakeField, exitStopField, exitZMinField, exitZMaxField,
                exitZMaxPercentField, exitTimeHoursField, useExitTakeCheckbox, useExitStopCheckbox,
                useExitZMinCheckbox, useExitZMaxCheckbox, useExitZMaxPercentCheckbox, useExitTimeHoursCheckbox));

        // Bind fields to settings object
        bindFields(timeframeField, candleLimitField, minZField, minRSquaredField, minWindowSizeField,
                minPValueField, minAdfValueField, checkIntervalField, minCorrelationField,
                minVolumeField, usePairsField,
                maxShortMarginSize,
                maxLongMarginSize,
                leverageField,
                exitTakeField, exitStopField,
                exitZMinField, exitZMaxField, exitZMaxPercentField, exitTimeHoursField,
                useMinZFilterCheckbox, useMinRSquaredFilterCheckbox, useMinPValueFilterCheckbox,
                useMinAdfValueFilterCheckbox, useMinCorrelationFilterCheckbox, useMinVolumeFilterCheckbox,
                useExitTakeCheckbox, useExitStopCheckbox, useExitZMinCheckbox, useExitZMaxCheckbox,
                useExitZMaxPercentCheckbox, useExitTimeHoursCheckbox);

        settingsBinder.readBean(currentSettings);
    }

    private Details createAnalysisSection(TextField timeframeField, NumberField candleLimitField,
                                          NumberField minZField, NumberField minRSquaredField,
                                          NumberField minWindowSizeField, NumberField minPValueField,
                                          NumberField minAdfValueField, NumberField minCorrelationField,
                                          NumberField minVolumeField, NumberField checkIntervalField,
                                          NumberField usePairsField, Checkbox useMinZFilterCheckbox,
                                          Checkbox useMinRSquaredFilterCheckbox, Checkbox useMinPValueFilterCheckbox,
                                          Checkbox useMinAdfValueFilterCheckbox, Checkbox useMinCorrelationFilterCheckbox,
                                          Checkbox useMinVolumeFilterCheckbox) {

        FormLayout analysisForm = createFormLayout();

        // Создаем компоненты фильтров с чекбоксами
        HorizontalLayout minZLayout = createFilterLayout(useMinZFilterCheckbox, minZField);
        HorizontalLayout minRSquaredLayout = createFilterLayout(useMinRSquaredFilterCheckbox, minRSquaredField);
        HorizontalLayout minPValueLayout = createFilterLayout(useMinPValueFilterCheckbox, minPValueField);
        HorizontalLayout minAdfValueLayout = createFilterLayout(useMinAdfValueFilterCheckbox, minAdfValueField);
        HorizontalLayout minCorrelationLayout = createFilterLayout(useMinCorrelationFilterCheckbox, minCorrelationField);
        HorizontalLayout minVolumeLayout = createFilterLayout(useMinVolumeFilterCheckbox, minVolumeField);

        analysisForm.add(
                timeframeField, candleLimitField, checkIntervalField,
                minZLayout, minRSquaredLayout, minWindowSizeField, minPValueLayout,
                minAdfValueLayout, minCorrelationLayout, minVolumeLayout, usePairsField
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

    private Details createExitStrategySection(NumberField exitTakeField, NumberField exitStopField,
                                              NumberField exitZMinField, NumberField exitZMaxField, NumberField exitZMaxPercentField,
                                              NumberField exitTimeHoursField, Checkbox useExitTakeCheckbox,
                                              Checkbox useExitStopCheckbox, Checkbox useExitZMinCheckbox,
                                              Checkbox useExitZMaxCheckbox, Checkbox useExitZMaxPercentCheckbox,
                                              Checkbox useExitTimeHoursCheckbox) {

        FormLayout exitForm = createFormLayout();

        // Создаем компоненты стратегий выхода с чекбоксами
        HorizontalLayout exitTakeLayout = createFilterLayout(useExitTakeCheckbox, exitTakeField);
        HorizontalLayout exitStopLayout = createFilterLayout(useExitStopCheckbox, exitStopField);
        HorizontalLayout exitZMinLayout = createFilterLayout(useExitZMinCheckbox, exitZMinField);
        HorizontalLayout exitZMaxLayout = createFilterLayout(useExitZMaxCheckbox, exitZMaxField);
        HorizontalLayout exitZMaxPercentLayout = createFilterLayout(useExitZMaxPercentCheckbox, exitZMaxPercentField);
        HorizontalLayout exitTimeHoursLayout = createFilterLayout(useExitTimeHoursCheckbox, exitTimeHoursField);

        exitForm.add(
                exitTakeLayout, exitStopLayout,
                exitZMinLayout, exitZMaxLayout,
                exitZMaxPercentLayout, exitTimeHoursLayout
        );

        return createDetailsCard("🚪 Стратегии выхода",
                "Условия закрытия позиций и управления рисками", exitForm);
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

    private void bindFields(TextField timeframeField, NumberField candleLimitField,
                            NumberField minZField, NumberField minRSquaredField, NumberField minWindowSizeField,
                            NumberField minPValueField, NumberField minAdfValueField,
                            NumberField checkIntervalField, NumberField minCorrelationField,
                            NumberField minVolumeField, NumberField usePairsField,
                            NumberField maxShortMarginSizeField,
                            NumberField maxLongMarginSizeField,
                            NumberField leverageField,
                            NumberField exitTakeField, NumberField exitStopField,
                            NumberField exitZMinField, NumberField exitZMaxField,
                            NumberField exitZMaxPercentField, NumberField exitTimeHoursField,
                            Checkbox useMinZFilterCheckbox, Checkbox useMinRSquaredFilterCheckbox,
                            Checkbox useMinPValueFilterCheckbox, Checkbox useMinAdfValueFilterCheckbox,
                            Checkbox useMinCorrelationFilterCheckbox, Checkbox useMinVolumeFilterCheckbox,
                            Checkbox useExitTakeCheckbox, Checkbox useExitStopCheckbox,
                            Checkbox useExitZMinCheckbox, Checkbox useExitZMaxCheckbox,
                            Checkbox useExitZMaxPercentCheckbox, Checkbox useExitTimeHoursCheckbox) {

        settingsBinder.forField(timeframeField)
                .withValidator(new StringLengthValidator("Таймфрейм не может быть пустым", 1, null))
                .bind(Settings::getTimeframe, Settings::setTimeframe);

        settingsBinder.forField(candleLimitField)
                .withValidator(new DoubleRangeValidator("Количество свечей должно быть больше 0", 1.0, Double.MAX_VALUE))
                .bind(Settings::getCandleLimit, Settings::setCandleLimit);

        settingsBinder.forField(minZField).bind(Settings::getMinZ, Settings::setMinZ);
        settingsBinder.forField(minRSquaredField).bind(Settings::getMinRSquared, Settings::setMinRSquared);
        settingsBinder.forField(minWindowSizeField).bind(Settings::getMinWindowSize, Settings::setMinWindowSize);
        settingsBinder.forField(minPValueField).bind(Settings::getMinPValue, Settings::setMinPValue);
        settingsBinder.forField(minAdfValueField).bind(Settings::getMinAdfValue, Settings::setMinAdfValue);
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
        settingsBinder.forField(exitTimeHoursField).bind(Settings::getExitTimeHours, Settings::setExitTimeHours);
        settingsBinder.forField(minCorrelationField).bind(Settings::getMinCorrelation, Settings::setMinCorrelation);
        settingsBinder.forField(minVolumeField).bind(Settings::getMinVolume, Settings::setMinVolume);
        settingsBinder.forField(usePairsField).bind(Settings::getUsePairs, Settings::setUsePairs);

        // Bind filter checkboxes
        settingsBinder.forField(useMinZFilterCheckbox).bind(Settings::isUseMinZFilter, Settings::setUseMinZFilter);
        settingsBinder.forField(useMinRSquaredFilterCheckbox).bind(Settings::isUseMinRSquaredFilter, Settings::setUseMinRSquaredFilter);
        settingsBinder.forField(useMinPValueFilterCheckbox).bind(Settings::isUseMinPValueFilter, Settings::setUseMinPValueFilter);
        settingsBinder.forField(useMinAdfValueFilterCheckbox).bind(Settings::isUseMinAdfValueFilter, Settings::setUseMinAdfValueFilter);
        settingsBinder.forField(useMinCorrelationFilterCheckbox).bind(Settings::isUseMinCorrelationFilter, Settings::setUseMinCorrelationFilter);
        settingsBinder.forField(useMinVolumeFilterCheckbox).bind(Settings::isUseMinVolumeFilter, Settings::setUseMinVolumeFilter);

        // Bind exit strategy checkboxes
        settingsBinder.forField(useExitTakeCheckbox).bind(Settings::isUseExitTake, Settings::setUseExitTake);
        settingsBinder.forField(useExitStopCheckbox).bind(Settings::isUseExitStop, Settings::setUseExitStop);
        settingsBinder.forField(useExitZMinCheckbox).bind(Settings::isUseExitZMin, Settings::setUseExitZMin);
        settingsBinder.forField(useExitZMaxCheckbox).bind(Settings::isUseExitZMax, Settings::setUseExitZMax);
        settingsBinder.forField(useExitZMaxPercentCheckbox).bind(Settings::isUseExitZMaxPercent, Settings::setUseExitZMaxPercent);
        settingsBinder.forField(useExitTimeHoursCheckbox).bind(Settings::isUseExitTimeHours, Settings::setUseExitTimeHours);
    }

    private void setupValidation() {
        settingsBinder.setStatusLabel(null);
    }

    private void saveSettings() {
        try {
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
            log.warn("Validation failed when saving settings: {}", errorMessage);
        } catch (Exception e) {
            String errorMessage = "Ошибка сохранения настроек: " + e.getMessage();
            Notification.show(errorMessage);
            log.error("Error saving settings", e);
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
}