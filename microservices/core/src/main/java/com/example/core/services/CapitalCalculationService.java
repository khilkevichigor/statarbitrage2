package com.example.core.services;

import com.example.shared.models.Settings;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Сервис для расчета требуемого капитала при заданных настройках торговли
 */
@Slf4j
@Service
public class CapitalCalculationService {

    /**
     * Рассчитывает общий требуемый капитал для заданных настроек
     *
     * @param settings настройки торговли
     * @return объект с расчетами капитала
     */
    public CapitalRequirement calculateRequiredCapital(Settings settings) {
        double pairsCount = settings.getUsePairs();
        double longMarginSize = settings.getMaxLongMarginSize();
        double shortMarginSize = settings.getMaxShortMarginSize();
        
        // Базовый капитал на одну пару (без усреднения)
        double baseCapitalPerPair = longMarginSize + shortMarginSize;
        double totalBaseCapital = pairsCount * baseCapitalPerPair;
        
        double totalAveragingCapital = 0;
        
        // Если включено автоусреднение, рассчитываем дополнительный капитал
        if (settings.isAutoAveragingEnabled()) {
            double volumeMultiplier = settings.getAveragingVolumeMultiplier();
            int maxAveragingCount = settings.getMaxAveragingCount();
            
            // Рассчитываем капитал для каждого уровня усреднения
            for (int i = 1; i <= maxAveragingCount; i++) {
                double currentMultiplier = Math.pow(volumeMultiplier, i);
                double averagingCapitalPerPair = (longMarginSize * currentMultiplier) + (shortMarginSize * currentMultiplier);
                totalAveragingCapital += pairsCount * averagingCapitalPerPair;
            }
        }
        
        double totalRequiredCapital = totalBaseCapital + totalAveragingCapital;
        
        log.debug("💰 Расчет капитала: базовый={}, усреднение={}, итого={}", 
                totalBaseCapital, totalAveragingCapital, totalRequiredCapital);
        
        return CapitalRequirement.builder()
                .pairsCount((int) pairsCount)
                .baseCapitalPerPair(baseCapitalPerPair)
                .totalBaseCapital(totalBaseCapital)
                .totalAveragingCapital(totalAveragingCapital)
                .totalRequiredCapital(totalRequiredCapital)
                .averagingEnabled(settings.isAutoAveragingEnabled())
                .maxAveragingCount(settings.getMaxAveragingCount())
                .volumeMultiplier(settings.getAveragingVolumeMultiplier())
                .build();
    }

    /**
     * Проверяет, превышает ли требуемый капитал доступный депозит
     *
     * @param requiredCapital требуемый капитал
     * @param availableBalance доступный баланс с OKX
     * @return результат проверки депозита
     */
    public DepositCheckResult checkDeposit(CapitalRequirement requiredCapital, double availableBalance) {
        boolean isExceeded = requiredCapital.getTotalRequiredCapital() > availableBalance;
        double difference = requiredCapital.getTotalRequiredCapital() - availableBalance;
        double utilizationPercent = availableBalance > 0 ? (requiredCapital.getTotalRequiredCapital() / availableBalance) * 100 : 0;
        
        return DepositCheckResult.builder()
                .requiredCapital(requiredCapital.getTotalRequiredCapital())
                .availableDeposit(availableBalance)
                .isExceeded(isExceeded)
                .difference(difference)
                .utilizationPercent(utilizationPercent)
                .build();
    }

    /**
     * Результат расчета требуемого капитала
     */
    @Builder
    @Data
    public static class CapitalRequirement {
        private int pairsCount;
        private double baseCapitalPerPair;
        private double totalBaseCapital;
        private double totalAveragingCapital;
        private double totalRequiredCapital;
        private boolean averagingEnabled;
        private int maxAveragingCount;
        private double volumeMultiplier;
    }

    /**
     * Результат проверки депозита
     */
    @Builder
    @Data
    public static class DepositCheckResult {
        private double requiredCapital;
        private double availableDeposit;
        private boolean isExceeded;
        private double difference;
        private double utilizationPercent;
    }
}