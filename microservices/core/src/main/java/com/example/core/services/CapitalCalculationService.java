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

        // Базовый капитал на одну пару (уровень 0)
        double baseCapitalPerPair = longMarginSize + shortMarginSize;
        double totalBaseCapital = pairsCount * baseCapitalPerPair;

        double totalCapital = totalBaseCapital; // Начинаем с базового капитала
        double totalAveragingCapital = 0;

        // Если включено автоусреднение, рассчитываем дополнительный капитал
        if (settings.isAutoAveragingEnabled()) {
            double volumeMultiplier = settings.getAveragingVolumeMultiplier();
            int maxAveragingCount = settings.getMaxAveragingCount();

            // Рассчитываем капитал для каждого уровня усреднения (начиная с уровня 1)
            for (int level = 1; level <= maxAveragingCount; level++) {
                double currentMultiplier = Math.pow(volumeMultiplier, level);
                double longCapitalForLevel = longMarginSize * currentMultiplier;
                double shortCapitalForLevel = shortMarginSize * currentMultiplier;
                double averagingCapitalPerPair = longCapitalForLevel + shortCapitalForLevel;
                double averagingCapitalForLevel = pairsCount * averagingCapitalPerPair;

                totalAveragingCapital += averagingCapitalForLevel;

                log.trace("Уровень {}: множитель={}, капитал на пару={}, общий капитал уровня={}",
                        level, currentMultiplier, averagingCapitalPerPair, averagingCapitalForLevel);
            }

            totalCapital += totalAveragingCapital;
        }

        log.debug("💰 Расчет капитала: базовый={}, усреднение={}, итого={}",
                totalBaseCapital, totalAveragingCapital, totalCapital);

        return CapitalRequirement.builder()
                .pairsCount((int) pairsCount)
                .baseCapitalPerPair(baseCapitalPerPair)
                .totalBaseCapital(totalBaseCapital)
                .totalAveragingCapital(totalAveragingCapital)
                .totalRequiredCapital(totalCapital)
                .averagingEnabled(settings.isAutoAveragingEnabled())
                .maxAveragingCount(settings.getMaxAveragingCount())
                .volumeMultiplier(settings.getAveragingVolumeMultiplier())
                .build();
    }

    /**
     * Альтернативный метод расчета через цикл от 0 (более элегантный)
     * Можете использовать этот вместо основного, если хотите
     */
    public CapitalRequirement calculateRequiredCapitalAlternative(Settings settings) {
        double pairsCount = settings.getUsePairs();
        double longMarginSize = settings.getMaxLongMarginSize();
        double shortMarginSize = settings.getMaxShortMarginSize();

        double totalCapital = 0;
        double totalBaseCapital = 0;
        double totalAveragingCapital = 0;

        if (settings.isAutoAveragingEnabled()) {
            double volumeMultiplier = settings.getAveragingVolumeMultiplier();
            int maxAveragingCount = settings.getMaxAveragingCount();

            // Рассчитываем все уровни: 0 (базовый) + уровни усреднения (1, 2, 3...)
            for (int level = 0; level <= maxAveragingCount; level++) {
                double currentMultiplier = Math.pow(volumeMultiplier, level);
                double capitalPerPair = (longMarginSize * currentMultiplier) + (shortMarginSize * currentMultiplier);
                double capitalForLevel = pairsCount * capitalPerPair;

                if (level == 0) {
                    totalBaseCapital = capitalForLevel;
                } else {
                    totalAveragingCapital += capitalForLevel;
                }

                totalCapital += capitalForLevel;
            }
        } else {
            // Если усреднение отключено, берем только базовый капитал
            totalBaseCapital = pairsCount * (longMarginSize + shortMarginSize);
            totalCapital = totalBaseCapital;
        }

        log.debug("💰 Расчет капитала: базовый={}, усреднение={}, итого={}",
                totalBaseCapital, totalAveragingCapital, totalCapital);

        return CapitalRequirement.builder()
                .pairsCount((int) pairsCount)
                .baseCapitalPerPair(longMarginSize + shortMarginSize)
                .totalBaseCapital(totalBaseCapital)
                .totalAveragingCapital(totalAveragingCapital)
                .totalRequiredCapital(totalCapital)
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