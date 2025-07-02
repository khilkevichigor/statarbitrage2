package com.example.statarbitrage.service;

import com.example.statarbitrage.dto.TradingPair;
import com.example.statarbitrage.model.Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Современный сервис валидации работающий с TradingPair
 * Заменяет старый ValidateService с ZScoreData
 */
@Slf4j
@Service
public class ModernValidateService {

    /**
     * Валидация размера списка торговых пар
     */
    public void validateSizeOfPairsAndThrow(List<TradingPair> tradingPairs, int expectedSize) {
        if (tradingPairs == null) {
            String message = "Список торговых пар не может быть null";
            log.error("❌ {}", message);
            throw new IllegalArgumentException(message);
        }
        
        if (tradingPairs.size() < expectedSize) {
            String message = String.format(
                "Недостаточно торговых пар: найдено %d, требуется минимум %d", 
                tradingPairs.size(), expectedSize);
            log.error("❌ {}", message);
            throw new IllegalStateException(message);
        }
        
        log.info("✅ Валидация размера прошла: {} пар >= {}", tradingPairs.size(), expectedSize);
    }

    /**
     * Валидация наличия положительных Z-score
     */
    public void validatePositiveZAndThrow(List<TradingPair> tradingPairs) {
        if (tradingPairs == null || tradingPairs.isEmpty()) {
            String message = "Список торговых пар пуст или null";
            log.error("❌ {}", message);
            throw new IllegalArgumentException(message);
        }
        
        boolean hasPositiveZ = tradingPairs.stream()
                .anyMatch(pair -> pair.getZscore() != null && pair.getZscore() > 0);
        
        if (!hasPositiveZ) {
            String message = "Не найдено пар с положительным Z-score";
            log.error("❌ {}", message);
            throw new IllegalStateException(message);
        }
        
        long positiveCount = tradingPairs.stream()
                .mapToLong(pair -> pair.getZscore() != null && pair.getZscore() > 0 ? 1 : 0)
                .sum();
        
        log.info("✅ Валидация положительного Z-score прошла: {} пар из {}", 
            positiveCount, tradingPairs.size());
    }

