package com.example.statarbitrage.ui.views;

import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.schedulers.TradeAndSimulationScheduler;
import com.example.statarbitrage.core.services.SettingsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.DoubleRangeValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringComponent
@UIScope
public class SettingsComponent extends VerticalLayout {

    private final SettingsService settingsService;
    private final TradeAndSimulationScheduler tradeAndSimulationScheduler;
    private final Binder<Settings> settingsBinder;

    private Settings currentSettings;
    private Checkbox simulationCheckbox;
    private FormLayout settingsForm;

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

    private void initializeComponent() {
        setSpacing(true);
        setPadding(false);

        H2 title = new H2("Настройки торговли");
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
        createSimulationCheckbox();
        createSettingsFormLayout();

        Button saveButton = new Button("Сохранить настройки", e -> saveSettings());
        saveButton.getStyle().set("margin-top", "10px");

        add(simulationCheckbox, settingsForm, saveButton);
    }

    private void createSimulationCheckbox() {
        simulationCheckbox = new Checkbox("Симуляция");
        simulationCheckbox.setValue(currentSettings.isSimulationEnabled());

        simulationCheckbox.addValueChangeListener(event -> {
            try {
                Settings settings = settingsService.getSettings();
                settings.setSimulationEnabled(event.getValue());
                settingsService.saveSettingsInDb(settings);

                if (event.getValue()) {
                    tradeAndSimulationScheduler.maintainPairs();
                }

                log.info(event.getValue() ? "Симуляция включена" : "Симуляция отключена");
                Notification.show(event.getValue() ? "Симуляция включена" : "Симуляция отключена");
            } catch (Exception e) {
                log.error("Error updating simulation mode", e);
                Notification.show("Ошибка при изменении режима симуляции: " + e.getMessage());
            }
        });
    }

