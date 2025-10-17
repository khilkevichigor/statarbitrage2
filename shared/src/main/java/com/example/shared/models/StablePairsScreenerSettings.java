package com.example.shared.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Настройки скриннера стабильных пар
 */
@Entity
@Table(name = "stable_pairs_screener_settings")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class StablePairsScreenerSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Название настройки для удобства пользователя
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Является ли настройка по умолчанию
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    // ======== ТАЙМФРЕЙМЫ И ПЕРИОДЫ ========

    /**
     * Выбранные таймфреймы (через запятую)
     */
    @Column(name = "selected_timeframes", columnDefinition = "TEXT")
    private String selectedTimeframes;

    /**
     * Выбранные периоды (через запятую)
     */
    @Column(name = "selected_periods", columnDefinition = "TEXT")
    private String selectedPeriods;

    // ======== ФИЛЬТРЫ КОРРЕЛЯЦИИ ========

    /**
     * Включен ли фильтр минимальной корреляции
     */
    @Column(name = "min_correlation_enabled", nullable = false)
    private boolean minCorrelationEnabled = true;

    /**
     * Минимальная корреляция
     */
    @Column(name = "min_correlation_value")
    private Double minCorrelationValue = 0.1;

    // ======== ФИЛЬТРЫ РАЗМЕРА ОКНА ========

    /**
     * Включен ли фильтр минимального размера окна
     */
    @Column(name = "min_window_size_enabled", nullable = false)
    private boolean minWindowSizeEnabled = true;

    /**
     * Минимальный размер окна
     */
    @Column(name = "min_window_size_value")
    private Double minWindowSizeValue = 450.0;

    // ======== ФИЛЬТРЫ ADF ========

    /**
     * Включен ли фильтр максимального ADF значения
     */
    @Column(name = "max_adf_value_enabled", nullable = false)
    private boolean maxAdfValueEnabled = true;

    /**
     * Максимальное ADF значение
     */
    @Column(name = "max_adf_value")
    private Double maxAdfValue = 0.1;

    // ======== ФИЛЬТРЫ R2 ========

    /**
     * Включен ли фильтр минимального R2
     */
    @Column(name = "min_r_squared_enabled", nullable = false)
    private boolean minRSquaredEnabled = true;

    /**
     * Минимальное R2 значение
     */
    @Column(name = "min_r_squared_value")
    private Double minRSquaredValue = 0.1;

    // ======== ФИЛЬТРЫ P-VALUE ========

    /**
     * Включен ли фильтр максимального P-Value
     */
    @Column(name = "max_p_value_enabled", nullable = false)
    private boolean maxPValueEnabled = true;

    /**
     * Максимальное P-Value
     */
    @Column(name = "max_p_value")
    private Double maxPValue = 0.1;

    // ======== ФИЛЬТР ПО ОБЪЕМУ ========

    /**
     * Включен ли фильтр по минимальному объему торгов
     */
    @Column(name = "min_volume_enabled", nullable = false)
    private boolean minVolumeEnabled = true;

    /**
     * Минимальный объем торгов в миллионах долларов
     */
    @Column(name = "min_volume_value")
    private Double minVolumeValue = 20.0;

    // ======== ФИЛЬТР ПО ТИКЕРАМ ========

    /**
     * Включен ли фильтр по определенным тикерам
     */
    @Column(name = "search_tickers_enabled", nullable = false)
    private boolean searchTickersEnabled = false;

    /**
     * Список тикеров для поиска (через запятую)
     * Если пустое - то поиск по всем возможным тикерам
     */
    @Column(name = "search_tickers", columnDefinition = "TEXT")
    private String searchTickers;

    // ======== АВТОМАТИЧЕСКИЙ ЗАПУСК ========

    /**
     * Запускать по расписанию
     */
    @Column(name = "run_on_schedule", nullable = false)
    private boolean runOnSchedule = false;

    /**
     * Использовать кэш для получения свечей (по умолчанию включено)
     * Если выключено - загружать свечи напрямую с OKX
     */
    @Column(name = "use_cache", nullable = false)
    private Boolean useCache = true;

    // ======== МЕТАДАННЫЕ ========

    /**
     * Дата создания
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Дата последнего использования
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    // ======== МЕТОДЫ ЖИЗНЕННОГО ЦИКЛА ========

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ======== УТИЛИТЫ ДЛЯ РАБОТЫ С МНОЖЕСТВАМИ ========

    /**
     * Получить выбранные таймфреймы как Set
     */
    public Set<String> getSelectedTimeframesSet() {
        Set<String> result = new HashSet<>();
        if (selectedTimeframes != null && !selectedTimeframes.trim().isEmpty()) {
            String[] timeframes = selectedTimeframes.split(",");
            for (String tf : timeframes) {
                String trimmed = tf.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    /**
     * Установить выбранные таймфреймы из Set
     */
    public void setSelectedTimeframesSet(Set<String> timeframes) {
        if (timeframes == null || timeframes.isEmpty()) {
            this.selectedTimeframes = "";
        } else {
            this.selectedTimeframes = String.join(",", timeframes);
        }
    }

    /**
     * Получить выбранные периоды как Set
     */
    public Set<String> getSelectedPeriodsSet() {
        Set<String> result = new HashSet<>();
        if (selectedPeriods != null && !selectedPeriods.trim().isEmpty()) {
            String[] periods = selectedPeriods.split(",");
            for (String period : periods) {
                String trimmed = period.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    /**
     * Установить выбранные периоды из Set
     */
    public void setSelectedPeriodsSet(Set<String> periods) {
        if (periods == null || periods.isEmpty()) {
            this.selectedPeriods = "";
        } else {
            this.selectedPeriods = String.join(",", periods);
        }
    }

    /**
     * Получить тикеры для поиска как Set
     */
    public Set<String> getSearchTickersSet() {
        Set<String> result = new HashSet<>();
        if (searchTickers != null && !searchTickers.trim().isEmpty()) {
            String[] tickers = searchTickers.split(",");
            for (String ticker : tickers) {
                String trimmed = ticker.trim().toUpperCase();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    /**
     * Установить тикеры для поиска из Set
     */
    public void setSearchTickersSet(Set<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            this.searchTickers = "";
        } else {
            // Конвертируем в верхний регистр и сортируем для консистентности
            this.searchTickers = tickers.stream()
                .map(String::toUpperCase)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        }
    }

    /**
     * Добавить тикеры к существующим
     */
    public void addTickers(String... newTickers) {
        Set<String> currentTickers = getSearchTickersSet();
        for (String ticker : newTickers) {
            if (ticker != null && !ticker.trim().isEmpty()) {
                currentTickers.add(ticker.trim().toUpperCase());
            }
        }
        setSearchTickersSet(currentTickers);
    }

    // ======== УТИЛИТЫ ДЛЯ СОЗДАНИЯ НАСТРОЕК ПО УМОЛЧАНИЮ ========

    /**
     * Создать настройки по умолчанию
     */
    public static StablePairsScreenerSettings createDefault() {
        return StablePairsScreenerSettings.builder()
                .name("Настройки по умолчанию")
                .isDefault(true)
                .selectedTimeframes("1D")
                .selectedPeriods("месяц")
                .minCorrelationEnabled(true)
                .minCorrelationValue(0.1)
                .minWindowSizeEnabled(true)
                .minWindowSizeValue(450.0)
                .maxAdfValueEnabled(true)
                .maxAdfValue(0.1)
                .minRSquaredEnabled(true)
                .minRSquaredValue(0.1)
                .maxPValueEnabled(true)
                .maxPValue(0.1)
                .minVolumeEnabled(true)
                .minVolumeValue(20.0)
                .searchTickersEnabled(false)
                .searchTickers("")
                .runOnSchedule(false)
                .useCache(true)
                .build();
    }

    /**
     * Обновить время последнего использования
     */
    public void markAsUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "StablePairsScreenerSettings{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isDefault=" + isDefault +
                ", selectedTimeframes='" + selectedTimeframes + '\'' +
                ", selectedPeriods='" + selectedPeriods + '\'' +
                ", runOnSchedule=" + runOnSchedule +
                ", createdAt=" + createdAt +
                '}';
    }
}