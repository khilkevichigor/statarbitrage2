package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.Positioninfo;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateChangesService {

    private static final long MILLISECONDS_IN_MINUTE = 1000 * 60;
    private static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final int DEFAULT_SCALE = 2;

    private final TradingIntegrationService tradingIntegrationService;

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

    private ChangesData getFromOpenPositions(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.info("--> getFromOpenPositions для пары {}", pairData.getPairName());

        BigDecimal netPnlUSDT = scale(longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT()));
//        BigDecimal netPnlPercent = scale(longPosition.getUnrealizedPnLPercent().add(shortPosition.getUnrealizedPnLPercent())); //todo не то что на окх... странно
        BigDecimal netPnlPercent = scale(calcPercent(longPosition, shortPosition));
        BigDecimal totalFees = scale(longPosition.getOpeningFees().add(shortPosition.getOpeningFees()));

        log.info("Нереализованный PnL в USDT: {}, в %: {}, комиссии: {}", netPnlUSDT, netPnlPercent, totalFees);

        return getProfitAndStatistics(pairData, changesData, netPnlUSDT, netPnlPercent, false, longPosition, shortPosition);
    }

    //сами считаем общий процент
    private BigDecimal calcPercent(Position longPosition, Position shortPosition) {
        BigDecimal totalAllocatedAmount = longPosition.getAllocatedAmount().add(shortPosition.getAllocatedAmount());
        BigDecimal netPnlUSDT = longPosition.getUnrealizedPnLUSDT().add(shortPosition.getUnrealizedPnLUSDT());
        if (totalAllocatedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // защита от деления на ноль
        }
        return netPnlUSDT
                .divide(totalAllocatedAmount, 8, RoundingMode.HALF_UP) // делим с нужной точностью
                .multiply(BigDecimal.valueOf(100));
    }


    private ChangesData getFromClosedPositions(PairData pairData, ChangesData changesData, Position longPosition, Position shortPosition) {
        log.info("--> getFromClosedPositions для пары {}", pairData.getPairName());

        BigDecimal netPnlUSDT = scale(longPosition.getRealizedPnLUSDT().add(shortPosition.getRealizedPnLUSDT()));
        BigDecimal netPnlPercent = scale(longPosition.getRealizedPnLPercent().add(shortPosition.getRealizedPnLPercent()));
        BigDecimal totalFees = scale(
                safeAdd(longPosition.getOpeningFees(), longPosition.getClosingFees())
                        .add(safeAdd(shortPosition.getOpeningFees(), shortPosition.getClosingFees())));

        log.info("Реализованный PnL в USDT: {}, в %: {}, комиссии: {}", netPnlUSDT, netPnlPercent, totalFees);

        return getProfitAndStatistics(pairData, changesData, netPnlUSDT, netPnlPercent, true, longPosition, shortPosition);
    }

    private ChangesData getProfitAndStatistics(PairData pairData, ChangesData changesData, BigDecimal netPnlUSDT, BigDecimal netPnlPercent,
                                               boolean isPositionsClosed,
                                               Position longPosition, Position shortPosition) {

        changesData.setLongAllocatedAmount(scale(longPosition.getAllocatedAmount()));
        changesData.setShortAllocatedAmount(scale(shortPosition.getAllocatedAmount()));

        BigDecimal totalInvestmentUSDT = scale(longPosition.getAllocatedAmount().add(shortPosition.getAllocatedAmount()));
        changesData.setTotalInvestmentUSDT(totalInvestmentUSDT);

        changesData.setLongChanges(scale(longPosition.getUnrealizedPnLPercent()));
        changesData.setShortChanges(scale(shortPosition.getUnrealizedPnLPercent()));

        changesData.setProfitUSDTChanges(netPnlUSDT);
        changesData.setProfitPercentChanges(netPnlPercent);

        log.info("Профит: {} USDT ({} %) из {}", netPnlUSDT, netPnlPercent, isPositionsClosed ? "закрытых позиций" : "открытых позиций");

        return getStatistics(pairData, changesData);
    }

    private ChangesData getStatistics(PairData pairData, ChangesData changesData) {
        BigDecimal zScoreEntry = BigDecimal.valueOf(pairData.getZScoreEntry());
        BigDecimal zScoreCurrent = BigDecimal.valueOf(pairData.getZScoreCurrent());
        changesData.setZScoreChanges(scale(zScoreCurrent.subtract(zScoreEntry)));

        long currentTimeInMinutes = calculateTimeInMinutes(pairData.getEntryTime());
        ProfitExtremums profitExtremums = updateProfitExtremums(changesData, currentTimeInMinutes);

        updateExtremumValues(pairData, changesData, changesData.getLongChanges(), changesData.getShortChanges(),
                BigDecimal.valueOf(pairData.getZScoreCurrent()), BigDecimal.valueOf(pairData.getCorrelationCurrent()));

        changesData.setMinProfitChanges(profitExtremums.minProfit());
        changesData.setMaxProfitChanges(profitExtremums.maxProfit());
        changesData.setTimeInMinutesSinceEntryToMax(profitExtremums.timeToMax());
        changesData.setTimeInMinutesSinceEntryToMin(profitExtremums.timeToMin());

        logFinalResults(pairData, changesData);

        return changesData;
    }

    private long calculateTimeInMinutes(long entryTime) {
        return (System.currentTimeMillis() - entryTime) / MILLISECONDS_IN_MINUTE;
    }

    private ProfitExtremums updateProfitExtremums(ChangesData changesData, long currentTimeInMinutes) {
        BigDecimal currentProfit = changesData.getProfitPercentChanges() != null ? changesData.getProfitPercentChanges() : BigDecimal.ZERO;
        MaxProfitResult max = updateMaxProfit(currentProfit, changesData.getMaxProfitChanges(), changesData.getTimeInMinutesSinceEntryToMax(), currentTimeInMinutes);
        MinProfitResult min = updateMinProfit(currentProfit, changesData.getMinProfitChanges(), changesData.getTimeInMinutesSinceEntryToMin(), currentTimeInMinutes);
        return new ProfitExtremums(max.maxProfit(), min.minProfit(), max.timeToMax(), min.timeToMin(), currentProfit);
    }

    private MaxProfitResult updateMaxProfit(BigDecimal current, BigDecimal max, long timeToMax, long now) {
        return (max == null || current.compareTo(max) > 0) ? new MaxProfitResult(current, now) : new MaxProfitResult(max, timeToMax);
    }

    private MinProfitResult updateMinProfit(BigDecimal current, BigDecimal min, long timeToMin, long now) {
        return (min == null || current.compareTo(min) < 0) ? new MinProfitResult(current, now) : new MinProfitResult(min, timeToMin);
    }

    private void logFinalResults(PairData pairData, ChangesData changesData) {
        log.info("📊 LONG {}: Entry: {}, Current: {}, Changes: {} %", pairData.getLongTicker(), pairData.getLongTickerEntryPrice(), changesData.getLongCurrentPrice(), changesData.getLongChanges());
        log.info("📉 SHORT {}: Entry: {}, Current: {}, Changes: {} %", pairData.getShortTicker(), pairData.getShortTickerEntryPrice(), changesData.getShortCurrentPrice(), changesData.getShortChanges());
        log.info("💰 Текущий профит: {} USDT ({} %)", changesData.getProfitUSDTChanges(), changesData.getProfitPercentChanges());
        log.info("📈 Max profit: {} %, Min profit: {} %", changesData.getMaxProfitChanges(), changesData.getMinProfitChanges()); //todo постоянно перезаписываются и не сохраняют экстремумы!
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

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
    }

    private BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING_MODE) : null;
    }

    private record ProfitExtremums(BigDecimal maxProfit, BigDecimal minProfit, long timeToMax, long timeToMin, BigDecimal currentProfit) {
    }

    private record MaxProfitResult(BigDecimal maxProfit, long timeToMax) {
    }

    private record MinProfitResult(BigDecimal minProfit, long timeToMin) {
    }
}
