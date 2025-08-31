package com.example.core.services;

import com.example.core.repositories.TradingPairRepository;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.core.trading.services.TradingProviderFactory;
import com.example.shared.dto.ArbitragePairTradeInfo;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Settings;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Сервис для усреднения позиций в парном трейдинге
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AveragingService {

    private final TradingIntegrationService tradingIntegrationService;
    private final TradingProviderFactory tradingProviderFactory;
    private final TradingPairRepository tradingPairRepository;

    /**
     * Выполняет ручное усреднение позиции для указанной пары
     *
     * @param tradingPair торгуемая пара
     * @param settings    настройки торговли
     * @return результат операции усреднения
     */
    @Transactional
    public AveragingResult performManualAveraging(TradingPair tradingPair, Settings settings) {
        log.info("");
        log.info("🔄 Начало ручного усреднения для пары: {}", tradingPair.getPairName());

        return executeAveraging(tradingPair, settings, "MANUAL");
    }

    /**
     * Выполняет автоматическое усреднение позиции при достижении порога просадки
     *
     * @param tradingPair торгуемая пара
     * @param settings    настройки торговли
     * @return результат операции усреднения
     */
    @Transactional
    public AveragingResult performAutoAveraging(TradingPair tradingPair, Settings settings) {
        log.info("🤖 Начало автоматического усреднения для пары: {}", tradingPair.getPairName());

        return executeAveraging(tradingPair, settings, "AUTO");
    }

    /**
     * Проверяет, нужно ли выполнить автоматическое усреднение для пары
     *
     * @param tradingPair торгуемая пара
     * @param settings    настройки торговли
     * @return true, если нужно усреднить
     */
    public boolean shouldPerformAutoAveraging(TradingPair tradingPair, Settings settings) {
        // Проверяем, включено ли автоусреднение
        if (!settings.isAutoAveragingEnabled()) {
            return false;
        }

        // Проверяем, что пара в активном трейде
        if (!isActiveTrade(tradingPair)) {
            return false;
        }

        // Проверяем, не превышен ли максимальный лимит усреднений
        if (tradingPair.getAveragingCount() >= settings.getMaxAveragingCount()) {
            log.info("🚫 Достигнут максимальный лимит усреднений для пары {}: {} >= {}",
                    tradingPair.getPairName(), tradingPair.getAveragingCount(), settings.getMaxAveragingCount());
            return false;
        }

        // Получаем текущий профит в процентах
        BigDecimal currentProfitPercent = tradingPair.getProfitPercentChanges();
        if (currentProfitPercent == null) {
            return false;
        }

        // Рассчитываем прогрессивный порог просадки
        double threshold = calculateAveragingThreshold(tradingPair.getAveragingCount(), settings);
        double currentProfitDouble = currentProfitPercent.doubleValue();

        boolean shouldAverage = currentProfitDouble <= threshold;

        if (shouldAverage) {
            log.info("📉 Обнаружена просадка для пары {}: {}% <= {}%. Требуется усреднение #{}/{}.",
                    tradingPair.getPairName(), currentProfitDouble, threshold,
                    tradingPair.getAveragingCount() + 1, settings.getMaxAveragingCount());
        }

        return shouldAverage;
    }

    /**
     * Основной метод выполнения усреднения
     */
    private AveragingResult executeAveraging(TradingPair tradingPair, Settings settings, String trigger) {
        try {
            // Создаем временные настройки с прогрессивно увеличенным объемом
            Settings averagingSettings = createAveragingSettings(settings, tradingPair.getAveragingCount());

            // Сохраняем текущий профит перед усреднением для отслеживания
            tradingPair.setLastAveragingProfitPercent(tradingPair.getProfitPercentChanges());

            // Открываем дополнительную позицию
            ArbitragePairTradeInfo tradeResult = tradingIntegrationService.openArbitragePair(tradingPair, averagingSettings);

            if (tradeResult == null || !tradeResult.isSuccess()) {
                log.error("❌ Не удалось выполнить усреднение для пары: {}", tradingPair.getPairName());
                return AveragingResult.failure("Не удалось открыть позицию для усреднения");
            }

            // Обновляем счетчик усреднений
            int newAveragingCount = tradingPair.getAveragingCount() + 1;
            tradingPair.setAveragingCount(newAveragingCount);
            tradingPair.setLastAveragingTimestamp(System.currentTimeMillis());

            // Копируем настройки усреднения в торговую пару для отслеживания
            tradingPair.setSettingsAveragingDrawdownMultiplier(settings.getAveragingDrawdownMultiplier());
            tradingPair.setSettingsMaxAveragingCount(settings.getMaxAveragingCount());

            // Сохраняем изменения
            tradingPairRepository.save(tradingPair);

            double volumeMultiplier = calculateVolumeMultiplier(newAveragingCount - 1, settings);
            log.info("✅ Успешно выполнено усреднение #{}/{} для пары: {} (триггер: {}, множитель объема: x{})",
                    newAveragingCount, settings.getMaxAveragingCount(),
                    tradingPair.getPairName(), trigger, String.format("%.2f", volumeMultiplier));

            return AveragingResult.success(
                    String.format("Выполнено усреднение #%d/%d для пары %s",
                            newAveragingCount, settings.getMaxAveragingCount(), tradingPair.getPairName())
            );

        } catch (Exception e) {
            log.error("💥 Ошибка при выполнении усреднения для пары {}: {}",
                    tradingPair.getPairName(), e.getMessage(), e);
            return AveragingResult.failure("Ошибка при усреднении: " + e.getMessage());
        }
    }

    /**
     * Рассчитывает пороговое значение просадки для усреднения с учетом множителя
     *
     * @param currentAveragingCount текущее количество усреднений
     * @param settings              настройки торговли
     * @return пороговое значение просадки (отрицательное)
     */
    private double calculateAveragingThreshold(int currentAveragingCount, Settings settings) {
        double baseThreshold = settings.getAveragingDrawdownThreshold();
        double multiplier = settings.getAveragingDrawdownMultiplier();

        // Рассчитываем прогрессивный порог
        // Первое усреднение: -10%, второе: -15%, третье: -22.5% при множителе 1.5
        double threshold = baseThreshold;
        for (int i = 0; i < currentAveragingCount; i++) {
            threshold *= multiplier;
        }

        // Возвращаем отрицательное значение (просадка)
        return -Math.abs(threshold);
    }

    /**
     * Рассчитывает множитель объема для текущего усреднения
     *
     * @param currentAveragingCount текущее количество усреднений
     * @param settings              настройки торговли
     * @return множитель объема
     */
    private double calculateVolumeMultiplier(int currentAveragingCount, Settings settings) {
        double baseMultiplier = settings.getAveragingVolumeMultiplier();

        // Прогрессивное увеличение объема
        // Первое усреднение: x1.5, второе: x2.25, третье: x3.375 при множителе 1.5
        double volumeMultiplier = 1.0;
        for (int i = 0; i <= currentAveragingCount; i++) {
            volumeMultiplier *= baseMultiplier;
        }

        return volumeMultiplier;
    }

    /**
     * Создает настройки для усреднения с прогрессивно увеличенным объемом
     */
    private Settings createAveragingSettings(Settings originalSettings, int currentAveragingCount) {
        double volumeMultiplier = calculateVolumeMultiplier(currentAveragingCount, originalSettings);
        
        return Settings.builder()
                // Основные настройки торговли
                .timeframe(originalSettings.getTimeframe())
                .candleLimit(originalSettings.getCandleLimit())
                .minZ(originalSettings.getMinZ())
                .minWindowSize(originalSettings.getMinWindowSize())
                .maxPValue(originalSettings.getMaxPValue())
                .maxAdfValue(originalSettings.getMaxAdfValue())
                .minRSquared(originalSettings.getMinRSquared())
                .minCorrelation(originalSettings.getMinCorrelation())
                .minVolume(originalSettings.getMinVolume())
                .checkInterval(originalSettings.getCheckInterval())
                
                // Размеры позиций с множителем объема
                .maxLongMarginSize(originalSettings.getMaxLongMarginSize() * volumeMultiplier) //новый объем
                .maxShortMarginSize(originalSettings.getMaxShortMarginSize() * volumeMultiplier) //новый объем
                .leverage(originalSettings.getLeverage())
                
                // Стратегии выхода
                .exitTake(originalSettings.getExitTake())
                .exitStop(originalSettings.getExitStop())
                .exitZMin(originalSettings.getExitZMin())
                .exitZMax(originalSettings.getExitZMax())
                .exitZMaxPercent(originalSettings.getExitZMaxPercent())
                .exitTimeMinutes(originalSettings.getExitTimeMinutes())
                .exitBreakEvenPercent(originalSettings.getExitBreakEvenPercent())
                .exitNegativeZMinProfitPercent(originalSettings.getExitNegativeZMinProfitPercent())
                
                .usePairs(originalSettings.getUsePairs())
                .autoTradingEnabled(originalSettings.isAutoTradingEnabled())
                
                // Флаги фильтров
                .useMinZFilter(originalSettings.isUseMinZFilter())
                .useMinRSquaredFilter(originalSettings.isUseMinRSquaredFilter())
                .useMaxPValueFilter(originalSettings.isUseMaxPValueFilter())
                .useMaxAdfValueFilter(originalSettings.isUseMaxAdfValueFilter())
                .useMinCorrelationFilter(originalSettings.isUseMinCorrelationFilter())
                .useMinVolumeFilter(originalSettings.isUseMinVolumeFilter())
                
                // Флаги стратегий выхода
                .useExitTake(originalSettings.isUseExitTake())
                .useExitStop(originalSettings.isUseExitStop())
                .useExitZMin(originalSettings.isUseExitZMin())
                .useExitZMax(originalSettings.isUseExitZMax())
                .useExitZMaxPercent(originalSettings.isUseExitZMaxPercent())
                .useExitTimeMinutes(originalSettings.isUseExitTimeMinutes())
                .useExitBreakEvenPercent(originalSettings.isUseExitBreakEvenPercent())
                .useExitNegativeZMinProfitPercent(originalSettings.isUseExitNegativeZMinProfitPercent())
                .useCointegrationStabilityFilter(originalSettings.isUseCointegrationStabilityFilter())
                
                // Списки
                .minimumLotBlacklist(originalSettings.getMinimumLotBlacklist())
                .observedPairs(originalSettings.getObservedPairs())
                
                // Флаги скоринга
                .useZScoreScoring(originalSettings.isUseZScoreScoring())
                .usePixelSpreadScoring(originalSettings.isUsePixelSpreadScoring())
                .useCointegrationScoring(originalSettings.isUseCointegrationScoring())
                .useModelQualityScoring(originalSettings.isUseModelQualityScoring())
                .useStatisticsScoring(originalSettings.isUseStatisticsScoring())
                .useBonusScoring(originalSettings.isUseBonusScoring())
                
                // Веса скоринга
                .zScoreScoringWeight(originalSettings.getZScoreScoringWeight())
                .pixelSpreadScoringWeight(originalSettings.getPixelSpreadScoringWeight())
                .cointegrationScoringWeight(originalSettings.getCointegrationScoringWeight())
                .modelQualityScoringWeight(originalSettings.getModelQualityScoringWeight())
                .statisticsScoringWeight(originalSettings.getStatisticsScoringWeight())
                .bonusScoringWeight(originalSettings.getBonusScoringWeight())
                
                // Настройки усреднения
                .autoAveragingEnabled(originalSettings.isAutoAveragingEnabled())
                .averagingDrawdownThreshold(originalSettings.getAveragingDrawdownThreshold())
                .averagingVolumeMultiplier(originalSettings.getAveragingVolumeMultiplier())
                .averagingDrawdownMultiplier(originalSettings.getAveragingDrawdownMultiplier())
                .maxAveragingCount(originalSettings.getMaxAveragingCount())
                
                .build();
    }

    /**
     * Проверяет, находится ли пара в активном трейде
     */
    private boolean isActiveTrade(TradingPair tradingPair) {
        return tradingPair.getStatus() == TradeStatus.TRADING;
    }

    /**
     * Результат операции усреднения
     */
    public static class AveragingResult {
        private final boolean success;
        private final String message;

        private AveragingResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static AveragingResult success(String message) {
            return new AveragingResult(true, message);
        }

        public static AveragingResult failure(String message) {
            return new AveragingResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}