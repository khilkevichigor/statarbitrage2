package com.example.core.services;

import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.dto.ChangesData;
import com.example.shared.dto.ProfitExtremum;
import com.example.shared.models.PairData;
import com.example.shared.models.Position;
import com.example.shared.models.Positioninfo;
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

    public ChangesData getChanges(PairData pairData) {
        log.debug("==> getChanges: НАЧАЛО для пары {}", pairData.getPairName());
        try {

            log.debug("Запрашиваем информацию о позициях...");
            Positioninfo positionsInfo = tradingIntegrationServiceImpl.getPositionInfo(pairData);
            log.debug("Получена информация о позициях: {}", positionsInfo);

            if (positionsInfo == null || positionsInfo.getLongPosition() == null || positionsInfo.getShortPosition() == null) {
                log.warn("⚠️ Не удалось получить полную информацию о позициях для пары {}. PositionInfo: {}", pairData.getPairName(), positionsInfo);
                return new ChangesData();
            }

            ChangesData result = getFromPositions(pairData, positionsInfo);
            log.debug("<== getChanges: КОНЕЦ для пары {}. Результат: {}", pairData.getPairName(), result);
            return result;

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при обновлении данных (getChanges) для пары {}: {}", pairData.getPairName(), e.getMessage(), e);
        }
        log.debug("<== getChanges: КОНЕЦ (с ошибкой) для пары {}", pairData.getPairName());
        return new ChangesData();
    }

    private ChangesData getFromPositions(PairData pairData, Positioninfo positionsInfo) {
        log.debug("--> getFromPositions: НАЧАЛО для пары {}", pairData.getPairName());
        Position longPosition = positionsInfo.getLongPosition();
        Position shortPosition = positionsInfo.getShortPosition();
        boolean isPositionsClosed = positionsInfo.isPositionsClosed();
        log.debug("Статус позиций: isPositionsClosed = {}", isPositionsClosed);

        ChangesData changesData = new ChangesData();
        changesData.setLongCurrentPrice(longPosition.getCurrentPrice());
        changesData.setShortCurrentPrice(shortPosition.getCurrentPrice());
        log.debug("Текущие цены: LONG {} = {}, SHORT {} = {}", longPosition.getSymbol(), longPosition.getCurrentPrice(), shortPosition.getSymbol(), shortPosition.getCurrentPrice());

        return isPositionsClosed ?
                getFromClosedPositions(pairData, changesData, longPosition, shortPosition) :
                getFromOpenPositions(pairData, changesData, longPosition, shortPosition);
    }

    private ChangesData getFromClosedPositions(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.debug("--> getFromClosedPositions для пары {}", pairData.getPairName());

        BigDecimal totalRealizedPnlUSDT = safeScale(safeGet(longPosition.getRealizedPnLUSDT()).add(safeGet(shortPosition.getRealizedPnLUSDT())), 8);
        BigDecimal totalRealizedPnlPercent = safeScale(safeGet(longPosition.getRealizedPnLPercent()).add(safeGet(shortPosition.getRealizedPnLPercent())), 8);
        BigDecimal totalFees = safeScale(
                safeGet(longPosition.getOpeningFees()).add(safeGet(longPosition.getClosingFees())).add(safeGet(longPosition.getFundingFees()))
                        .add(safeGet(shortPosition.getOpeningFees())).add(safeGet(shortPosition.getClosingFees())).add(safeGet(shortPosition.getFundingFees())),
                8);

        log.debug("Реализованный PnL: {} USDT ({} %), комиссии: {}", totalRealizedPnlUSDT, totalRealizedPnlPercent, totalFees);

        return getProfitAndStatistics(pairData, changesData, totalRealizedPnlUSDT, totalRealizedPnlPercent, true, longPosition, shortPosition);
    }

    private ChangesData getFromOpenPositions(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.debug("--> getFromOpenPositions для пары {}", pairData.getPairName());

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
        log.debug("📊 Итог по паре {}:", pairData.getPairName());
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
        log.debug("➡️ Суммарные комиссии по паре {}: {}", pairData.getPairName(), totalFees);

        return getProfitAndStatistics(pairData, changesData, totalUnrealizedPnlUSDT, totalUnrealizedPnlPercent, false, longPosition, shortPosition);
    }

    private ChangesData getProfitAndStatistics(PairData pairData, ChangesData changesData, BigDecimal pnlUSDT, BigDecimal pnlPercent,
                                               boolean isPositionsClosed,
                                               Position longPosition, Position shortPosition) {

        changesData.setLongUSDTChanges(safeScale(isPositionsClosed ? safeGet(longPosition.getRealizedPnLUSDT()) : safeGet(longPosition.getUnrealizedPnLUSDT()), 8));
        changesData.setLongPercentChanges(safeScale(isPositionsClosed ? safeGet(longPosition.getRealizedPnLPercent()) : safeGet(longPosition.getUnrealizedPnLPercent()), 8));

        changesData.setShortUSDTChanges(safeScale(isPositionsClosed ? safeGet(shortPosition.getRealizedPnLUSDT()) : safeGet(shortPosition.getUnrealizedPnLUSDT()), 8));
        changesData.setShortPercentChanges(safeScale(isPositionsClosed ? safeGet(shortPosition.getRealizedPnLPercent()) : safeGet(shortPosition.getUnrealizedPnLPercent()), 8));

        changesData.setProfitUSDTChanges(pnlUSDT);
        changesData.setProfitPercentChanges(pnlPercent);

        log.debug("Профит: {} USDT ({} %) из {}", pnlUSDT, pnlPercent, isPositionsClosed ? "закрытых позиций" : "открытых позиций");

        return getStatistics(pairData, changesData);
    }

    private ChangesData getStatistics(PairData pairData, ChangesData changesData) {
        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
        changesData.setZScoreChanges(safeScale(zScoreCurrent.subtract(zScoreEntry), 2));

        ProfitExtremum profitExtremum = profitExtremumService.getProfitExtremums(pairData, changesData);

        updateExtremumValues(pairData, changesData, changesData.getLongPercentChanges(), changesData.getShortPercentChanges(),
                BigDecimal.valueOf(pairData.getZScoreCurrent()), BigDecimal.valueOf(pairData.getCorrelationCurrent()));

        changesData.setMinProfitChanges(profitExtremum.minProfit());
        changesData.setMaxProfitChanges(profitExtremum.maxProfit());
        changesData.setTimeInMinutesSinceEntryToMaxProfit(profitExtremum.timeToMax());
        changesData.setTimeInMinutesSinceEntryToMinProfit(profitExtremum.timeToMin());

        logFinalResults(pairData, changesData);

        return changesData;
    }

    private void logFinalResults(PairData pairData, ChangesData changesData) {
        log.info("📊 ЛОНГ {}: вход: {}, тек.: {}, изм.: {} %", pairData.getLongTicker(), pairData.getLongTickerEntryPrice(), changesData.getLongCurrentPrice(), changesData.getLongPercentChanges());
        log.info("📉 ШОРТ {}: вход: {}, тек.: {}, изм.: {} %", pairData.getShortTicker(), pairData.getShortTickerEntryPrice(), changesData.getShortCurrentPrice(), changesData.getShortPercentChanges());
        log.info("💰 Текущий профит: {} USDT ({} %)", changesData.getProfitUSDTChanges(), changesData.getProfitPercentChanges());
        log.info("📈 Max профит: {} % ({} минут), Min профит: {} % ({} минут)", changesData.getMaxProfitChanges(), changesData.getTimeInMinutesSinceEntryToMaxProfit(), changesData.getMinProfitChanges(), changesData.getTimeInMinutesSinceEntryToMinProfit());
    }

    private void updateExtremumValues(PairData pairData, ChangesData changesData, BigDecimal longPct, BigDecimal shortPct,
                                      BigDecimal zScore, BigDecimal corr) {
        changesData.setMinZ(updateMin(pairData.getMinZ(), zScore));
        changesData.setMaxZ(updateMax(pairData.getMaxZ(), zScore));
        changesData.setMinLong(updateMin(pairData.getMinLong(), longPct));
        changesData.setMaxLong(updateMax(pairData.getMaxLong(), longPct));
        changesData.setMinShort(updateMin(pairData.getMinShort(), shortPct));
        changesData.setMaxShort(updateMax(pairData.getMaxShort(), shortPct));
        changesData.setMinCorr(updateMin(pairData.getMinCorr(), corr));
        changesData.setMaxCorr(updateMax(pairData.getMaxCorr(), corr));
    }

    private BigDecimal updateMin(BigDecimal currentMin, BigDecimal newVal) {
        return (currentMin == null || newVal.compareTo(currentMin) < 0) ? newVal : currentMin;
    }

    private BigDecimal updateMax(BigDecimal currentMax, BigDecimal newVal) {
        return (currentMax == null || newVal.compareTo(currentMax) > 0) ? newVal : currentMax;
    }

}
