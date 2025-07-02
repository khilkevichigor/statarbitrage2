package com.example.statarbitrage.service;

import com.example.statarbitrage.dto.TradingPair;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Современная версия PairDataService работающая с TradingPair
 * Заменяет старый PairDataService с ZScoreData
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModernPairDataService {

    /**
     * Создание PairData из TradingPair
     */
    public PairData createPairData(TradingPair tradingPair, Map<String, List<Candle>> candlesMap) {
        log.info("📊 Создание PairData из TradingPair: {}", tradingPair.getDisplayName());
        
        PairData pairData = new PairData();
        
        // Основные торговые данные
        pairData.setLongTicker(tradingPair.getBuyTicker());
        pairData.setShortTicker(tradingPair.getSellTicker());
        
        // Статистические параметры
        pairData.setZScoreEntry(tradingPair.getZscore() != null ? tradingPair.getZscore() : 0.0);
        pairData.setZScoreCurrent(tradingPair.getZscore() != null ? tradingPair.getZscore() : 0.0);
        pairData.setCorrelation(tradingPair.getCorrelation() != null ? tradingPair.getCorrelation() : 0.0);
        pairData.setPvalue(tradingPair.getPValue() != null ? tradingPair.getPValue() : 0.0);
        pairData.setAdfpvalue(tradingPair.getAdfpvalue() != null ? tradingPair.getAdfpvalue() : 0.0);
        
        // Коэффициенты регрессии
        pairData.setAlpha(tradingPair.getAlpha() != null ? tradingPair.getAlpha() : 0.0);
        pairData.setBeta(tradingPair.getBeta() != null ? tradingPair.getBeta() : 0.0);
        
        // Статистика спреда
        pairData.setSpread(tradingPair.getSpread() != null ? tradingPair.getSpread() : 0.0);
        pairData.setMean(tradingPair.getMean() != null ? tradingPair.getMean() : 0.0);
        pairData.setStd(tradingPair.getStd() != null ? tradingPair.getStd() : 0.0);
        
        // Текущие цены
        setCurrentPrices(pairData, candlesMap);
        
        // Статус и время
        pairData.setStatus(TradeStatus.FOUND);
        pairData.setTimestamp(tradingPair.getTimestamp() != null ? tradingPair.getTimestamp() : System.currentTimeMillis());
        
        log.info("✅ Создан PairData: {} | {}", pairData.getLongTicker(), pairData.getShortTicker());
        return pairData;
    }

    /**
     * Создание списка PairData из списка TradingPair
     */
    public List<PairData> createPairDataList(List<TradingPair> tradingPairs, Map<String, List<Candle>> candlesMap) {
        log.info("📊 Создание списка PairData из {} TradingPair", tradingPairs.size());
        
        return tradingPairs.stream()
                .map(pair -> createPairData(pair, candlesMap))
                .toList();
    }

    /**
     * Обновление PairData новыми данными из TradingPair
     */
    public void updatePairData(PairData pairData, TradingPair tradingPair, Map<String, List<Candle>> candlesMap) {
        log.info("🔄 Обновление PairData: {} с новыми данными", pairData.getLongTicker());
        
        // Обновляем текущий Z-score
        if (tradingPair.getZscore() != null) {
            pairData.setZScoreCurrent(tradingPair.getZscore());
        }
        
        // Обновляем статистические параметры
        if (tradingPair.getCorrelation() != null) {
            pairData.setCorrelation(tradingPair.getCorrelation());
        }
        if (tradingPair.getPValue() != null) {
            pairData.setPvalue(tradingPair.getPValue());
        }
        if (tradingPair.getAdfpvalue() != null) {
            pairData.setAdfpvalue(tradingPair.getAdfpvalue());
        }
        
        // Обновляем параметры спреда
        if (tradingPair.getSpread() != null) {
            pairData.setSpread(tradingPair.getSpread());
        }
        if (tradingPair.getMean() != null) {
            pairData.setMean(tradingPair.getMean());
        }
        if (tradingPair.getStd() != null) {
            pairData.setStd(tradingPair.getStd());
        }
        
        // Обновляем текущие цены
        setCurrentPrices(pairData, candlesMap);
        
        // Обновляем timestamp
        pairData.setTimestamp(System.currentTimeMillis());
        
        // Логика торгового статуса
        updateTradingStatus(pairData);
        
        log.info("✅ PairData обновлен: Z={:.2f}, Status={}", 
            pairData.getZScoreCurrent(), pairData.getStatus());
    }

    /**
     * Исключение существующих торговых пар
     */
    public List<TradingPair> excludeExistingTradingPairs(List<TradingPair> newPairs, List<PairData> existingPairs) {
        log.info("🔍 Исключение {} существующих пар из {} новых", existingPairs.size(), newPairs.size());
        
        List<TradingPair> filtered = newPairs.stream()
                .filter(newPair -> existingPairs.stream()
                        .noneMatch(existing -> 
                            isSamePair(newPair.getBuyTicker(), newPair.getSellTicker(),
                                     existing.getLongTicker(), existing.getShortTicker())))
                .toList();
        
        log.info("✅ Остается {} уникальных пар после фильтрации", filtered.size());
        return filtered;
    }

    /**
     * Установка текущих цен из данных свечей
     */
    private void setCurrentPrices(PairData pairData, Map<String, List<Candle>> candlesMap) {
        // Получаем последние цены для long тикера
        List<Candle> longCandles = candlesMap.get(pairData.getLongTicker());
        if (longCandles != null && !longCandles.isEmpty()) {
            double longPrice = longCandles.get(longCandles.size() - 1).getClose();
            pairData.setLongTickerCurrentPrice(longPrice);
            
            // Устанавливаем цену входа, если еще не установлена
            if (pairData.getLongTickerEntryPrice() == 0.0) {
                pairData.setLongTickerEntryPrice(longPrice);
            }
        }
        
        // Получаем последние цены для short тикера
        List<Candle> shortCandles = candlesMap.get(pairData.getShortTicker());
        if (shortCandles != null && !shortCandles.isEmpty()) {
            double shortPrice = shortCandles.get(shortCandles.size() - 1).getClose();
            pairData.setShortTickerCurrentPrice(shortPrice);
            
            // Устанавливаем цену входа, если еще не установлена
            if (pairData.getShortTickerEntryPrice() == 0.0) {
                pairData.setShortTickerEntryPrice(shortPrice);
            }
        }
    }

    /**
     * Обновление торгового статуса на основе текущего состояния
     */
    private void updateTradingStatus(PairData pairData) {
        TradeStatus currentStatus = pairData.getStatus();
        
        // Логика перехода статусов
        if (currentStatus == TradeStatus.FOUND || currentStatus == TradeStatus.SELECTED) {
            // Если найдена хорошая пара, переводим в SELECTED
            if (Math.abs(pairData.getZScoreCurrent()) >= 2.0) {
                pairData.setStatus(TradeStatus.SELECTED);
                log.info("🎯 Пара {} переведена в статус SELECTED (Z={:.2f})", 
                    pairData.getLongTicker(), pairData.getZScoreCurrent());
            }
        }
        
        if (currentStatus == TradeStatus.SELECTED) {
            // Если пара выбрана и есть точки входа, переводим в TRADING
            if (pairData.getLongTickerEntryPrice() > 0 && pairData.getShortTickerEntryPrice() > 0) {
                pairData.setStatus(TradeStatus.TRADING);
                pairData.setZScoreEntry(pairData.getZScoreCurrent());
                
                log.info("💹 Пара {} начала торговлю (Entry Z={:.2f})", 
                    pairData.getLongTicker(), pairData.getZScoreEntry());
            }
        }
    }

    /**
     * Проверка, что это одна и та же пара (с учетом возможной инверсии)
     */
    private boolean isSamePair(String buy1, String sell1, String buy2, String sell2) {
        return (buy1.equals(buy2) && sell1.equals(sell2)) ||
               (buy1.equals(sell2) && sell1.equals(buy2));
    }

    /**
     * Конвертация PairData обратно в TradingPair (для совместимости)
     */
    public TradingPair convertToTradingPair(PairData pairData) {
        return TradingPair.builder()
                .buyTicker(pairData.getLongTicker())
                .sellTicker(pairData.getShortTicker())
                .zscore(pairData.getZScoreCurrent())
                .correlation(pairData.getCorrelation())
                .pValue(pairData.getPvalue())
                .adfpvalue(pairData.getAdfpvalue())
                .alpha(pairData.getAlpha())
                .beta(pairData.getBeta())
                .spread(pairData.getSpread())
                .mean(pairData.getMean())
                .std(pairData.getStd())
                .timestamp(pairData.getTimestamp())
                .build();
    }
}