    /**
     * Мягкая проверка положительных Z-score (без исключения)
     */
    public boolean validatePositiveZ(List<TradingPair> tradingPairs) {
        try {
            validatePositiveZAndThrow(tradingPairs);
            return true;
        } catch (Exception e) {
            log.warn("⚠️ Мягкая валидация Z-score не прошла: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Валидация торговых пар по всем критериям Settings
     */
    public void validateTradingCriteria(List<TradingPair> tradingPairs, Settings settings) {
        if (tradingPairs == null || tradingPairs.isEmpty()) {
            throw new IllegalArgumentException("Список торговых пар пуст");
        }
        
        int validCount = 0;
        
        for (TradingPair pair : tradingPairs) {
            if (isValidPair(pair, settings)) {
                validCount++;
            }
        }
        
        if (validCount == 0) {
            String message = "Ни одна пара не прошла валидацию по критериям торговли";
            log.error("❌ {}", message);
            throw new IllegalStateException(message);
        }
        
        log.info("✅ Валидация критериев торговли прошла: {} пар из {} соответствуют требованиям", 
            validCount, tradingPairs.size());
    }

    /**
     * Валидация отдельной торговой пары
     */
    public boolean validateSinglePair(TradingPair pair, Settings settings) {
        if (pair == null) {
            log.error("❌ Торговая пара не может быть null");
            return false;
        }
        
        // Проверка основных полей
        if (pair.getBuyTicker() == null || pair.getBuyTicker().trim().isEmpty()) {
            log.error("❌ Пара {}: buyTicker не может быть пустым", pair.getDisplayName());
            return false;
        }
        
        if (pair.getSellTicker() == null || pair.getSellTicker().trim().isEmpty()) {
            log.error("❌ Пара {}: sellTicker не может быть пустым", pair.getDisplayName());
            return false;
        }
        
        if (pair.getBuyTicker().equals(pair.getSellTicker())) {
            log.error("❌ Пара {}: buyTicker и sellTicker не могут быть одинаковыми", pair.getDisplayName());
            return false;
        }
        
        // Проверка статистических параметров
        if (pair.getZscore() == null) {
            log.error("❌ Пара {}: Z-score не может быть null", pair.getDisplayName());
            return false;
        }
        
        if (pair.getCorrelation() == null) {
            log.error("❌ Пара {}: корреляция не может быть null", pair.getDisplayName());
            return false;
        }
        
        if (pair.getPValue() == null) {
            log.error("❌ Пара {}: p-value не может быть null", pair.getDisplayName());
            return false;
        }
        
        // Проверка критериев торговли
        if (!isValidPair(pair, settings)) {
            log.warn("⚠️ Пара {} не соответствует критериям торговли", pair.getDisplayName());
            return false;
        }
        
        log.info("✅ Пара {} прошла валидацию", pair.getDisplayName());
        return true;
    }

    /**
     * Валидация качества данных
     */
    public void validateDataQuality(List<TradingPair> tradingPairs) {
        if (tradingPairs == null || tradingPairs.isEmpty()) {
            throw new IllegalArgumentException("Список торговых пар пуст");
        }
        
        int qualityIssues = 0;
        
        for (TradingPair pair : tradingPairs) {
            // Проверка аномальных значений
            if (pair.getZscore() != null && Math.abs(pair.getZscore()) > 10) {
                log.warn("⚠️ Аномальный Z-score в паре {}: {}", pair.getDisplayName(), pair.getZscore());
                qualityIssues++;
            }
            
            if (pair.getCorrelation() != null && Math.abs(pair.getCorrelation()) > 1) {
                log.warn("⚠️ Некорректная корреляция в паре {}: {}", pair.getDisplayName(), pair.getCorrelation());
                qualityIssues++;
            }
            
            if (pair.getPValue() != null && (pair.getPValue() < 0 || pair.getPValue() > 1)) {
                log.warn("⚠️ Некорректный p-value в паре {}: {}", pair.getDisplayName(), pair.getPValue());
                qualityIssues++;
            }
            
            if (pair.getRSquared() != null && (pair.getRSquared() < 0 || pair.getRSquared() > 1)) {
                log.warn("⚠️ Некорректный R² в паре {}: {}", pair.getDisplayName(), pair.getRSquared());
                qualityIssues++;
            }
        }
        
        if (qualityIssues > tradingPairs.size() / 2) {
            String message = String.format("Слишком много проблем с качеством данных: %d из %d пар", 
                qualityIssues, tradingPairs.size());
            log.error("❌ {}", message);
            throw new IllegalStateException(message);
        }
        
        log.info("✅ Валидация качества данных: {} проблем из {} пар (допустимо)", 
            qualityIssues, tradingPairs.size());
    }

    /**
     * Проверка валидности пары по критериям
     */
    private boolean isValidPair(TradingPair pair, Settings settings) {
        // Используем расширенную валидацию TradingPair
        return pair.isValidForTradingExtended(
            settings.getMinCorrelation(),
            settings.getMinPvalue(),
            settings.getMinZ(),
            settings.getMinAdfValue()
        );
    }

    /**
     * Полная валидация списка торговых пар
     */
    public void validateFullPairList(List<TradingPair> tradingPairs, Settings settings, int minSize) {
        log.info("🔍 Полная валидация {} торговых пар", tradingPairs.size());
        
        // 1. Валидация размера
        validateSizeOfPairsAndThrow(tradingPairs, minSize);
        
        // 2. Валидация положительных Z-score
        validatePositiveZAndThrow(tradingPairs);
        
        // 3. Валидация критериев торговли
        validateTradingCriteria(tradingPairs, settings);
        
        // 4. Валидация качества данных
        validateDataQuality(tradingPairs);
        
        log.info("✅ Полная валидация прошла успешно для {} пар", tradingPairs.size());
    }

    /**
     * Генерация отчета по валидации
     */
    public ValidationReport generateValidationReport(List<TradingPair> tradingPairs, Settings settings) {
        ValidationReport report = new ValidationReport();
        
        report.totalPairs = tradingPairs.size();
        report.validPairs = (int) tradingPairs.stream()
                .mapToLong(pair -> isValidPair(pair, settings) ? 1 : 0)
                .sum();
        report.positiveZPairs = (int) tradingPairs.stream()
                .mapToLong(pair -> pair.getZscore() != null && pair.getZscore() > 0 ? 1 : 0)
                .sum();
        report.highCorrelationPairs = (int) tradingPairs.stream()
                .mapToLong(pair -> pair.getCorrelation() != null && 
                    Math.abs(pair.getCorrelation()) >= settings.getMinCorrelation() ? 1 : 0)
                .sum();
        
        log.info("📊 Отчет валидации: всего={}, валидных={}, с Z>0={}, с высокой корр.={}", 
            report.totalPairs, report.validPairs, report.positiveZPairs, report.highCorrelationPairs);
        
        return report;
    }

    /**
     * Класс отчета валидации
     */
    public static class ValidationReport {
        public int totalPairs;
        public int validPairs;
        public int positiveZPairs;
        public int highCorrelationPairs;
        
        public double getValidationRate() {
            return totalPairs > 0 ? (double) validPairs / totalPairs : 0.0;
        }
        
        public boolean isAcceptable() {
            return getValidationRate() >= 0.1; // Минимум 10% валидных пар
        }
    }
}