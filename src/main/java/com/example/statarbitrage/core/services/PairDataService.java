package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.CandlesUtil;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.statarbitrage.common.constant.Constants.EXIT_REASON_MANUALLY;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final ChangesService changesService;
    private final ExitStrategyService exitStrategyService;
    private final PairDataRepository pairDataRepository;

    public List<PairData> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<PairData> result = new ArrayList<>();

        for (ZScoreData zScoreData : top) {
            try {
                List<Candle> underValuedTickerCandles = candlesMap.get(zScoreData.getUndervaluedTicker());
                List<Candle> overValuedTickerCandles = candlesMap.get(zScoreData.getOvervaluedTicker());
                PairData newPairData = createPairData(zScoreData, underValuedTickerCandles, overValuedTickerCandles);
                result.add(newPairData);
            } catch (Exception e) {
                log.error("Ошибка при создании PairData для пары {}/{}: {}",
                        zScoreData.getUndervaluedTicker(),
                        zScoreData.getOvervaluedTicker(),
                        e.getMessage());
            }
        }

        // Сохраняем с обработкой конфликтов
        List<PairData> savedPairs = new ArrayList<>();
        for (PairData pair : result) {
            try {
                save(pair);
                savedPairs.add(pair);
            } catch (RuntimeException e) {
                log.warn("⚠️ Не удалось сохранить пару {}/{}: {} - пропускаем",
                        pair.getLongTicker(), pair.getShortTicker(), e.getMessage());
                // Продолжаем обработку остальных пар
            }
        }

        log.info("✅ Успешно сохранено {}/{} пар", savedPairs.size(), result.size());

        return result;
    }

    private static PairData createPairData(ZScoreData zScoreData, List<Candle> underValuedTickerCandles, List<Candle> overValuedTickerCandles) {
        // Проверяем наличие данных
        if (underValuedTickerCandles == null || underValuedTickerCandles.isEmpty() || overValuedTickerCandles == null || overValuedTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker());
            throw new IllegalArgumentException("Отсутствуют данные свечей");
        }

        PairData pairData = new PairData();

        pairData.setStatus(TradeStatus.SELECTED);

        // Устанавливаем основные параметры
        pairData.setLongTicker(zScoreData.getUndervaluedTicker());
        pairData.setShortTicker(zScoreData.getOvervaluedTicker());

        // Получаем последние параметры
        ZScoreParam latestParam = zScoreData.getLastZScoreParam();

        // Устанавливаем текущие цены
        pairData.setLongTickerCurrentPrice(CandlesUtil.getLastClose(underValuedTickerCandles));
        pairData.setShortTickerCurrentPrice(CandlesUtil.getLastClose(overValuedTickerCandles));

        // Устанавливаем статистические параметры
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        if (pairData.getZScoreEntry() != 0) {
            BigDecimal zScoreChanges = BigDecimal.valueOf(latestParam.getZscore()).subtract(BigDecimal.valueOf(pairData.getZScoreEntry()));
            pairData.setZScoreChanges(zScoreChanges);
        }

        // Добавляем всю историю Z-Score из ZScoreData
        if (zScoreData.getZscoreParams() != null && !zScoreData.getZscoreParams().isEmpty()) {
            // Добавляем всю историю, если она есть
            for (ZScoreParam param : zScoreData.getZscoreParams()) {
                pairData.addZScorePoint(param);
            }
        } else {
            // Если истории нет, добавляем хотя бы текущую точку
            pairData.addZScorePoint(latestParam);
        }

        return pairData;
    }

    @Transactional
    public void update(PairData pairData, ZScoreData zScoreData, List<Candle> longTickerCandles, List<Candle> shortTickerCandles, boolean isCloseManually) {
        // Проверяем наличие данных
        if (longTickerCandles == null || longTickerCandles.isEmpty() || shortTickerCandles == null || shortTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker());
            throw new IllegalArgumentException("Отсутствуют данные свечей");
        }

        double longTickerCurrentPrice = CandlesUtil.getLastClose(longTickerCandles);
        double shortTickerCurrentPrice = CandlesUtil.getLastClose(shortTickerCandles);

        pairData.setLongTickerCurrentPrice(longTickerCurrentPrice);
        pairData.setShortTickerCurrentPrice(shortTickerCurrentPrice);

        ZScoreParam latestParam = zScoreData.getLastZScoreParam();

        //Точки входа
        if (pairData.getStatus() == TradeStatus.SELECTED) {

            pairData.setStatus(TradeStatus.TRADING);

            pairData.setLongTickerEntryPrice(longTickerCurrentPrice);
            pairData.setShortTickerEntryPrice(shortTickerCurrentPrice);

            pairData.setZScoreEntry(latestParam.getZscore());
            pairData.setCorrelationEntry(latestParam.getCorrelation());
            pairData.setAdfPvalueEntry(latestParam.getAdfpvalue());
            pairData.setPValueEntry(latestParam.getPvalue());
            pairData.setMeanEntry(latestParam.getMean());
            pairData.setStdEntry(latestParam.getStd());
            pairData.setSpreadEntry(latestParam.getSpread());
            pairData.setAlphaEntry(latestParam.getAlpha());
            pairData.setBetaEntry(latestParam.getBeta());

            // Ставим время открытия по long-свечке
            pairData.setEntryTime(latestParam.getTimestamp());

            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z = {}",
                    pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
                    pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
                    pairData.getZScoreEntry());
        }

        //updateCurrentCointParams
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        changesService.calculateAndAdd(pairData);

        // Добавляем новые точки в историю Z-Score при каждом обновлении
        if (zScoreData.getZscoreParams() != null && !zScoreData.getZscoreParams().isEmpty()) {
            // Добавляем всю новую историю из ZScoreData
            for (ZScoreParam param : zScoreData.getZscoreParams()) {
                pairData.addZScorePoint(param);
            }
        } else {
            // Если новой истории нет, добавляем хотя бы текущую точку
            pairData.addZScorePoint(latestParam);
        }

        String exitReason = exitStrategyService.getExitReason(pairData);
        if (exitReason != null) {
            pairData.setExitReason(exitReason);
            pairData.setStatus(TradeStatus.CLOSED);
        }

        //после всех обновлений профита закрываем если нужно
        if (isCloseManually) {
            pairData.setStatus(TradeStatus.CLOSED);
            pairData.setExitReason(EXIT_REASON_MANUALLY);
        }

        save(pairData);
    }

    @Transactional
    public void save(PairData pairData) {
        saveWithRetry(pairData, 10);
    }

    private void saveWithRetry(PairData pairData, int maxRetries) {
        int attempts = 0;
        PairData currentEntity = pairData;

        while (attempts < maxRetries) {
            try {
                log.debug("💾 Попытка сохранения PairData #{} (попытка {}/{}) версия: {}",
                        currentEntity.getId(), attempts + 1, maxRetries, currentEntity.getVersion());

                PairData savedEntity = pairDataRepository.save(currentEntity);
                log.debug("✅ Успешно сохранено PairData #{} версия: {}",
                        savedEntity.getId(), savedEntity.getVersion());
                return; // Успешное сохранение

            } catch (OptimisticLockingFailureException e) {
                attempts++;
                log.warn("⚠️ Конфликт при сохранении PairData #{} (попытка {}/{}) для пары {}/{}: {}",
                        currentEntity.getId(), attempts, maxRetries,
                        currentEntity.getLongTicker(), currentEntity.getShortTicker(), e.getMessage());

                if (attempts >= maxRetries) {
                    log.error("❌ Не удалось сохранить PairData #{} после {} попыток для пары {}/{}",
                            currentEntity.getId(), maxRetries,
                            currentEntity.getLongTicker(), currentEntity.getShortTicker());
                    throw new RuntimeException("Не удалось сохранить данные пары после " + maxRetries + " попыток", e);
                }

                // Перезагружаем актуальную версию из БД и создаем новый entity для следующей попытки
                try {
                    Thread.sleep(1000 + (attempts * 1000L)); // Exponential backoff: 50ms, 75ms, 100ms

                    if (currentEntity.getId() != null) {
                        // Перезагружаем свежие данные из БД
                        Optional<PairData> freshDataOpt = pairDataRepository.findById(currentEntity.getId());
                        if (freshDataOpt.isPresent()) {
                            PairData freshData = freshDataOpt.get();

                            // Создаем новый entity с актуальной версией и нашими изменениями
                            currentEntity = mergeWithFreshData(currentEntity, freshData);

                            log.info("🔄 Обновлена версия для попытки #{}: старая={}, новая={}",
                                    attempts + 1, freshData.getVersion(), currentEntity.getVersion());
                        } else {
                            log.warn("❌ PairData #{} была удалена из БД другим процессом для пары {}/{}. Прекращаем попытки сохранения.",
                                    currentEntity.getId(), currentEntity.getLongTicker(), currentEntity.getShortTicker());
                            // Помечаем транзакцию для rollback и выбрасываем исключение
                            throw new RuntimeException("Запись была удалена другим процессом во время сохранения");
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Прерван поток при повторной попытке сохранения", ie);
                }
            }
        }
    }

    /**
     * Объединяет изменения из detached entity с актуальными данными из БД
     */
    private PairData mergeWithFreshData(PairData modifiedEntity, PairData freshEntity) {
        // Копируем все наши изменения в свежую entity с актуальной версией
        freshEntity.setStatus(modifiedEntity.getStatus());
        freshEntity.setLongTicker(modifiedEntity.getLongTicker());
        freshEntity.setShortTicker(modifiedEntity.getShortTicker());

        // Цены
        freshEntity.setLongTickerEntryPrice(modifiedEntity.getLongTickerEntryPrice());
        freshEntity.setLongTickerCurrentPrice(modifiedEntity.getLongTickerCurrentPrice());
        freshEntity.setShortTickerEntryPrice(modifiedEntity.getShortTickerEntryPrice());
        freshEntity.setShortTickerCurrentPrice(modifiedEntity.getShortTickerCurrentPrice());

        // Статистические параметры входа
        freshEntity.setZScoreEntry(modifiedEntity.getZScoreEntry());
        freshEntity.setCorrelationEntry(modifiedEntity.getCorrelationEntry());
        freshEntity.setAdfPvalueEntry(modifiedEntity.getAdfPvalueEntry());
        freshEntity.setPValueEntry(modifiedEntity.getPValueEntry());
        freshEntity.setMeanEntry(modifiedEntity.getMeanEntry());
        freshEntity.setStdEntry(modifiedEntity.getStdEntry());
        freshEntity.setSpreadEntry(modifiedEntity.getSpreadEntry());
        freshEntity.setAlphaEntry(modifiedEntity.getAlphaEntry());
        freshEntity.setBetaEntry(modifiedEntity.getBetaEntry());

        // Текущие статистические параметры
        freshEntity.setZScoreCurrent(modifiedEntity.getZScoreCurrent());
        freshEntity.setCorrelationCurrent(modifiedEntity.getCorrelationCurrent());
        freshEntity.setAdfPvalueCurrent(modifiedEntity.getAdfPvalueCurrent());
        freshEntity.setPValueCurrent(modifiedEntity.getPValueCurrent());
        freshEntity.setMeanCurrent(modifiedEntity.getMeanCurrent());
        freshEntity.setStdCurrent(modifiedEntity.getStdCurrent());
        freshEntity.setSpreadCurrent(modifiedEntity.getSpreadCurrent());
        freshEntity.setAlphaCurrent(modifiedEntity.getAlphaCurrent());
        freshEntity.setBetaCurrent(modifiedEntity.getBetaCurrent());

        // Изменения и статистика
        freshEntity.setZScoreChanges(modifiedEntity.getZScoreChanges());
        freshEntity.setLongChanges(modifiedEntity.getLongChanges());
        freshEntity.setShortChanges(modifiedEntity.getShortChanges());
        freshEntity.setProfitChanges(modifiedEntity.getProfitChanges());

        // Времена и метрики
        freshEntity.setEntryTime(modifiedEntity.getEntryTime());
        freshEntity.setUpdatedTime(modifiedEntity.getUpdatedTime());
        freshEntity.setTimestamp(modifiedEntity.getTimestamp());

        // Минимумы и максимумы
        freshEntity.setMaxProfitRounded(modifiedEntity.getMaxProfitRounded());
        freshEntity.setMinProfitRounded(modifiedEntity.getMinProfitRounded());
        freshEntity.setTimeInMinutesSinceEntryToMin(modifiedEntity.getTimeInMinutesSinceEntryToMin());
        freshEntity.setTimeInMinutesSinceEntryToMax(modifiedEntity.getTimeInMinutesSinceEntryToMax());

        freshEntity.setMaxZ(modifiedEntity.getMaxZ());
        freshEntity.setMinZ(modifiedEntity.getMinZ());
        freshEntity.setMaxLong(modifiedEntity.getMaxLong());
        freshEntity.setMinLong(modifiedEntity.getMinLong());
        freshEntity.setMaxShort(modifiedEntity.getMaxShort());
        freshEntity.setMinShort(modifiedEntity.getMinShort());
        freshEntity.setMaxCorr(modifiedEntity.getMaxCorr());
        freshEntity.setMinCorr(modifiedEntity.getMinCorr());

        // Причина выхода
        freshEntity.setExitReason(modifiedEntity.getExitReason());

        // История Z-Score (только если изменилась)
        if (modifiedEntity.getZScoreHistoryJson() != null) {
            freshEntity.setZScoreHistoryJson(modifiedEntity.getZScoreHistoryJson());
        }

        return freshEntity;
    }

    public List<PairData> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByEntryTimeDesc(status);
    }

    public List<PairData> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByUpdatedTimeDesc(status);
    }

    public int deleteAllByStatus(TradeStatus status) {
        return pairDataRepository.deleteAllByStatus(status);
    }

    public void delete(PairData pairData) {
        pairDataRepository.delete(pairData);
    }

    public void excludeExistingTradingPairs(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) return;

        // Получаем список уже торгующихся пар
        List<PairData> tradingPairs = findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);

        // Собираем множество идентификаторов пар в торговле (например, "BTC-USDT-ETH-USDT")
        Set<String> tradingSet = tradingPairs.stream()
                .map(pair -> buildKey(pair.getLongTicker(), pair.getShortTicker()))
                .collect(Collectors.toSet());

        // Удаляем из списка те пары, которые уже торгуются
        zScoreDataList.removeIf(z -> {
            String key = buildKey(z.getUndervaluedTicker(), z.getOvervaluedTicker());
            return tradingSet.contains(key);
        });
    }

    // Приватный метод для создания уникального ключа пары, независимо от порядка
    private String buildKey(String ticker1, String ticker2) {
        List<String> sorted = Arrays.asList(ticker1, ticker2);
        Collections.sort(sorted);
        return sorted.get(0) + "-" + sorted.get(1);
    }

    public BigDecimal getUnrealizedProfitTotal() {
        List<PairData> tradingPairs = findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        return tradingPairs.stream()
                .map(PairData::getProfitChanges)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
