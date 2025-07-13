package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.CandlesService;
import com.example.statarbitrage.core.services.PairDataService;
import com.example.statarbitrage.core.services.SettingsService;
import com.example.statarbitrage.core.services.ZScoreService;
import com.example.statarbitrage.ui.dto.FetchPairsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchPairsProcessor {
    private final PairDataService pairDataService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;

    public List<PairData> fetchPairs(FetchPairsRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 Начинаем поиск пар для торговли...");

        Settings settings = settingsService.getSettings();
        long candlesStartTime = System.currentTimeMillis();
        //todo тут брать все TRADING, маппить в лист тикеров с лонг/шорт и передавать для фильтрации в getApplicableCandlesMap() что бы их исключить
        //todo нужно чтобы были только уникальные монеты чтобы не путать сделки между собой - проще управлять сделками открыл/закрыл и не думаешь что в монете
        //todo часть денег с другого трейда
        List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);

        List<String> tradingTickers = new ArrayList<>();
        tradingPairs.forEach(p -> {
            tradingTickers.add(p.getLongTicker());
            tradingTickers.add(p.getShortTicker());
        });

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(settings, tradingTickers); //todo сюда передаем лист TRADING тикеров
        long candlesEndTime = System.currentTimeMillis();
        log.info("✅ Собрали карту свечей за {}с", String.format("%.2f", (candlesEndTime - candlesStartTime) / 1000.0));
        int count = request.getCountOfPairs() != null ? request.getCountOfPairs() : (int) settings.getUsePairs();
        List<ZScoreData> zScoreDataList = zScoreService.getTopNPairs(settings, candlesMap, count);

        for (int i = 0; i < zScoreDataList.size(); i++) {
            ZScoreData zScoreData = zScoreDataList.get(i);
            ZScoreParam latest = zScoreData.getLastZScoreParam();
            log.info(String.format("%d. Пара: underValuedTicker=%s overValuedTicker=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f", i + 1, zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(), latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));
        }

        List<PairData> topPairs = pairDataService.createPairDataList(zScoreDataList, candlesMap);
        long endTime = System.currentTimeMillis();
        log.info("Создали {} новых PairData", topPairs.size());
        log.info("✅ Поиск пар завершен за {}с", String.format("%.2f", (endTime - startTime) / 1000.0));
        return topPairs;
    }
}
