package com.example.core.services;

import com.example.core.repositories.SettingsRepository;
import com.example.core.repositories.StablePairsScreenerSettingsRepository;
import com.example.shared.models.Settings;
import com.example.shared.models.StablePairsScreenerSettings;
import com.example.shared.services.TimeframeAndPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы с настройками скриннера стабильных пар
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StablePairsScreenerSettingsService {

    private final StablePairsScreenerSettingsRepository repository;
    private final SettingsRepository settingsRepository;
    private final TimeframeAndPeriodService timeframeAndPeriodService;

    /**
     * Получить все настройки, отсортированные по использованию
     */
    public List<StablePairsScreenerSettings> getAllSettings() {
        log.debug("📋 Получение всех настроек скриннера");
        return repository.findAllOrderedByUsage();
    }

    /**
     * Получить настройки по ID
     */
    public Optional<StablePairsScreenerSettings> getSettingsById(Long id) {
        log.debug("🔍 Поиск настроек по ID: {}", id);
        return repository.findById(id);
    }

    /**
     * Получить настройки по умолчанию (создать если не существуют)
     */
    @Transactional
    public StablePairsScreenerSettings getDefaultSettings() {
        log.debug("⚙️ Получение настроек по умолчанию");
        
        Optional<StablePairsScreenerSettings> defaultSettings = repository.findByIsDefaultTrue();
        
        if (defaultSettings.isPresent()) {
            log.debug("✅ Найдены существующие настройки по умолчанию: {}", defaultSettings.get().getName());
            return defaultSettings.get();
        }
        
        // Создаем настройки по умолчанию
        log.info("🆕 Создание новых настроек скриннера по умолчанию");
        StablePairsScreenerSettings newDefault = StablePairsScreenerSettings.createDefault();
        StablePairsScreenerSettings saved = repository.save(newDefault);
        
        log.info("✅ Настройки по умолчанию созданы с ID: {}", saved.getId());
        return saved;
    }

    /**
     * Сохранить или обновить настройки
     */
    @Transactional
    public StablePairsScreenerSettings saveSettings(StablePairsScreenerSettings settings) {
        log.info("💾 Сохранение настроек: {}", settings.getName());
        
        // Валидация
        validateSettings(settings);
        
        // Если устанавливается флаг "по умолчанию", сбрасываем его у остальных
        if (settings.isDefault()) {
            log.debug("🔄 Сброс флага 'по умолчанию' у других настроек");
            repository.resetAllDefaultFlags();
        }
        
        StablePairsScreenerSettings saved = repository.save(settings);
        log.info("✅ Настройки сохранены с ID: {}", saved.getId());
        return saved;
    }

    /**
     * Удалить настройки
     */
    @Transactional
    public void deleteSettings(Long id) {
        log.info("🗑️ Удаление настроек с ID: {}", id);
        
        Optional<StablePairsScreenerSettings> settings = repository.findById(id);
        if (settings.isEmpty()) {
            log.warn("⚠️ Настройки с ID {} не найдены", id);
            throw new IllegalArgumentException("Настройки не найдены: " + id);
        }
        
        if (settings.get().isDefault()) {
            log.error("❌ Попытка удаления настроек по умолчанию");
            throw new IllegalStateException("Нельзя удалить настройки по умолчанию");
        }
        
        repository.deleteById(id);
        log.info("✅ Настройки с ID {} удалены", id);
    }

    /**
     * Получить настройки для автоматического запуска с применением глобальных ограничений таймфреймов и периодов
     */
    public List<StablePairsScreenerSettings> getScheduledSettings() {
        log.debug("⏰ Получение настроек для автоматического запуска");
        
        try {
            // Получаем все настройки с включенным автоматическим запуском
            List<StablePairsScreenerSettings> originalSettings = repository.findByRunOnScheduleTrue();
            
            if (originalSettings.isEmpty()) {
                log.debug("📋 Нет настроек с включенным автоматическим запуском");
                return originalSettings;
            }
            
            // Получаем глобальные активные таймфреймы и периоды
            Settings globalSettings = settingsRepository.findAll().stream().findFirst()
                    .orElse(new Settings());
            List<String> allowedTimeframes = timeframeAndPeriodService.getActiveTimeframes(
                    globalSettings.getGlobalActiveTimeframes());
            List<String> allowedPeriods = timeframeAndPeriodService.getActivePeriods(
                    globalSettings.getGlobalActivePeriods());
            
            log.info("🌐 Глобальные ограничения для шедуллера:");
            log.info("📊 Разрешенные таймфреймы: {}", allowedTimeframes);
            log.info("📅 Разрешенные периоды: {}", allowedPeriods);
            
            // Фильтруем настройки и ограничиваем их глобальными настройками
            List<StablePairsScreenerSettings> filteredSettings = new ArrayList<>();
            
            for (StablePairsScreenerSettings setting : originalSettings) {
                try {
                    // Пересекаем таймфреймы настройки с глобально разрешенными
                    Set<String> settingTimeframes = setting.getSelectedTimeframesSet();
                    Set<String> validTimeframes = settingTimeframes.stream()
                            .filter(allowedTimeframes::contains)
                            .collect(Collectors.toSet());
                    
                    // Пересекаем периоды настройки с глобально разрешенными
                    Set<String> settingPeriods = setting.getSelectedPeriodsSet();
                    Set<String> validPeriods = settingPeriods.stream()
                            .filter(allowedPeriods::contains)
                            .collect(Collectors.toSet());
                    
                    // Если после фильтрации остались валидные таймфреймы и периоды
                    if (!validTimeframes.isEmpty() && !validPeriods.isEmpty()) {
                        // Создаем копию настройки с ограниченными таймфреймами и периодами
                        StablePairsScreenerSettings filteredSetting = createFilteredCopy(setting, 
                                validTimeframes, validPeriods);
                        
                        filteredSettings.add(filteredSetting);
                        
                        log.info("✅ Настройки '{}': {} тф → {}, {} периодов → {}",
                                setting.getName(),
                                settingTimeframes.size(), validTimeframes.size(),
                                settingPeriods.size(), validPeriods.size());
                        
                        if (!settingTimeframes.equals(validTimeframes)) {
                            log.info("🔄 Исключенные таймфреймы: {}", 
                                    settingTimeframes.stream()
                                            .filter(tf -> !validTimeframes.contains(tf))
                                            .collect(Collectors.toSet()));
                        }
                        
                        if (!settingPeriods.equals(validPeriods)) {
                            log.info("🔄 Исключенные периоды: {}", 
                                    settingPeriods.stream()
                                            .filter(p -> !validPeriods.contains(p))
                                            .collect(Collectors.toSet()));
                        }
                        
                    } else {
                        log.warn("⚠️ Настройки '{}' исключены: нет пересечений с глобальными настройками", 
                                setting.getName());
                        log.warn("   📊 Таймфреймы настройки: {} vs глобальные: {}", 
                                settingTimeframes, allowedTimeframes);
                        log.warn("   📅 Периоды настройки: {} vs глобальные: {}", 
                                settingPeriods, allowedPeriods);
                    }
                    
                } catch (Exception e) {
                    log.error("❌ Ошибка при фильтрации настройки '{}': {}", 
                            setting.getName(), e.getMessage(), e);
                }
            }
            
            log.info("🏁 Итого для шедуллера: {} настроек из {} исходных прошли фильтрацию", 
                    filteredSettings.size(), originalSettings.size());
            
            return filteredSettings;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при получении настроек для шедуллера: {}", e.getMessage(), e);
            // Возвращаем исходные настройки при ошибке
            return repository.findByRunOnScheduleTrue();
        }
    }
    
    /**
     * Создает копию настройки с ограниченными таймфреймами и периодами
     */
    private StablePairsScreenerSettings createFilteredCopy(StablePairsScreenerSettings original,
                                                          Set<String> validTimeframes, Set<String> validPeriods) {
        StablePairsScreenerSettings copy = new StablePairsScreenerSettings();
        
        // Копируем основные поля
        copy.setId(original.getId());
        copy.setName(original.getName());
        copy.setDefault(original.isDefault());
        copy.setRunOnSchedule(original.isRunOnSchedule());
        copy.setLastUsedAt(original.getLastUsedAt());
        copy.setCreatedAt(original.getCreatedAt());
        copy.setUpdatedAt(original.getUpdatedAt());
        
        // Устанавливаем отфильтрованные таймфреймы и периоды
        copy.setSelectedTimeframesSet(validTimeframes);
        copy.setSelectedPeriodsSet(validPeriods);
        
        // Копируем настройки фильтров
        copy.setMinCorrelationEnabled(original.isMinCorrelationEnabled());
        copy.setMinCorrelationValue(original.getMinCorrelationValue());
        copy.setMinWindowSizeEnabled(original.isMinWindowSizeEnabled());
        copy.setMinWindowSizeValue(original.getMinWindowSizeValue());
        copy.setMaxAdfValueEnabled(original.isMaxAdfValueEnabled());
        copy.setMaxAdfValue(original.getMaxAdfValue());
        copy.setMinRSquaredEnabled(original.isMinRSquaredEnabled());
        copy.setMinRSquaredValue(original.getMinRSquaredValue());
        copy.setMaxPValueEnabled(original.isMaxPValueEnabled());
        copy.setMaxPValue(original.getMaxPValue());
        copy.setMinVolumeEnabled(original.isMinVolumeEnabled());
        copy.setMinVolumeValue(original.getMinVolumeValue());
        copy.setSearchTickersEnabled(original.isSearchTickersEnabled());
        copy.setSearchTickersSet(original.getSearchTickersSet());
        copy.setUseCache(original.getUseCache());
        
        return copy;
    }

    /**
     * Отметить настройки как использованные
     */
    @Transactional
    public void markAsUsed(Long settingsId) {
        log.debug("🕒 Отметка использования настроек ID: {}", settingsId);
        
        Optional<StablePairsScreenerSettings> settings = repository.findById(settingsId);
        if (settings.isPresent()) {
            StablePairsScreenerSettings s = settings.get();
            s.markAsUsed();
            repository.save(s);
        }
    }

    /**
     * Создать Map настроек поиска из объекта настроек
     */
    public Map<String, Object> buildSearchSettingsMap(StablePairsScreenerSettings settings) {
        log.debug("🔧 Построение карты настроек поиска");
        
        Map<String, Object> searchSettings = new HashMap<>();
        
        if (settings.isMinCorrelationEnabled() && settings.getMinCorrelationValue() != null) {
            searchSettings.put("minCorrelation", settings.getMinCorrelationValue());
        }
        
        if (settings.isMinWindowSizeEnabled() && settings.getMinWindowSizeValue() != null) {
            searchSettings.put("minWindowSize", settings.getMinWindowSizeValue().intValue());
        }
        
        if (settings.isMaxAdfValueEnabled() && settings.getMaxAdfValue() != null) {
            searchSettings.put("maxAdfValue", settings.getMaxAdfValue());
        }
        
        if (settings.isMinRSquaredEnabled() && settings.getMinRSquaredValue() != null) {
            searchSettings.put("minRSquared", settings.getMinRSquaredValue());
        }
        
        if (settings.isMaxPValueEnabled() && settings.getMaxPValue() != null) {
            searchSettings.put("maxPValue", settings.getMaxPValue());
        }
        
        if (settings.isMinVolumeEnabled() && settings.getMinVolumeValue() != null) {
            searchSettings.put("minVolume", settings.getMinVolumeValue());
        }
        
        if (settings.isSearchTickersEnabled() && !settings.getSearchTickersSet().isEmpty()) {
            searchSettings.put("searchTickers", settings.getSearchTickersSet());
        }
        
        // Добавляем настройку использования кэша
        searchSettings.put("useCache", settings.getUseCache() != null ? settings.getUseCache() : true);
        
        log.debug("✅ Карта настроек построена: {}", searchSettings);
        return searchSettings;
    }

    /**
     * Создать настройки из UI параметров
     */
    public StablePairsScreenerSettings createFromUIParams(
            String name,
            Set<String> timeframes,
            Set<String> periods,
            boolean minCorrelationEnabled, Double minCorrelationValue,
            boolean minWindowSizeEnabled, Double minWindowSizeValue,
            boolean maxAdfValueEnabled, Double maxAdfValue,
            boolean minRSquaredEnabled, Double minRSquaredValue,
            boolean maxPValueEnabled, Double maxPValue,
            boolean minVolumeEnabled, Double minVolumeValue,
            boolean searchTickersEnabled, Set<String> searchTickers,
            boolean runOnSchedule,
            Boolean useCache) {
        
        log.debug("🏗️ Создание настроек из UI параметров: {}", name);
        
        StablePairsScreenerSettings settings = new StablePairsScreenerSettings();
        settings.setName(name != null ? name : "Настройки " + LocalDateTime.now());
        settings.setSelectedTimeframesSet(timeframes);
        settings.setSelectedPeriodsSet(periods);
        settings.setMinCorrelationEnabled(minCorrelationEnabled);
        settings.setMinCorrelationValue(minCorrelationValue);
        settings.setMinWindowSizeEnabled(minWindowSizeEnabled);
        settings.setMinWindowSizeValue(minWindowSizeValue);
        settings.setMaxAdfValueEnabled(maxAdfValueEnabled);
        settings.setMaxAdfValue(maxAdfValue);
        settings.setMinRSquaredEnabled(minRSquaredEnabled);
        settings.setMinRSquaredValue(minRSquaredValue);
        settings.setMaxPValueEnabled(maxPValueEnabled);
        settings.setMaxPValue(maxPValue);
        settings.setMinVolumeEnabled(minVolumeEnabled);
        settings.setMinVolumeValue(minVolumeValue);
        settings.setSearchTickersEnabled(searchTickersEnabled);
        settings.setSearchTickersSet(searchTickers);
        settings.setRunOnSchedule(runOnSchedule);
        settings.setUseCache(useCache != null ? useCache : true);
        
        return settings;
    }

    /**
     * Валидация настроек
     */
    private void validateSettings(StablePairsScreenerSettings settings) {
        log.debug("✔️ Валидация настроек: {}", settings.getName());
        
        if (settings.getName() == null || settings.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Название настроек не может быть пустым");
        }
        
        // Проверка уникальности названия
        if (repository.existsByNameIgnoreCaseAndIdNot(settings.getName(), settings.getId())) {
            throw new IllegalArgumentException("Настройки с таким названием уже существуют");
        }
        
        Set<String> timeframes = settings.getSelectedTimeframesSet();
        if (timeframes.isEmpty()) {
            throw new IllegalArgumentException("Необходимо выбрать хотя бы один таймфрейм");
        }
        
        Set<String> periods = settings.getSelectedPeriodsSet();
        if (periods.isEmpty()) {
            throw new IllegalArgumentException("Необходимо выбрать хотя бы один период");
        }
        
        // Валидация числовых значений
        if (settings.isMinCorrelationEnabled() && 
            (settings.getMinCorrelationValue() == null || 
             settings.getMinCorrelationValue() < -1.0 || 
             settings.getMinCorrelationValue() > 1.0)) {
            throw new IllegalArgumentException("Минимальная корреляция должна быть от -1.0 до 1.0");
        }
        
        if (settings.isMinWindowSizeEnabled() && 
            (settings.getMinWindowSizeValue() == null || 
             settings.getMinWindowSizeValue() <= 0)) {
            throw new IllegalArgumentException("Минимальный размер окна должен быть больше 0");
        }
        
        if (settings.isMinVolumeEnabled() && 
            (settings.getMinVolumeValue() == null || 
             settings.getMinVolumeValue() <= 0)) {
            throw new IllegalArgumentException("Минимальный объем должен быть больше 0");
        }
        
        // Валидация фильтра тикеров
        if (settings.isSearchTickersEnabled()) {
            Set<String> tickers = settings.getSearchTickersSet();
            if (tickers.isEmpty()) {
                throw new IllegalArgumentException("При включенном фильтре тикеров должен быть указан хотя бы один тикер");
            }
        }
        
        log.debug("✅ Валидация настроек прошла успешно");
    }
}