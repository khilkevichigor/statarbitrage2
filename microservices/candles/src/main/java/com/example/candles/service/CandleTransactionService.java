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
     * @return количество реально добавленных свечей в БД
     */
    @Transactional
    public int saveCandlesToCache(String ticker, String timeframe, String exchange,
                                   List<Candle> candles) {
        try {
            log.debug("💾 ДОБАВЛЯЕМ: {} свечей для {}/{}/{}",
                    candles.size(), ticker, timeframe, exchange);

            // Получаем количество записей до операции
            long countBefore = cachedCandleRepository.countByTickerTimeframeExchangeSimple(ticker, timeframe, exchange);

            // Сохраняем порциями для экономии памяти
            int batchSize = 1000;

            for (int i = 0; i < candles.size(); i += batchSize) {
                long batchStartTime = System.currentTimeMillis();
                List<Candle> batch = candles.subList(i, Math.min(i + batchSize, candles.size()));

                List<CachedCandle> cachedCandles = batch.stream()
                        .map(candle -> CachedCandle.fromCandle(candle, ticker, timeframe, exchange))
                        .toList();

                // Супер-быстрое сохранение через батчевый INSERT (игнорируем дубликаты)
                int processedCount = 0;
                
                try {
                    // Используем стандартный saveAll для батчевой вставки
                    cachedCandleRepository.saveAll(cachedCandles);
                    processedCount = cachedCandles.size();
                    
                } catch (Exception e) {
                    log.warn("⚠️ BATCH FAILED: {} - падаем на поштучные вставки: {}", ticker, e.getMessage());
                    
                    // Fallback: поштучные вставки если батчевая не сработала
                    for (CachedCandle cachedCandle : cachedCandles) {
                        try {
                            cachedCandleRepository.insertIgnoreDuplicates(
                                    cachedCandle.getTicker(),
                                    cachedCandle.getTimeframe(), 
                                    cachedCandle.getExchange(),
                                    cachedCandle.getTimestamp(),
                                    cachedCandle.getOpenPrice(),
                                    cachedCandle.getHighPrice(),
                                    cachedCandle.getLowPrice(),
                                    cachedCandle.getClosePrice(),
                                    cachedCandle.getVolume(),
                                    cachedCandle.getIsValid()
                            );
                            processedCount++;
                            
                        } catch (Exception ex) {
                            log.debug("🔄 SKIP: {} timestamp={} - вероятно дубликат", 
                                    ticker, cachedCandle.getTimestamp());
                        }
                    }
                }
                
                log.info("💾 BATCH SAVED: {} - обработано {} свечей за {} сек", 
                        ticker, processedCount, (System.currentTimeMillis() - batchStartTime) / 1000);

                // Более агрессивная очистка памяти для предотвращения OutOfMemoryError
                if (i % 1000 == 0) { // Каждые 1K свечей (было 5K)
                    System.gc();
                    try { Thread.sleep(50); } catch (Exception ignored) {} // Пауза для GC
                }
            }

            // Получаем количество записей после операции
            long countAfter = cachedCandleRepository.countByTickerTimeframeExchangeSimple(ticker, timeframe, exchange);
            int reallyAdded = (int)(countAfter - countBefore);

            if (reallyAdded > 0) {
                log.debug("💾 ДОБАВЛЕНО: {} новых свечей для {}/{}/{}", reallyAdded, ticker, timeframe, exchange);
            }

            return reallyAdded;

        } catch (Exception e) {
            log.error("❌ ТРАНЗАКЦИЯ: Ошибка сохранения свечей в кэш для {}: {}", ticker, e.getMessage(), e);
            throw e; // Перебрасываем для rollback
        }
    }

    /**
     * Транзакционное обновление свечей в кэше
     * @return количество реально добавленных свечей в БД
     */
    @Transactional
    public int updateCandlesInCache(String ticker, String timeframe, String exchange,
                                     List<Candle> candles, long fromTimestamp) {
        try {
            log.debug("🔄 ОБНОВЛЕНИЕ: {} свечей для {}/{}/{} с timestamp {}",
                    candles.size(), ticker, timeframe, exchange, fromTimestamp);

            // Получаем количество записей до операции
            long countBefore = cachedCandleRepository.countByTickerTimeframeExchangeSimple(ticker, timeframe, exchange);

            // Только добавляем новые свечи, уникальный индекс предотвратит дубли
            List<CachedCandle> newCandles = candles.stream()
                    .filter(candle -> candle.getTimestamp() >= fromTimestamp)
                    .map(candle -> CachedCandle.fromCandle(candle, ticker, timeframe, exchange))
                    .collect(Collectors.toList());

            if (!newCandles.isEmpty()) {
                // Используем INSERT ... ON CONFLICT DO NOTHING для быстрого обновления
                for (CachedCandle cachedCandle : newCandles) {
                    try {
                        cachedCandleRepository.insertIgnoreDuplicates(
                                cachedCandle.getTicker(),
                                cachedCandle.getTimeframe(), 
                                cachedCandle.getExchange(),
                                cachedCandle.getTimestamp(),
                                cachedCandle.getOpenPrice(),
                                cachedCandle.getHighPrice(),
                                cachedCandle.getLowPrice(),
                                cachedCandle.getClosePrice(),
                                cachedCandle.getVolume(),
                                cachedCandle.getIsValid()
                        );
                    } catch (Exception e) {
                        log.warn("❌ ОШИБКА: Не удалось обновить свечу для {}: {}", ticker, e.getMessage());
                        // Продолжаем со следующей свечей
                    }
                }
            }

            // Получаем количество записей после операции
            long countAfter = cachedCandleRepository.countByTickerTimeframeExchangeSimple(ticker, timeframe, exchange);
            int reallyAdded = (int)(countAfter - countBefore);

            if (reallyAdded > 0) {
                log.debug("💾 ДОБАВЛЕНО: {} новых свечей при обновлении {}/{}/{}", reallyAdded, ticker, timeframe, exchange);
            }

            return reallyAdded;

        } catch (Exception e) {
            log.error("❌ ТРАНЗАКЦИЯ: Ошибка обновления свечей в кэше для {}: {}", ticker, e.getMessage());
            throw e; // Перебрасываем для rollback
        }
    }
}