    private void createSettingsFormLayout() {
        settingsForm = new FormLayout();
        settingsForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2),
                new FormLayout.ResponsiveStep("800px", 4),
                new FormLayout.ResponsiveStep("1100px", 6),
                new FormLayout.ResponsiveStep("1400px", 8),
                new FormLayout.ResponsiveStep("1700px", 10)
        );

        // Create form fields
        TextField timeframeField = new TextField("Таймфрейм");
        NumberField candleLimitField = new NumberField("Свечей (шт)");
        NumberField minZField = new NumberField("Min Z");
        NumberField minWindowSizeField = new NumberField("Min windowSize");
        NumberField minPValueField = new NumberField("Min pValue");
        NumberField minAdfValueField = new NumberField("Min adfValue");
        NumberField minCorrelationField = new NumberField("Min corr");
        NumberField minVolumeField = new NumberField("Min Vol (млн $)");
        NumberField checkIntervalField = new NumberField("Обновление (мин)");

        NumberField capitalLongField = new NumberField("Depo лонг ($)");
        NumberField capitalShortField = new NumberField("Depo шорт ($)");
        NumberField leverageField = new NumberField("Depo Leverage");
        NumberField feePctPerTradeField = new NumberField("Depo Комиссия (%)");

        NumberField exitTakeField = new NumberField("Exit Тейк (%)");
        NumberField exitStopField = new NumberField("Exit Стоп (%)");
        NumberField exitZMinField = new NumberField("Exit Мин Z");
        NumberField exitZMaxPercentField = new NumberField("Exit Макс Z (%)");
        NumberField exitTimeHoursField = new NumberField("Exit Таймаут (ч)");

        NumberField usePairsField = new NumberField("Кол-во пар");

        // Set step and min values for number fields
        setNumberFieldProperties(candleLimitField, 1.0, 1.0);
        setNumberFieldProperties(minZField, 0.01, 0.0);
        setNumberFieldProperties(minWindowSizeField, 1.0, 1.0);
        setNumberFieldProperties(minPValueField, 0.01, 0.0);
        setNumberFieldProperties(minAdfValueField, 0.01, 0.0);
        setNumberFieldProperties(minCorrelationField, 0.01, -1.0);
        setNumberFieldProperties(minVolumeField, 0.1, 0.0);
        setNumberFieldProperties(checkIntervalField, 1.0, 1.0);
        setNumberFieldProperties(capitalLongField, 1.0, 0.0);
        setNumberFieldProperties(capitalShortField, 1.0, 0.0);
        setNumberFieldProperties(leverageField, 0.1, 0.1);
        setNumberFieldProperties(feePctPerTradeField, 0.01, 0.0);
        setNumberFieldProperties(exitTakeField, 0.01, 0.0);
        setNumberFieldProperties(exitStopField, 0.01, 0.0);
        setNumberFieldProperties(exitZMinField, 0.01, 0.0);
        setNumberFieldProperties(exitZMaxPercentField, 0.01, 0.0);
        setNumberFieldProperties(exitTimeHoursField, 0.1, 0.1);
        setNumberFieldProperties(usePairsField, 1.0, 1.0);

        // Add fields to form
        settingsForm.add(
                timeframeField, candleLimitField,
                minZField, minWindowSizeField, minPValueField, minAdfValueField,
                checkIntervalField, minCorrelationField, minVolumeField, usePairsField,
                capitalLongField, capitalShortField, leverageField, feePctPerTradeField,
                exitTakeField, exitStopField, exitZMinField, exitZMaxPercentField, exitTimeHoursField
        );

        // Bind fields to settings object
        bindFields(timeframeField, candleLimitField, minZField, minWindowSizeField,
                minPValueField, minAdfValueField, checkIntervalField, minCorrelationField,
                minVolumeField, usePairsField, capitalLongField, capitalShortField,
                leverageField, feePctPerTradeField, exitTakeField, exitStopField,
                exitZMinField, exitZMaxPercentField, exitTimeHoursField);

        settingsBinder.readBean(currentSettings);
    }

    private void setNumberFieldProperties(NumberField field, double step, double min) {
        field.setStep(step);
        field.setMin(min);
        field.setStepButtonsVisible(true);
    }

    private void bindFields(TextField timeframeField, NumberField candleLimitField,
                            NumberField minZField, NumberField minWindowSizeField,
                            NumberField minPValueField, NumberField minAdfValueField,
                            NumberField checkIntervalField, NumberField minCorrelationField,
                            NumberField minVolumeField, NumberField usePairsField,
                            NumberField capitalLongField, NumberField capitalShortField,
                            NumberField leverageField, NumberField feePctPerTradeField,
                            NumberField exitTakeField, NumberField exitStopField,
                            NumberField exitZMinField, NumberField exitZMaxPercentField,
                            NumberField exitTimeHoursField) {

        settingsBinder.forField(timeframeField)
                .withValidator(new StringLengthValidator("Таймфрейм не может быть пустым", 1, null))
                .bind(Settings::getTimeframe, Settings::setTimeframe);

        settingsBinder.forField(candleLimitField)
                .withValidator(new DoubleRangeValidator("Количество свечей должно быть больше 0", 1.0, Double.MAX_VALUE))
                .bind(Settings::getCandleLimit, Settings::setCandleLimit);

        settingsBinder.forField(minZField).bind(Settings::getMinZ, Settings::setMinZ);
        settingsBinder.forField(minWindowSizeField).bind(Settings::getMinWindowSize, Settings::setMinWindowSize);
        settingsBinder.forField(minPValueField).bind(Settings::getMinPvalue, Settings::setMinPvalue);
        settingsBinder.forField(minAdfValueField).bind(Settings::getMinAdfValue, Settings::setMinAdfValue);
        settingsBinder.forField(checkIntervalField).bind(Settings::getCheckInterval, Settings::setCheckInterval);
        settingsBinder.forField(capitalLongField).bind(Settings::getCapitalLong, Settings::setCapitalLong);
        settingsBinder.forField(capitalShortField).bind(Settings::getCapitalShort, Settings::setCapitalShort);

        settingsBinder.forField(leverageField)
                .withValidator(new DoubleRangeValidator("Плечо должно быть больше 0", 0.1, Double.MAX_VALUE))
                .bind(Settings::getLeverage, Settings::setLeverage);

        settingsBinder.forField(feePctPerTradeField).bind(Settings::getFeePctPerTrade, Settings::setFeePctPerTrade);
        settingsBinder.forField(exitTakeField).bind(Settings::getExitTake, Settings::setExitTake);
        settingsBinder.forField(exitStopField).bind(Settings::getExitStop, Settings::setExitStop);
        settingsBinder.forField(exitZMinField).bind(Settings::getExitZMin, Settings::setExitZMin);
        settingsBinder.forField(exitZMaxPercentField).bind(Settings::getExitZMaxPercent, Settings::setExitZMaxPercent);
        settingsBinder.forField(exitTimeHoursField).bind(Settings::getExitTimeHours, Settings::setExitTimeHours);
        settingsBinder.forField(minCorrelationField).bind(Settings::getMinCorrelation, Settings::setMinCorrelation);
        settingsBinder.forField(minVolumeField).bind(Settings::getMinVolume, Settings::setMinVolume);
        settingsBinder.forField(usePairsField).bind(Settings::getUsePairs, Settings::setUsePairs);
    }

    private void setupValidation() {
        settingsBinder.setStatusLabel(null);
    }

    private void saveSettings() {
        try {
            settingsBinder.writeBean(currentSettings);
            settingsService.saveSettingsInDb(currentSettings);
            Notification.show("Настройки сохранены успешно");
            log.info("Settings saved successfully");
        } catch (ValidationException e) {
            String errorMessage = "Проверьте правильность заполнения полей: " +
                    e.getValidationErrors().stream()
                            .map(error -> error.getErrorMessage())
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
        simulationCheckbox.setValue(currentSettings.isSimulationEnabled());
        settingsBinder.readBean(currentSettings);
    }
}