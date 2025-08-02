package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.dto.ProfitExtremum;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.Positioninfo;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.example.statarbitrage.common.utils.BigDecimalUtil.safeScale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateChangesService {
    private final TradingIntegrationService tradingIntegrationService;
    private final ProfitExtremumService profitExtremumService;

    public ChangesData getChanges(PairData pairData) {
        log.info("==> getChanges: НАЧАЛО для пары {}", pairData.getPairName());
        try {
            log.info("Запрашиваем информацию о позициях...");
            Positioninfo positionsInfo = tradingIntegrationService.getPositionInfo(pairData);
            log.info("Получена информация о позициях: {}", positionsInfo);

            if (positionsInfo == null || positionsInfo.getLongPosition() == null || positionsInfo.getShortPosition() == null) {
                log.warn("⚠️ Не удалось получить полную информацию о позициях для пары {}. PositionInfo: {}", pairData.getPairName(), positionsInfo);
                return new ChangesData();
            }

            ChangesData result = getFromPositions(pairData, positionsInfo);
            log.info("<== getChanges: КОНЕЦ для пары {}. Результат: {}", pairData.getPairName(), result);
            return result;

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при обновлении данных (getChanges) для пары {}: {}", pairData.getPairName(), e.getMessage(), e);
        }
        log.info("<== getChanges: КОНЕЦ (с ошибкой) для пары {}", pairData.getPairName());
        return new ChangesData();
    }

    private ChangesData getFromPositions(PairData pairData, Positioninfo positionsInfo) {
        log.info("--> getFromPositions: НАЧАЛО для пары {}", pairData.getPairName());
        Position longPosition = positionsInfo.getLongPosition();
        Position shortPosition = positionsInfo.getShortPosition();
        boolean isPositionsClosed = positionsInfo.isPositionsClosed();
        log.info("Статус позиций: isPositionsClosed = {}", isPositionsClosed);

        ChangesData changesData = new ChangesData();
        changesData.setLongCurrentPrice(longPosition.getCurrentPrice());
        changesData.setShortCurrentPrice(shortPosition.getCurrentPrice());
        log.info("Текущие цены: LONG {} = {}, SHORT {} = {}", longPosition.getSymbol(), longPosition.getCurrentPrice(), shortPosition.getSymbol(), shortPosition.getCurrentPrice());

        return isPositionsClosed ?
                getFromClosedPositions(pairData, changesData, longPosition, shortPosition) :
                getFromOpenPositions(pairData, changesData, longPosition, shortPosition);
    }

    private ChangesData getFromClosedPositions(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.info("--> getFromClosedPositions для пары {}", pairData.getPairName());

        BigDecimal totalRealizedPnlUSDT = safeScale(longPosition.getRealizedPnLUSDT().add(shortPosition.getRealizedPnLUSDT()), 8);
        BigDecimal totalRealizedPnlPercent = safeScale(longPosition.getRealizedPnLPercent().add(shortPosition.getRealizedPnLPercent()), 8);
        BigDecimal totalFees = safeScale(
                longPosition.getOpeningFees().add(longPosition.getClosingFees()).add(longPosition.getFundingFees())
                        .add(shortPosition.getOpeningFees()).add(shortPosition.getClosingFees()).add(shortPosition.getFundingFees()),
                8);

        log.info("Реализованный PnL: {} USDT ({} %), комиссии: {}", totalRealizedPnlUSDT, totalRealizedPnlPercent, totalFees);

        return getProfitAndStatistics(pairData, changesData, totalRealizedPnlUSDT, totalRealizedPnlPercent, true, longPosition, shortPosition);
    }

    private ChangesData getFromOpenPositions(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.info("--> getFromOpenPositions для пары {}", pairData.getPairName());

        // Суммарный USDT-профит (уже с учетом комиссий)
        BigDecimal totalUnrealizedPnlUSDT = safeScale(
                safe(longPosition.getUnrealizedPnLUSDT()).add(safe(shortPosition.getUnrealizedPnLUSDT())),
                8
        );

        // Взвешенный процентный профит: (P1 * A1 + P2 * A2) / (A1 + A2)
        BigDecimal longAlloc = safe(longPosition.getAllocatedAmount());
        BigDecimal shortAlloc = safe(shortPosition.getAllocatedAmount());
        BigDecimal totalAlloc = longAlloc.add(shortAlloc);

        BigDecimal totalUnrealizedPnlPercent;
        if (totalAlloc.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal weightedSum = safe(longPosition.getUnrealizedPnLPercent()).multiply(longAlloc)
                    .add(safe(shortPosition.getUnrealizedPnLPercent()).multiply(shortAlloc));
            totalUnrealizedPnlPercent = safeScale(weightedSum.divide(totalAlloc, 8, RoundingMode.HALF_UP), 8);
        } else {
            totalUnrealizedPnlPercent = BigDecimal.ZERO;
            log.warn("⚠️ allocatedAmount у обеих позиций ноль. Устанавливаем PnL % = 0");
        }

        // Расчет общих комиссий
        BigDecimal totalFees = safeScale(
                safe(longPosition.getOpeningFees()).add(safe(longPosition.getFundingFees()))
                        .add(safe(shortPosition.getOpeningFees())).add(safe(shortPosition.getFundingFees())),
                8
        );

        // Логирование деталей
        log.info("📊 Итог по паре {}:", pairData.getPairName());
        log.info("➡️ Нереализованный PnL: {} USDT ({} %) с учетом комиссий", totalUnrealizedPnlUSDT, totalUnrealizedPnlPercent);
        log.info("➡️ Лонг: allocated = {}, unrealizedPnL = {} USDT ({} %), openingFee = {}, fundingFee = {}",
                longAlloc,
                longPosition.getUnrealizedPnLUSDT(), longPosition.getUnrealizedPnLPercent(),
                longPosition.getOpeningFees(), longPosition.getFundingFees()
        );
        log.info("➡️ Шорт: allocated = {}, unrealizedPnL = {} USDT ({} %), openingFee = {}, fundingFee = {}",
                shortAlloc,
                shortPosition.getUnrealizedPnLUSDT(), shortPosition.getUnrealizedPnLPercent(),
                shortPosition.getOpeningFees(), shortPosition.getFundingFees()
        );
        log.info("➡️ Суммарные комиссии по паре {}: {}", pairData.getPairName(), totalFees);

        return getProfitAndStatistics(pairData, changesData, totalUnrealizedPnlUSDT, totalUnrealizedPnlPercent, false, longPosition, shortPosition);
    }

    private ChangesData getProfitAndStatistics(PairData pairData, ChangesData changesData, BigDecimal pnlUSDT, BigDecimal pnlPercent,
                                               boolean isPositionsClosed,
                                               Position longPosition, Position shortPosition) {

        changesData.setLongUSDTChanges(safeScale(isPositionsClosed ? longPosition.getRealizedPnLUSDT() : longPosition.getUnrealizedPnLUSDT(), 8));
        changesData.setLongPercentChanges(safeScale(isPositionsClosed ? longPosition.getRealizedPnLPercent() : longPosition.getUnrealizedPnLPercent(), 8));

        changesData.setShortUSDTChanges(safeScale(isPositionsClosed ? shortPosition.getRealizedPnLUSDT() : shortPosition.getUnrealizedPnLUSDT(), 8));
        changesData.setShortPercentChanges(safeScale(isPositionsClosed ? shortPosition.getRealizedPnLPercent() : shortPosition.getUnrealizedPnLPercent(), 8));

        changesData.setProfitUSDTChanges(pnlUSDT);
        changesData.setProfitPercentChanges(pnlPercent);

        log.info("Профит: {} USDT ({} %) из {}", pnlUSDT, pnlPercent, isPositionsClosed ? "закрытых позиций" : "открытых позиций");

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

    private BigDecimal safe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

}
