package com.example.core.core.services;

import com.example.core.common.model.PairData;
import com.example.core.common.model.Settings;
import com.example.core.common.model.TradeStatus;
import com.example.core.core.repositories.PairDataRepository;
import com.example.core.trading.model.ArbitragePairTradeInfo;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.core.trading.services.TradingProviderFactory;
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
    private final PairDataRepository pairDataRepository;

    /**
     * Выполняет ручное усреднение позиции для указанной пары
     *
     * @param pairData торгуемая пара
     * @param settings настройки торговли
     * @return результат операции усреднения
     */
    @Transactional
    public AveragingResult performManualAveraging(PairData pairData, Settings settings) {
        log.info("");
        log.info("🔄 Начало ручного усреднения для пары: {}", pairData.getPairName());

        return executeAveraging(pairData, settings, "MANUAL");
    }

    /**
     * Выполняет автоматическое усреднение позиции при достижении порога просадки
     *
     * @param pairData торгуемая пара
     * @param settings настройки торговли
     * @return результат операции усреднения
     */
    @Transactional
    public AveragingResult performAutoAveraging(PairData pairData, Settings settings) {
        log.info("🤖 Начало автоматического усреднения для пары: {}", pairData.getPairName());

        return executeAveraging(pairData, settings, "AUTO");
    }

    /**
     * Проверяет, нужно ли выполнить автоматическое усреднение для пары
     *
     * @param pairData торгуемая пара
     * @param settings настройки торговли
     * @return true, если нужно усреднить
     */
    public boolean shouldPerformAutoAveraging(PairData pairData, Settings settings) {
        // Проверяем, включено ли автоусреднение
        if (!settings.isAutoAveragingEnabled()) {
            return false;
        }

        // Проверяем, что пара в активном трейде
        if (!isActiveTrade(pairData)) {
            return false;
        }

        // Получаем текущий профит в процентах
        BigDecimal currentProfitPercent = pairData.getProfitPercentChanges();
        if (currentProfitPercent == null) {
            return false;
        }

        // Проверяем, что просадка превышает пороговое значение
        double currentProfitDouble = currentProfitPercent.doubleValue();
        double threshold = -Math.abs(settings.getAveragingDrawdownThreshold()); // Делаем отрицательным

        boolean shouldAverage = currentProfitDouble <= threshold;

        if (shouldAverage) {
            log.info("📉 Обнаружена просадка для пары {}: {}% <= {}%. Требуется усреднение.",
                    pairData.getPairName(), currentProfitDouble, threshold);
        }

        return shouldAverage;
    }

    /**
     * Основной метод выполнения усреднения
     */
    private AveragingResult executeAveraging(PairData pairData, Settings settings, String trigger) {
        try {
            // Создаем временные настройки с увеличенным объемом
            Settings averagingSettings = createAveragingSettings(settings);

            // Открываем дополнительную позицию
            ArbitragePairTradeInfo tradeResult = tradingIntegrationService.openArbitragePair(pairData, averagingSettings);

            if (tradeResult == null || !tradeResult.isSuccess()) {
                log.error("❌ Не удалось выполнить усреднение для пары: {}", pairData.getPairName());
                return AveragingResult.failure("Не удалось открыть позицию для усреднения");
            }

            // Обновляем счетчик усреднений
            pairData.setAveragingCount(pairData.getAveragingCount() + 1);
            pairData.setLastAveragingTimestamp(System.currentTimeMillis());

            // Сохраняем изменения
            pairDataRepository.save(pairData);

            log.info("✅ Успешно выполнено усреднение #{} для пары: {} (триггер: {})",
                    pairData.getAveragingCount(), pairData.getPairName(), trigger);

            return AveragingResult.success(
                    String.format("Выполнено усреднение #%d для пары %s",
                            pairData.getAveragingCount(), pairData.getPairName())
            );

        } catch (Exception e) {
            log.error("💥 Ошибка при выполнении усреднения для пары {}: {}",
                    pairData.getPairName(), e.getMessage(), e);
            return AveragingResult.failure("Ошибка при усреднении: " + e.getMessage());
        }
    }

    /**
     * Создает настройки для усреднения с увеличенным объемом
     */
    private Settings createAveragingSettings(Settings originalSettings) {
        return Settings.builder()
                .maxLongMarginSize(originalSettings.getMaxLongMarginSize() * originalSettings.getAveragingVolumeMultiplier())
                .maxShortMarginSize(originalSettings.getMaxShortMarginSize() * originalSettings.getAveragingVolumeMultiplier())
                .leverage(originalSettings.getLeverage())
                .autoTradingEnabled(originalSettings.isAutoTradingEnabled())
                // Копируем все остальные настройки
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
                .build();
    }

    /**
     * Проверяет, находится ли пара в активном трейде
     */
    private boolean isActiveTrade(PairData pairData) {
        return pairData.getStatus() == TradeStatus.TRADING;
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