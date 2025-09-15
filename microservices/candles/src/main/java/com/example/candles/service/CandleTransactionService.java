package com.example.candles.service;

import com.example.candles.repositories.CachedCandleRepository;
import com.example.shared.dto.Candle;
import com.example.shared.models.CachedCandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Отдельный сервис для транзакционных операций с кэшем
 * Решает проблему self-invocation в CandleCacheService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CandleTransactionService {

    private final CachedCandleRepository cachedCandleRepository;

    /**
     * Транзакционное сохранение свечей в кэш
     */
    @Transactional
    public void saveCandlesToCache(String ticker, String timeframe, String exchange,
                                   List<Candle> candles) {
        try {
            log.debug("💾 Транзакционное сохранение {} свечей для {}/{}/{}",
                    candles.size(), ticker, timeframe, exchange);

            // Удаляем существующие свечи для этого тикера/таймфрейма
            cachedCandleRepository.deleteByTickerTimeframeExchange(ticker, timeframe, exchange);

            // Сохраняем порциями для экономии памяти
            int batchSize = 1000;
            int totalSaved = 0;

            for (int i = 0; i < candles.size(); i += batchSize) {
                List<Candle> batch = candles.subList(i, Math.min(i + batchSize, candles.size()));

                List<CachedCandle> cachedCandles = batch.stream()
                        .map(candle -> CachedCandle.fromCandle(candle, ticker, timeframe, exchange))
                        .collect(Collectors.toList());

                cachedCandleRepository.saveAll(cachedCandles);
                totalSaved += cachedCandles.size();

                // Принудительно очищаем память после каждого батча
                if (i % 5000 == 0) { // Каждые 5K свечей
                    System.gc();
                }
            }

            log.info("💾 ТРАНЗАКЦИЯ: Сохранено {} свечей для {}/{}/{} (батчами по {})",
                    totalSaved, ticker, timeframe, exchange, batchSize);

        } catch (Exception e) {
            log.error("❌ ТРАНЗАКЦИЯ: Ошибка сохранения свечей в кэш для {}: {}", ticker, e.getMessage(), e);
            throw e; // Перебрасываем для rollback
        }
    }

    /**
     * Транзакционное обновление свечей в кэше
     */
    @Transactional
    public void updateCandlesInCache(String ticker, String timeframe, String exchange,
                                     List<Candle> candles, long fromTimestamp) {
        try {
            log.debug("🔄 Транзакционное обновление свечей для {}/{}/{} с timestamp {}",
                    ticker, timeframe, exchange, fromTimestamp);

            // Удаляем только свечи начиная с fromTimestamp
            List<CachedCandle> existingCandles = cachedCandleRepository
                    .findByTickerAndTimeframeAndExchangeOrderByTimestampAsc(ticker, timeframe, exchange);

            List<CachedCandle> toDelete = existingCandles.stream()
                    .filter(cc -> cc.getTimestamp() >= fromTimestamp)
                    .collect(Collectors.toList());

            if (!toDelete.isEmpty()) {
                cachedCandleRepository.deleteAll(toDelete);
                log.debug("🗑️ ТРАНЗАКЦИЯ: Удалено {} старых свечей", toDelete.size());
            }

            // Добавляем новые свечи
            List<CachedCandle> newCandles = candles.stream()
                    .filter(candle -> candle.getTimestamp() >= fromTimestamp)
                    .map(candle -> CachedCandle.fromCandle(candle, ticker, timeframe, exchange))
                    .collect(Collectors.toList());

            if (!newCandles.isEmpty()) {
                cachedCandleRepository.saveAll(newCandles);
                log.debug("💾 ТРАНЗАКЦИЯ: Добавлено {} новых свечей", newCandles.size());
            }

            log.info("🔄 ТРАНЗАКЦИЯ: Обновлено {} свечей для {}/{}", newCandles.size(), ticker, timeframe);

        } catch (Exception e) {
            log.error("❌ ТРАНЗАКЦИЯ: Ошибка обновления свечей в кэше для {}: {}", ticker, e.getMessage());
            throw e; // Перебрасываем для rollback
        }
    }
}