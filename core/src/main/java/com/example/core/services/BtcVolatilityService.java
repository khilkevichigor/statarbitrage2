package com.example.core.services;

import com.example.core.client.CandlesFeignClient;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.models.Settings;
import com.example.shared.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 🪙 Сервис анализа волатильности Bitcoin для фильтрации автотрейдинга
 * Рассчитывает ATR, дневное движение и другие индикаторы для определения
 * можно ли торговать в текущих рыночных условиях
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BtcVolatilityService {

    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;

    // Кэш последней проверки
    private LocalDateTime lastCheckTime;
    private Boolean lastCheckResult;
    private double lastAtr;
    private double lastDailyRange;
    private double lastDailyChangePercent;

    // Время жизни кэша (5 минут)
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);

    /**
     * Основной метод проверки - можно ли торговать
     * @return true если можно торговать, false если нет
     */
    public boolean canTradeNow() {
        try {
            Settings settings = settingsService.getSettings();
            
            // Если фильтр выключен - всегда можно торговать
            if (!settings.isUseBtcVolatilityFilter()) {
                log.debug("🪙 BTC фильтр отключен - торговля разрешена");
                return true;
            }

            // Проверяем кэш
            if (isCacheValid()) {
                log.debug("🪙 Используем кэшированный результат BTC анализа: canTrade={}", lastCheckResult);
                return lastCheckResult;
            }

            log.info("");
            log.info("🪙 Запуск анализа волатильности Bitcoin...");

            // Получаем свечи BTC
            List<Candle> btcCandles = getBtcCandles(settings);
            if (btcCandles.isEmpty()) {
                log.warn("🪙 ⚠️ Не удалось получить свечи BTC - разрешаем торговлю по умолчанию");
                cacheResult(true, 0, 0, 0);
                return true;
            }

            // Рассчитываем индикаторы
            BtcVolatilityData data = calculateVolatilityIndicators(btcCandles);
            
            // Проверяем условия фильтрации
            boolean canTrade = checkTradingConditions(settings, data);
            
            // Кэшируем результат
            cacheResult(canTrade, data.currentAtr, data.currentDailyRange, data.dailyChangePercent);
            
            // Логируем детальный результат
            logAnalysisResult(settings, data, canTrade);
            
            return canTrade;

        } catch (Exception e) {
            log.error("🪙 ❌ Критическая ошибка при анализе BTC волатильности: {}", e.getMessage(), e);
            // При ошибке разрешаем торговлю для безопасности
            return true;
        }
    }

    /**
     * Получает свечи Bitcoin с биржи
     */
    private List<Candle> getBtcCandles(Settings settings) {
        try {
            log.debug("🪙 Загрузка свечей BTC: таймфрейм={}, лимит={}", 
                    settings.getTimeframe(), (int) settings.getCandleLimit());

            ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                    .timeframe(settings.getTimeframe())
                    .candleLimit((int) settings.getCandleLimit())
                    .tickers(Arrays.asList("BTC-USDT-SWAP"))
                    .excludeTickers(Collections.emptyList())
                    .period(settings.calculateCurrentPeriod())
                    .untilDate(StringUtils.getCurrentDateTimeWithZ())
                    .exchange("OKX")
                    .useCache(true)
                    .useMinVolumeFilter(false)
                    .minimumLotBlacklist(null)
                    .sorted(true)
                    .build();

            Map<String, List<Candle>> candlesMap = candlesFeignClient.getValidatedCacheExtended(request);
            
            if (candlesMap != null && candlesMap.containsKey("BTC-USDT-SWAP")) {
                List<Candle> candles = candlesMap.get("BTC-USDT-SWAP");
                log.debug("🪙 ✅ Получено {} свечей BTC", candles.size());
                return candles;
            } else {
                log.warn("🪙 ⚠️ BTC-USDT не найден в ответе микросервиса candles");
                return Collections.emptyList();
            }

        } catch (Exception e) {
            log.error("🪙 ❌ Ошибка при загрузке свечей BTC: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Рассчитывает индикаторы волатильности
     */
    private BtcVolatilityData calculateVolatilityIndicators(List<Candle> candles) {
        if (candles.size() < 14) {
            log.warn("🪙 ⚠️ Недостаточно свечей для расчета ATR (нужно минимум 14)");
            return BtcVolatilityData.builder()
                    .currentAtr(0)
                    .averageAtr(0)
                    .currentDailyRange(0)
                    .averageDailyRange(0)
                    .dailyChangePercent(0)
                    .build();
        }

        // Сортируем свечи по времени (от старых к новым)
        List<Candle> sortedCandles = candles.stream()
                .sorted(Comparator.comparing(Candle::getTimestamp))
                .toList();

        double currentAtr = calculateATR(sortedCandles, 14);
        double averageAtr = calculateATR(sortedCandles, Math.min(sortedCandles.size(), 50));

        double currentDailyRange = calculateDailyRange(sortedCandles);
        double averageDailyRange = calculateAverageDailyRange(sortedCandles, 30);

        double dailyChangePercent = calculateDailyChangePercent(sortedCandles);

        return BtcVolatilityData.builder()
                .currentAtr(currentAtr)
                .averageAtr(averageAtr)
                .currentDailyRange(currentDailyRange)
                .averageDailyRange(averageDailyRange)
                .dailyChangePercent(dailyChangePercent)
                .build();
    }

    /**
     * Расчет ATR (Average True Range)
     */
    private double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            return 0;
        }

        double atrSum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            Candle current = candles.get(i);
            
            double trueRange;
            if (i == 0) {
                // Для первой свечи TR = High - Low
                trueRange = current.getHigh() - current.getLow();
            } else {
                Candle previous = candles.get(i - 1);
                double highLow = current.getHigh() - current.getLow();
                double highClosePrev = Math.abs(current.getHigh() - previous.getClose());
                double lowClosePrev = Math.abs(current.getLow() - previous.getClose());
                
                trueRange = Math.max(highLow, Math.max(highClosePrev, lowClosePrev));
            }
            
            atrSum += trueRange;
        }

        return atrSum / period;
    }

    /**
     * Расчет дневного диапазона (разница между максимумом и минимумом дня)
     */
    private double calculateDailyRange(List<Candle> candles) {
        if (candles.isEmpty()) return 0;
        
        Candle lastCandle = candles.get(candles.size() - 1);
        return lastCandle.getHigh() - lastCandle.getLow();
    }

    /**
     * Расчет среднего дневного диапазона за период
     */
    private double calculateAverageDailyRange(List<Candle> candles, int period) {
        if (candles.size() < period) {
            period = candles.size();
        }

        double rangeSum = 0;
        int count = 0;
        
        for (int i = candles.size() - period; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            rangeSum += (candle.getHigh() - candle.getLow());
            count++;
        }

        return count > 0 ? rangeSum / count : 0;
    }

    /**
     * Расчет дневного изменения в процентах
     */
    private double calculateDailyChangePercent(List<Candle> candles) {
        if (candles.size() < 2) return 0;
        
        Candle current = candles.get(candles.size() - 1);
        Candle previous = candles.get(candles.size() - 2);
        
        if (previous.getClose() == 0) return 0;
        
        return ((current.getClose() - previous.getClose()) / previous.getClose()) * 100;
    }

    /**
     * Проверяет условия для торговли на основе индикаторов
     */
    private boolean checkTradingConditions(Settings settings, BtcVolatilityData data) {
        boolean atrOk = true;
        boolean dailyRangeOk = true;
        boolean dailyChangeOk = true;

        // Проверка ATR
        if (data.averageAtr > 0) {
            double atrRatio = data.currentAtr / data.averageAtr;
            atrOk = atrRatio <= settings.getBtcAtrThresholdMultiplier();
            log.debug("🪙 ATR: текущий={}, средний={}, ratio={}, порог={}, OK={}", 
                    String.format("%.2f", data.currentAtr), String.format("%.2f", data.averageAtr), 
                    String.format("%.2f", atrRatio), String.format("%.2f", settings.getBtcAtrThresholdMultiplier()), atrOk);
        }

        // Проверка дневного диапазона
        if (data.averageDailyRange > 0) {
            double rangeRatio = data.currentDailyRange / data.averageDailyRange;
            dailyRangeOk = rangeRatio <= settings.getBtcDailyRangeMultiplier();
            log.debug("🪙 Дневной диапазон: текущий={}, средний={}, ratio={}, порог={}, OK={}", 
                    String.format("%.2f", data.currentDailyRange), String.format("%.2f", data.averageDailyRange), 
                    String.format("%.2f", rangeRatio), String.format("%.2f", settings.getBtcDailyRangeMultiplier()), dailyRangeOk);
        }

        // Проверка дневного изменения
        double absChange = Math.abs(data.dailyChangePercent);
        dailyChangeOk = absChange <= settings.getMaxBtcDailyChangePercent();
        log.debug("🪙 Дневное изменение: {}%, порог={}%, OK={}", 
                String.format("%.2f", data.dailyChangePercent), String.format("%.2f", settings.getMaxBtcDailyChangePercent()), dailyChangeOk);

        return atrOk && dailyRangeOk && dailyChangeOk;
    }

    /**
     * Проверяет валидность кэша
     */
    private boolean isCacheValid() {
        return lastCheckTime != null && 
               lastCheckResult != null && 
               Duration.between(lastCheckTime, LocalDateTime.now()).compareTo(CACHE_DURATION) < 0;
    }

    /**
     * Кэширует результат проверки
     */
    private void cacheResult(boolean canTrade, double atr, double dailyRange, double dailyChangePercent) {
        this.lastCheckTime = LocalDateTime.now();
        this.lastCheckResult = canTrade;
        this.lastAtr = atr;
        this.lastDailyRange = dailyRange;
        this.lastDailyChangePercent = dailyChangePercent;
    }

    /**
     * Логирует результат анализа
     */
    private void logAnalysisResult(Settings settings, BtcVolatilityData data, boolean canTrade) {
        if (canTrade) {
            log.info("🪙 ✅ BTC анализ: торговля РАЗРЕШЕНА - волатильность в норме");
        } else {
            log.warn("🪙 ⛔ BTC анализ: торговля ЗАБЛОКИРОВАНА - повышенная волатильность");
        }
        
        double atrRatio = data.averageAtr > 0 ? data.currentAtr / data.averageAtr : 0;
        log.info("🪙 📊 ATR: текущий={}, средний={}, ratio={} (порог={})", 
                String.format("%.2f", data.currentAtr), 
                String.format("%.2f", data.averageAtr), 
                String.format("%.2f", atrRatio),
                String.format("%.2f", settings.getBtcAtrThresholdMultiplier()));
        
        double rangeRatio = data.averageDailyRange > 0 ? data.currentDailyRange / data.averageDailyRange : 0;
        log.info("🪙 📊 Дневной диапазон: текущий={}, средний={}, ratio={} (порог={})", 
                String.format("%.2f", data.currentDailyRange), 
                String.format("%.2f", data.averageDailyRange),
                String.format("%.2f", rangeRatio),
                String.format("%.2f", settings.getBtcDailyRangeMultiplier()));
        
        log.info("🪙 📊 Дневное изменение: {}% (порог={}%)", 
                String.format("%.2f", data.dailyChangePercent), 
                String.format("%.2f", settings.getMaxBtcDailyChangePercent()));
    }

    /**
     * Данные анализа волатильности BTC
     */
    private static class BtcVolatilityData {
        final double currentAtr;
        final double averageAtr;
        final double currentDailyRange;
        final double averageDailyRange;
        final double dailyChangePercent;

        private BtcVolatilityData(double currentAtr, double averageAtr, double currentDailyRange, 
                                 double averageDailyRange, double dailyChangePercent) {
            this.currentAtr = currentAtr;
            this.averageAtr = averageAtr;
            this.currentDailyRange = currentDailyRange;
            this.averageDailyRange = averageDailyRange;
            this.dailyChangePercent = dailyChangePercent;
        }

        public static BtcVolatilityDataBuilder builder() {
            return new BtcVolatilityDataBuilder();
        }

        public static class BtcVolatilityDataBuilder {
            private double currentAtr;
            private double averageAtr;
            private double currentDailyRange;
            private double averageDailyRange;
            private double dailyChangePercent;

            public BtcVolatilityDataBuilder currentAtr(double currentAtr) {
                this.currentAtr = currentAtr;
                return this;
            }

            public BtcVolatilityDataBuilder averageAtr(double averageAtr) {
                this.averageAtr = averageAtr;
                return this;
            }

            public BtcVolatilityDataBuilder currentDailyRange(double currentDailyRange) {
                this.currentDailyRange = currentDailyRange;
                return this;
            }

            public BtcVolatilityDataBuilder averageDailyRange(double averageDailyRange) {
                this.averageDailyRange = averageDailyRange;
                return this;
            }

            public BtcVolatilityDataBuilder dailyChangePercent(double dailyChangePercent) {
                this.dailyChangePercent = dailyChangePercent;
                return this;
            }

            public BtcVolatilityData build() {
                return new BtcVolatilityData(currentAtr, averageAtr, currentDailyRange, averageDailyRange, dailyChangePercent);
            }
        }
    }
}