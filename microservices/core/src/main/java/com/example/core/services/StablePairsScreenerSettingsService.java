package com.example.core.services;

import com.example.core.repositories.StablePairsScreenerSettingsRepository;
import com.example.shared.models.StablePairsScreenerSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Сервис для работы с настройками скриннера стабильных пар
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StablePairsScreenerSettingsService {

    private final StablePairsScreenerSettingsRepository repository;

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
     * Получить настройки для автоматического запуска
     */
    public List<StablePairsScreenerSettings> getScheduledSettings() {
        log.debug("⏰ Получение настроек для автоматического запуска");
        return repository.findByRunOnScheduleTrue();
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