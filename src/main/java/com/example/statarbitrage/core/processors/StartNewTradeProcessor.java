package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.services.*;
import com.example.statarbitrage.trading.model.CloseArbitragePairResult;
import com.example.statarbitrage.trading.model.OpenArbitragePairResult;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.example.statarbitrage.trading.services.TradingProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartNewTradeProcessor {
    private final PairDataService pairDataService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;
    private final TradeLogService tradeLogService;
    private final ZScoreService zScoreService;
    private final ValidateService validateService;
    private final TradingIntegrationService tradingIntegrationService;
    private final TradingProviderFactory tradingProviderFactory;
    private final ChangesService changesService;
    private final ExitStrategyService exitStrategyService;

    public PairData startNewTrade(PairData pairData) {
        boolean isVirtual = tradingProviderFactory.getCurrentProvider().getProviderType().isVirtual();
        if (isVirtual) {
            return startNewVirtualTrade(pairData);
        } else {
            return startNewRealTrade(pairData);
        }
    }

    private PairData startNewVirtualTrade(PairData pairData) {
        log.info("🚀 Начинаем новый трейд для {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
        Settings settings = settingsService.getSettings();

        //Проверка на дурака
        if (validateService.isLastZLessThenMinZ(pairData, settings)) {
            //если впервые прогоняем и Z<ZMin
            pairDataService.delete(pairData);
            log.warn("Удалили пару {} - {} т.к. ZCurrent < ZMin", pairData.getLongTicker(), pairData.getShortTicker());
            return null;
        }

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        Optional<ZScoreData> maybeZScoreData = zScoreService.calculateZScoreDataForNewTrade(pairData, settings, candlesMap);

        if (maybeZScoreData.isEmpty()) {
            log.warn("ZScore data is empty");
            return null;
        }

        ZScoreData zScoreData = maybeZScoreData.get();

        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params

        if (!Objects.equals(pairData.getLongTicker(), zScoreData.getUndervaluedTicker()) || !Objects.equals(pairData.getShortTicker(), zScoreData.getOvervaluedTicker())) {
            String message = String.format("Ошибка начала нового терейда для пары лонг=%s шорт=%s. Тикеры поменялись местами!!! Торговать нельзя!!!", pairData.getLongTicker(), pairData.getShortTicker());
            log.error(message);
            throw new IllegalArgumentException(message);
        }

        log.info(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(), latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));

        pairDataService.updateVirtual(pairData, zScoreData, candlesMap);
        changesService.calculateVirtual(pairData);
        String exitReason = exitStrategyService.getExitReason(pairData);
        if (exitReason != null) {
            pairData.setExitReason(exitReason);
            pairData.setStatus(TradeStatus.CLOSED);
        }
        pairDataService.save(pairData);

        tradeLogService.saveLog(pairData);

        return pairData;
    }

    private PairData startNewRealTrade(PairData pairData) {
        log.info("🚀 Начинаем новый трейд для {} - {}", pairData.getLongTicker(), pairData.getShortTicker());
        Settings settings = settingsService.getSettings();

        //Проверка на дурака
        if (validateService.isLastZLessThenMinZ(pairData, settings)) {
            //если впервые прогоняем и Z<ZMin
            pairDataService.delete(pairData);
            log.warn("Удалили пару {} - {} т.к. ZCurrent < ZMin", pairData.getLongTicker(), pairData.getShortTicker());
            return null;
        }

        Map<String, List<Candle>> candlesMap = candlesService.getApplicableCandlesMap(pairData, settings);
        Optional<ZScoreData> maybeZScoreData = zScoreService.calculateZScoreDataForNewTrade(pairData, settings, candlesMap);

        if (maybeZScoreData.isEmpty()) {
            log.warn("📊 ZScore данные пусты для пары {}/{}. Пропускаем создание нового трейда.", pairData.getLongTicker(), pairData.getShortTicker());
            return null;
        }

        ZScoreData zScoreData = maybeZScoreData.get();

        ZScoreParam latest = zScoreData.getLastZScoreParam(); // последние params

        if (!Objects.equals(pairData.getLongTicker(), zScoreData.getUndervaluedTicker()) || !Objects.equals(pairData.getShortTicker(), zScoreData.getOvervaluedTicker())) {
            String message = String.format("Ошибка начала нового терейда для пары лонг=%s шорт=%s. Тикеры поменялись местами!!! Торговать нельзя!!!", pairData.getLongTicker(), pairData.getShortTicker());
            log.error(message);
            throw new IllegalArgumentException(message);
        }

        log.info(String.format("Наш новый трейд: underValued=%s overValued=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker(), latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));

        //todo ПИЛЮ...

        // Проверяем, можем ли открыть новую пару на торговом депо
        if (tradingIntegrationService.canOpenNewPair()) {
            // Открываем арбитражную пару через торговую систему СИНХРОННО
            OpenArbitragePairResult openArbitragePairResult = tradingIntegrationService.openArbitragePair(pairData, zScoreData, candlesMap);
            if (openArbitragePairResult != null && openArbitragePairResult.isSuccess()) {

                TradeResult openLongTradeResult = openArbitragePairResult.getLongTradeResult();
                TradeResult openShortTradeResult = openArbitragePairResult.getShortTradeResult();

                log.info("✅ Успешно открыта арбитражная пара через торговую систему: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                pairDataService.updateReal(pairData, zScoreData, candlesMap, openLongTradeResult, openShortTradeResult);
                changesService.calculateReal(pairData);
                String exitReason = exitStrategyService.getExitReason(pairData);
                if (exitReason != null) {
                    log.info("🚪 Найдена причина для выхода из позиции: {} для пары {}/{}",
                            exitReason, pairData.getLongTicker(), pairData.getShortTicker());

                    // Закрываем арбитражную пару через торговую систему СИНХРОННО
                    CloseArbitragePairResult closeArbitragePairResult = tradingIntegrationService.closeArbitragePair(pairData);
                    if (closeArbitragePairResult != null && closeArbitragePairResult.isSuccess()) {
                        log.info("✅ Успешно закрыта арбитражная пара: {}/{}",
                                pairData.getLongTicker(), pairData.getShortTicker());

                        TradeResult closeLongTradeResult = closeArbitragePairResult.getLongTradeResult();
                        TradeResult closeShortTradeResult = closeArbitragePairResult.getShortTradeResult();
                        pairDataService.updateReal(pairData, zScoreData, candlesMap, closeLongTradeResult, closeShortTradeResult); //todo может и не надо обновлять тк там все тоже самое что и при открытии трейда выше
                        // Снова обновляем данные после закрытия позиций
                        changesService.calculateReal(pairData);

                        pairData.setExitReason(exitReason);
                        pairData.setStatus(TradeStatus.CLOSED);
                    } else {
                        log.error("❌ Ошибка при закрытии арбитражной пары: {}/{}",
                                pairData.getLongTicker(), pairData.getShortTicker());

                        pairData.setExitReason("ERROR_CLOSING: " + exitReason);
                        pairData.setStatus(TradeStatus.ERROR_200);
                    }
                }
                pairDataService.save(pairData);

                tradeLogService.saveLog(pairData);
            } else {
                log.warn("⚠️ Не удалось открыть арбитражную пару через торговую систему: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());

                pairData.setStatus(TradeStatus.ERROR_100);
                pairDataService.save(pairData);
            }
        } else {
            log.warn("⚠️ Недостаточно средств в торговом депо для открытия пары {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

            pairData.setStatus(TradeStatus.ERROR_110);
            pairDataService.save(pairData);
        }

        return pairData;
    }
}
