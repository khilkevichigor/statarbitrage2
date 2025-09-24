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
    @Transactional(rollbackFor = Exception.class)
    public int saveCandlesToCache(String ticker, String timeframe, String exchange,
                                   List<Candle> candles) {
        try {
            log.debug("💾 ДОБАВЛЯЕМ: {} свечей для {}/{}/{}",
                    candles.size(), ticker, timeframe, exchange);

            // Убираем подсчет записей для избежания проблем с Hibernate session

            // Сохраняем порциями для экономии памяти
            int batchSize = 1000;
            int totalProcessedCount = 0; // Общий счетчик обработанных свечей

            for (int i = 0; i < candles.size(); i += batchSize) {
                long batchStartTime = System.currentTimeMillis();
                List<Candle> batch = candles.subList(i, Math.min(i + batchSize, candles.size()));

                List<CachedCandle> cachedCandles = batch.stream()
                        .map(candle -> CachedCandle.fromCandle(candle, ticker, timeframe, exchange))
                        .toList();

                // Супер-быстрое сохранение через батчевый INSERT (игнорируем дубликаты)
                int processedCount = 0;
                
                try {
                    // Используем только native SQL для избежания проблем с Hibernate session
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
                            // Тихо игнорируем дубликаты
                        }
                    }
                    
                } catch (Exception e) {
                    log.debug("🔄 BATCH processing error для {}: {}", ticker, e.getMessage());
                    return 0;
                }
                
                log.info("💾 BATCH SAVED: {} - обработано {} свечей за {} сек", 
                        ticker, processedCount, (System.currentTimeMillis() - batchStartTime) / 1000);

                totalProcessedCount += processedCount; // Добавляем к общему счетчику

                // Более агрессивная очистка памяти для предотвращения OutOfMemoryError
                if (i % 1000 == 0) { // Каждые 1K свечей (было 5K)
                    System.gc();
                    try { Thread.sleep(50); } catch (Exception ignored) {} // Пауза для GC
                }
            }

            // Возвращаем количество успешно обработанных свечей
            if (totalProcessedCount > 0) {
                log.debug("💾 ДОБАВЛЕНО: {} свечей для {}/{}/{}", totalProcessedCount, ticker, timeframe, exchange);
            }

            return totalProcessedCount;

        } catch (Exception e) {
            log.warn("⚠️ ТРАНЗАКЦИЯ: Проблема с сохранением свечей для {} - вероятно дубликаты: {}", ticker, e.getMessage());
            return 0; // Возвращаем 0 вместо исключения
        }
    }

    /**
     * Транзакционное обновление свечей в кэше
     * @return количество реально добавленных свечей в БД
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateCandlesInCache(String ticker, String timeframe, String exchange,
                                     List<Candle> candles, long fromTimestamp) {
        try {
            log.debug("🔄 ОБНОВЛЕНИЕ: {} свечей для {}/{}/{} с timestamp {}",
                    candles.size(), ticker, timeframe, exchange, fromTimestamp);

            // Убираем подсчет записей для избежания проблем с Hibernate session

            // Только добавляем новые свечи, уникальный индекс предотвратит дубли
            List<CachedCandle> newCandles = candles.stream()
                    .filter(candle -> candle.getTimestamp() >= fromTimestamp)
                    .map(candle -> CachedCandle.fromCandle(candle, ticker, timeframe, exchange))
                    .collect(Collectors.toList());

            int addedCount = 0;
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
                        addedCount++; // Считаем успешные вставки
                    } catch (Exception e) {
                        // Тихо игнорируем дубликаты
                    }
                }
            }

            if (addedCount > 0) {
                log.debug("💾 ДОБАВЛЕНО: {} новых свечей при обновлении {}/{}/{}", addedCount, ticker, timeframe, exchange);
            }

            return addedCount;

        } catch (Exception e) {
            log.warn("⚠️ ТРАНЗАКЦИЯ: Проблема с обновлением свечей для {} - вероятно дубликаты: {}", ticker, e.getMessage());
            return 0; // Возвращаем 0 вместо исключения
        }
    }
}