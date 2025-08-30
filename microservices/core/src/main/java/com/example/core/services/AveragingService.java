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

        // Получаем текущий профит в процентах
        BigDecimal currentProfitPercent = tradingPair.getProfitPercentChanges();
        if (currentProfitPercent == null) {
            return false;
        }

        // Проверяем, что просадка превышает пороговое значение
        double currentProfitDouble = currentProfitPercent.doubleValue();
        double threshold = -Math.abs(settings.getAveragingDrawdownThreshold()); // Делаем отрицательным

        boolean shouldAverage = currentProfitDouble <= threshold;

        if (shouldAverage) {
            log.info("📉 Обнаружена просадка для пары {}: {}% <= {}%. Требуется усреднение.",
                    tradingPair.getPairName(), currentProfitDouble, threshold);
        }

        return shouldAverage;
    }

    /**
     * Основной метод выполнения усреднения
     */
    private AveragingResult executeAveraging(TradingPair tradingPair, Settings settings, String trigger) {
        try {
            // Создаем временные настройки с увеличенным объемом
            Settings averagingSettings = createAveragingSettings(settings);

            // Открываем дополнительную позицию
            ArbitragePairTradeInfo tradeResult = tradingIntegrationService.openArbitragePair(tradingPair, averagingSettings);

            if (tradeResult == null || !tradeResult.isSuccess()) {
                log.error("❌ Не удалось выполнить усреднение для пары: {}", tradingPair.getPairName());
                return AveragingResult.failure("Не удалось открыть позицию для усреднения");
            }

            // Обновляем счетчик усреднений
            tradingPair.setAveragingCount(tradingPair.getAveragingCount() + 1);
            tradingPair.setLastAveragingTimestamp(System.currentTimeMillis());

            // Сохраняем изменения
            tradingPairRepository.save(tradingPair);

            log.info("✅ Успешно выполнено усреднение #{} для пары: {} (триггер: {})",
                    tradingPair.getAveragingCount(), tradingPair.getPairName(), trigger);

            return AveragingResult.success(
                    String.format("Выполнено усреднение #%d для пары %s",
                            tradingPair.getAveragingCount(), tradingPair.getPairName())
            );

        } catch (Exception e) {
            log.error("💥 Ошибка при выполнении усреднения для пары {}: {}",
                    tradingPair.getPairName(), e.getMessage(), e);
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