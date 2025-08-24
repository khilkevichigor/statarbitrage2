package com.example.core.core.services;

import com.example.core.common.model.ChartSettings;
import com.example.core.core.repositories.ChartSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChartSettingsService {

    private final ChartSettingsRepository chartSettingsRepository;

    /**
     * Получает настройки чарта по типу
     *
     * @param chartType тип чарта
     * @return настройки чарта (создает новые если не найдены)
     */
    public ChartSettings getChartSettings(String chartType) {
        return chartSettingsRepository.findByChartType(chartType)
                .orElseGet(() -> createDefaultSettings(chartType));
    }

    /**
     * Сохраняет настройки чарта
     *
     * @param settings настройки для сохранения
     * @return сохраненные настройки
     */
    public ChartSettings saveChartSettings(ChartSettings settings) {
        try {
            ChartSettings savedSettings = chartSettingsRepository.save(settings);
            log.debug("💾 Сохранены настройки чарта типа: {}", settings.getChartType());
            return savedSettings;
        } catch (Exception e) {
            log.error("❌ Ошибка сохранения настроек чарта типа: {}", settings.getChartType(), e);
            throw e;
        }
    }

    /**
     * Обновляет конкретную настройку чарта
     *
     * @param chartType   тип чарта
     * @param settingName название настройки
     * @param value       новое значение
     */
    public void updateChartSetting(String chartType, String settingName, boolean value) {
        ChartSettings settings = getChartSettings(chartType);

        switch (settingName.toLowerCase()) {
            case "showzscore":
                settings.setShowZScore(value);
                break;
            case "showcombinedprice":
                settings.setShowCombinedPrice(value);
                break;
            case "showpixelspread":
                settings.setShowPixelSpread(value);
                break;
            case "showema":
                settings.setShowEma(value);
                break;
            case "showstochrsi":
                settings.setShowStochRsi(value);
                break;
            case "showprofit":
                settings.setShowProfit(value);
                break;
            case "showentrypoint":
                settings.setShowEntryPoint(value);
                break;
            default:
                log.warn("⚠️ Неизвестная настройка чарта: {}", settingName);
                return;
        }

        saveChartSettings(settings);
        log.debug("✅ Обновлена настройка {} = {} для чарта типа {}", settingName, value, chartType);
    }

    /**
     * Создает настройки чарта по умолчанию
     *
     * @param chartType тип чарта
     * @return новые настройки с значениями по умолчанию
     */
    private ChartSettings createDefaultSettings(String chartType) {
        ChartSettings defaultSettings = ChartSettings.builder()
                .chartType(chartType)
                .showZScore(true)
                .showCombinedPrice(true)
                .showPixelSpread(false)
                .showEma(false)
                .showStochRsi(false)
                .showProfit(false)
                .showEntryPoint(true)
                .build();

        log.debug("🆕 Созданы настройки по умолчанию для чарта типа: {}", chartType);
        return saveChartSettings(defaultSettings);
    }
}