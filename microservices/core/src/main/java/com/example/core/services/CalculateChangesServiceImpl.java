package com.example.core.services;

import com.example.core.messaging.SendEventService;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.dto.ChangesData;
import com.example.shared.dto.Positioninfo;
import com.example.shared.dto.ProfitExtremum;
import com.example.shared.events.rabbit.CoreEvent;
import com.example.shared.models.Position;
import com.example.shared.models.Settings;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.example.shared.utils.BigDecimalUtil.safeGet;
import static com.example.shared.utils.BigDecimalUtil.safeScale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateChangesServiceImpl implements CalculateChangesService {
    private final TradingIntegrationService tradingIntegrationServiceImpl;
    private final ProfitExtremumService profitExtremumService;
    private final SettingsService settingsService;
    //    private final TelegramNotificationService telegramNotificationService;
    private final SendEventService sendEventService;

    public ChangesData getChanges(Pair tradingPair) {
        log.debug("==> getChanges: НАЧАЛО для пары {}", tradingPair.getPairName());
        try {

            log.debug("Запрашиваем информацию о позициях...");
            Positioninfo positionsInfo = tradingIntegrationServiceImpl.getPositionInfo(tradingPair);
            log.debug("Получена информация о позициях: {}", positionsInfo);

            if (positionsInfo == null || positionsInfo.getLongPosition() == null || positionsInfo.getShortPosition() == null) {
                log.warn("⚠️ Не удалось получить полную информацию о позициях для пары {}. PositionInfo: {}", tradingPair.getPairName(), positionsInfo);
                return new ChangesData();
            }

            ChangesData result = getFromPositions(tradingPair, positionsInfo);
            log.debug("<== getChanges: КОНЕЦ для пары {}. Результат: {}", tradingPair.getPairName(), result);
            return result;

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при обновлении данных (getChanges) для пары {}: {}", tradingPair.getPairName(), e.getMessage(), e);
            Settings settings = settingsService.getSettings();
            settings.setAutoTradingEnabled(false);

            settingsService.save(settings);
            log.warn("Автотрейдинг отключен.");

//            telegramNotificationService.sendTelegramMessage("Ошибка при обновлении changes. Автотрейдинг был отключен.");
            sendEventService.sendCoreEvent(new CoreEvent("Ошибка при обновлении changes. Автотрейдинг был отключен.", CoreEvent.Type.MESSAGE_TO_TELEGRAM));
            log.warn("Уведомление в телеграм отправлено.");

            throw new RuntimeException("❌ КРИТИЧЕСКАЯ ОШИБКА при обновлении данных (getChanges) для пары " + tradingPair.getPairName(), e);
        }
    }

    private ChangesData getFromPositions(Pair tradingPair, Positioninfo positionsInfo) {
        log.debug("--> getFromPositions: НАЧАЛО для пары {}", tradingPair.getPairName());
        Position longPosition = positionsInfo.getLongPosition();
        Position shortPosition = positionsInfo.getShortPosition();
        boolean isPositionsClosed = positionsInfo.isPositionsClosed();
        log.debug("Статус позиций: isPositionsClosed = {}", isPositionsClosed);

        ChangesData changesData = new ChangesData();
        changesData.setLongCurrentPrice(longPosition.getCurrentPrice());
        changesData.setShortCurrentPrice(shortPosition.getCurrentPrice());
        log.debug("Текущие цены: LONG {} = {}, SHORT {} = {}", longPosition.getSymbol(), longPosition.getCurrentPrice(), shortPosition.getSymbol(), shortPosition.getCurrentPrice());

        return isPositionsClosed ?
                getFromClosedPositions(tradingPair, changesData, longPosition, shortPosition) :
                getFromOpenPositions(tradingPair, changesData, longPosition, shortPosition);
    }

    private ChangesData getFromClosedPositions(Pair tradingPair, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.debug("--> getFromClosedPositions для пары {}", tradingPair.getPairName());

        BigDecimal totalRealizedPnlUSDT = safeScale(safeGet(longPosition.getRealizedPnLUSDT()).add(safeGet(shortPosition.getRealizedPnLUSDT())), 8);
//        BigDecimal totalRealizedPnlPercent = safeScale(safeGet(longPosition.getRealizedPnLPercent()).add(safeGet(shortPosition.getRealizedPnLPercent())), 8); //todo 0.00 в закрытых парах
//        BigDecimal totalFees = safeScale(
//                safeGet(longPosition.getOpeningFees()).add(safeGet(longPosition.getClosingFees())).add(safeGet(longPosition.getFundingFees()))
//                        .add(safeGet(shortPosition.getOpeningFees())).add(safeGet(shortPosition.getClosingFees())).add(safeGet(shortPosition.getFundingFees())),
//                8);

        // Взвешенный процентный профит: (P1 * A1 + P2 * A2) / (A1 + A2)
        BigDecimal longAlloc = safeGet(longPosition.getAllocatedAmount());
        BigDecimal shortAlloc = safeGet(shortPosition.getAllocatedAmount());
        BigDecimal totalAlloc = longAlloc.add(shortAlloc);

        BigDecimal totalRealizedPnlPercent;
        if (totalAlloc.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal weightedSum = safeGet(longPosition.getUnrealizedPnLPercent()).multiply(longAlloc)
                    .add(safeGet(shortPosition.getUnrealizedPnLPercent()).multiply(shortAlloc));
            totalRealizedPnlPercent = safeScale(weightedSum.divide(totalAlloc, 8, RoundingMode.HALF_UP), 8);
        } else {
            totalRealizedPnlPercent = BigDecimal.ZERO;
            log.warn("⚠️ allocatedAmount у обеих позиций ноль. Устанавливаем PnL % = 0");
        }

        // Расчет общих комиссий
        BigDecimal totalFees = safeScale(safeGet(longPosition.getOpenCloseFundingFees()).add(safeGet(shortPosition.getOpenCloseFundingFees())), 8);

        log.info("Реализованный PnL: {} USDT ({} %), комиссии (открытие+закрытие+фандинг): {}", totalRealizedPnlUSDT, totalRealizedPnlPercent, totalFees);

        return getProfitAndStatistics(tradingPair, changesData, totalRealizedPnlUSDT, totalRealizedPnlPercent, true, longPosition, shortPosition);
    }

    private ChangesData getFromOpenPositions(Pair tradingPair, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.debug("--> getFromOpenPositions для пары {}", tradingPair.getPairName());

        // Суммарный USDT-профит (уже с учетом комиссий)
        BigDecimal totalUnrealizedPnlUSDT = safeScale(
                safeGet(longPosition.getUnrealizedPnLUSDT()).add(safeGet(shortPosition.getUnrealizedPnLUSDT())),
                8
        );

        // Взвешенный процентный профит: (P1 * A1 + P2 * A2) / (A1 + A2)
        BigDecimal longAlloc = safeGet(longPosition.getAllocatedAmount());
        BigDecimal shortAlloc = safeGet(shortPosition.getAllocatedAmount());
        BigDecimal totalAlloc = longAlloc.add(shortAlloc);

        BigDecimal totalUnrealizedPnlPercent;
        if (totalAlloc.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal weightedSum = safeGet(longPosition.getUnrealizedPnLPercent()).multiply(longAlloc)
                    .add(safeGet(shortPosition.getUnrealizedPnLPercent()).multiply(shortAlloc));
            totalUnrealizedPnlPercent = safeScale(weightedSum.divide(totalAlloc, 8, RoundingMode.HALF_UP), 8);
        } else {
            totalUnrealizedPnlPercent = BigDecimal.ZERO;
            log.warn("⚠️ allocatedAmount у обеих позиций ноль. Устанавливаем PnL % = 0");
        }

        // Расчет общих комиссий
        BigDecimal totalFees = safeScale(
                safeGet(longPosition.getOpeningFees()).add(safeGet(longPosition.getFundingFees()))
                        .add(safeGet(shortPosition.getOpeningFees())).add(safeGet(shortPosition.getFundingFees())),
                8
        );

        // Логирование деталей
        log.debug("📊 Итог по паре {}:", tradingPair.getPairName());
        log.debug("➡️ Нереализованный PnL: {} USDT ({} %) с учетом комиссий", totalUnrealizedPnlUSDT, totalUnrealizedPnlPercent);
        log.debug("➡️ Лонг: allocated = {}, unrealizedPnL = {} USDT ({} %), openingFee = {}, fundingFee = {}",
                longAlloc,
                safeGet(longPosition.getUnrealizedPnLUSDT()), safeGet(longPosition.getUnrealizedPnLPercent()),
                safeGet(longPosition.getOpeningFees()), safeGet(longPosition.getFundingFees())
        );
        log.debug("➡️ Шорт: allocated = {}, unrealizedPnL = {} USDT ({} %), openingFee = {}, fundingFee = {}",
                shortAlloc,
                safeGet(shortPosition.getUnrealizedPnLUSDT()), safeGet(shortPosition.getUnrealizedPnLPercent()),
                safeGet(shortPosition.getOpeningFees()), safeGet(shortPosition.getFundingFees())
        );
        log.debug("➡️ Суммарные комиссии по паре {}: {}", tradingPair.getPairName(), totalFees);

        return getProfitAndStatistics(tradingPair, changesData, totalUnrealizedPnlUSDT, totalUnrealizedPnlPercent, false, longPosition, shortPosition);
    }

    private ChangesData getProfitAndStatistics(Pair tradingPair, ChangesData changesData, BigDecimal pnlUSDT, BigDecimal pnlPercent,
                                               boolean isPositionsClosed,
                                               Position longPosition, Position shortPosition) {

        changesData.setLongUSDTChanges(safeScale(isPositionsClosed ? safeGet(longPosition.getRealizedPnLUSDT()) : safeGet(longPosition.getUnrealizedPnLUSDT()), 8));
        changesData.setLongPercentChanges(safeScale(isPositionsClosed ? safeGet(longPosition.getRealizedPnLPercent()) : safeGet(longPosition.getUnrealizedPnLPercent()), 8));

        changesData.setShortUSDTChanges(safeScale(isPositionsClosed ? safeGet(shortPosition.getRealizedPnLUSDT()) : safeGet(shortPosition.getUnrealizedPnLUSDT()), 8));
        changesData.setShortPercentChanges(safeScale(isPositionsClosed ? safeGet(shortPosition.getRealizedPnLPercent()) : safeGet(shortPosition.getUnrealizedPnLPercent()), 8));

        changesData.setProfitUSDTChanges(pnlUSDT);
        changesData.setProfitPercentChanges(pnlPercent);

        log.debug("Профит: {} USDT ({} %) из {}", pnlUSDT, pnlPercent, isPositionsClosed ? "закрытых позиций" : "открытых позиций");

        return getStatistics(tradingPair, changesData);
    }

    private ChangesData getStatistics(Pair tradingPair, ChangesData changesData) {
        BigDecimal zScoreEntry = tradingPair.getZScoreEntry() != null ? tradingPair.getZScoreEntry() : BigDecimal.ZERO;
        BigDecimal zScoreCurrent = tradingPair.getZScoreCurrent() != null ? tradingPair.getZScoreCurrent() : BigDecimal.ZERO;
        changesData.setZScoreChanges(safeScale(zScoreCurrent.subtract(zScoreEntry), 2));

        ProfitExtremum profitExtremum = profitExtremumService.getProfitExtremums(tradingPair, changesData);

        updateExtremumValues(tradingPair, changesData, changesData.getLongPercentChanges(), changesData.getShortPercentChanges(),
                tradingPair.getZScoreCurrent() != null ? tradingPair.getZScoreCurrent() : BigDecimal.ZERO, 
                tradingPair.getCorrelationCurrent() != null ? tradingPair.getCorrelationCurrent() : BigDecimal.ZERO);

        changesData.setMinProfitChanges(profitExtremum.minProfit());
        changesData.setMaxProfitChanges(profitExtremum.maxProfit());
        changesData.setTimeInMinutesSinceEntryToMaxProfit(profitExtremum.timeToMax());
        changesData.setTimeInMinutesSinceEntryToMinProfit(profitExtremum.timeToMin());

        logFinalResults(tradingPair, changesData);

        return changesData;
    }

    private void logFinalResults(Pair tradingPair, ChangesData changesData) {
        log.info("📊 ЛОНГ {}: вход: {}, тек.: {}, изм.: {} %", tradingPair.getLongTicker(), tradingPair.getLongTickerEntryPrice(), changesData.getLongCurrentPrice(), changesData.getLongPercentChanges());
        log.info("📉 ШОРТ {}: вход: {}, тек.: {}, изм.: {} %", tradingPair.getShortTicker(), tradingPair.getShortTickerEntryPrice(), changesData.getShortCurrentPrice(), changesData.getShortPercentChanges());
        log.info("💰 Текущий профит: {} USDT ({} %)", changesData.getProfitUSDTChanges(), changesData.getProfitPercentChanges());
        log.info("📈 Max профит: {} % ({} минут), Min профит: {} % ({} минут)", changesData.getMaxProfitChanges(), changesData.getTimeInMinutesSinceEntryToMaxProfit(), changesData.getMinProfitChanges(), changesData.getTimeInMinutesSinceEntryToMinProfit());
    }

    private void updateExtremumValues(Pair tradingPair, ChangesData changesData, BigDecimal longPct, BigDecimal shortPct,
                                      BigDecimal zScore, BigDecimal corr) {
        changesData.setMinZ(updateMin(tradingPair.getMinZ(), zScore));
        changesData.setMaxZ(updateMax(tradingPair.getMaxZ(), zScore));
        changesData.setMinLong(updateMin(tradingPair.getMinLong(), longPct));
        changesData.setMaxLong(updateMax(tradingPair.getMaxLong(), longPct));
        changesData.setMinShort(updateMin(tradingPair.getMinShort(), shortPct));
        changesData.setMaxShort(updateMax(tradingPair.getMaxShort(), shortPct));
        changesData.setMinCorr(updateMin(tradingPair.getMinCorr(), corr));
        changesData.setMaxCorr(updateMax(tradingPair.getMaxCorr(), corr));
    }

    private BigDecimal updateMin(BigDecimal currentMin, BigDecimal newVal) {
        return (currentMin == null || newVal.compareTo(currentMin) < 0) ? newVal : currentMin;
    }

    private BigDecimal updateMax(BigDecimal currentMax, BigDecimal newVal) {
        return (currentMax == null || newVal.compareTo(currentMax) > 0) ? newVal : currentMax;
    }

}
