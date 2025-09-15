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
            log.info("💾 ТОЛЬКО ДОБАВЛЯЕМ: {} новых свечей для {}/{}/{}",
                    candles.size(), ticker, timeframe, exchange);

            // ИСПРАВЛЕНО: НИКОГДА НЕ УДАЛЯЕМ! Только добавляем уникальные свечи
            // Уникальный индекс предотвратит дубли автоматически

            // Сохраняем порциями для экономии памяти
            int batchSize = 1000;
            int totalSaved = 0;

            for (int i = 0; i < candles.size(); i += batchSize) {
                List<Candle> batch = candles.subList(i, Math.min(i + batchSize, candles.size()));

                List<CachedCandle> cachedCandles = batch.stream()
                        .map(candle -> CachedCandle.fromCandle(candle, ticker, timeframe, exchange))
                        .collect(Collectors.toList());

                try {
                    cachedCandleRepository.saveAll(cachedCandles);
                    totalSaved += cachedCandles.size();
                } catch (Exception e) {
                    // Игнорируем ошибки дубликатов - это нормально с уникальным индексом
                    if (e.getMessage().contains("duplicate") || e.getMessage().contains("unique")) {
                        log.info("⚠️ ДУБЛИ: Проигнорировано {} дублированных свечей в батче", cachedCandles.size());
                        // Считаем как сохраненные для статистики (они уже есть в БД)
                        totalSaved += cachedCandles.size(); 
                    } else {
                        throw e; // Другие ошибки перебрасываем
                    }
                }

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
     * УПРОЩЕНО: Только добавляем новые свечи, дубли предотвращает уникальный индекс
     */
    @Transactional
    public void updateCandlesInCache(String ticker, String timeframe, String exchange,
                                     List<Candle> candles, long fromTimestamp) {
        try {
            log.info("🔄 ТОЛЬКО ДОБАВЛЕНИЕ: обновление свечей для {}/{}/{} с timestamp {}",
                    ticker, timeframe, exchange, fromTimestamp);

            // УПРОЩЕНО: Только добавляем новые свечи, уникальный индекс предотвратит дубли
            List<CachedCandle> newCandles = candles.stream()
                    .filter(candle -> candle.getTimestamp() >= fromTimestamp)
                    .map(candle -> CachedCandle.fromCandle(candle, ticker, timeframe, exchange))
                    .collect(Collectors.toList());

            if (!newCandles.isEmpty()) {
                // Сохраняем батчами для больших объемов
                int batchSize = 1000;
                for (int i = 0; i < newCandles.size(); i += batchSize) {
                    List<CachedCandle> batch = newCandles.subList(i, Math.min(i + batchSize, newCandles.size()));
                    try {
                        cachedCandleRepository.saveAll(batch);
                    } catch (Exception e) {
                        // Игнорируем ошибки дубликатов - это нормально
                        if (e.getMessage().contains("duplicate") || e.getMessage().contains("unique")) {
                            log.info("⚠️ ДУБЛИ: Проигнорировано {} дублированных свечей в батче", batch.size());
                        } else {
                            throw e; // Другие ошибки перебрасываем
                        }
                    }
                }
                log.info("💾 ТРАНЗАКЦИЯ: Попытка добавить {} новых свечей", newCandles.size());
            }

            log.info("🔄 ТРАНЗАКЦИЯ: Обновление для {}/{} завершено", ticker, timeframe);

        } catch (Exception e) {
            log.error("❌ ТРАНЗАКЦИЯ: Ошибка обновления свечей в кэше для {}: {}", ticker, e.getMessage());
            throw e; // Перебрасываем для rollback
        }
    }
}