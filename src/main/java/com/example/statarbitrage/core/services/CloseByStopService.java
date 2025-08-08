package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.Positioninfo;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.example.statarbitrage.common.utils.BigDecimalUtil.safeGet;
import static com.example.statarbitrage.common.utils.BigDecimalUtil.safeScale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseByStopService {

    private final TradingIntegrationService tradingIntegrationServiceImpl;

    public boolean isShouldCloseByStop(PairData pairData, Settings settings) {
        if (!settings.isUseExitStop()) {
            return false;
        }

        Positioninfo positionsInfo = tradingIntegrationServiceImpl.getPositionInfo(pairData);

        log.debug("--> getFromPositions: НАЧАЛО для пары {}", pairData.getPairName());
        Position longPosition = positionsInfo.getLongPosition();
        Position shortPosition = positionsInfo.getShortPosition();
        boolean isPositionsClosed = positionsInfo.isPositionsClosed();
        log.debug("Статус позиций: isPositionsClosed = {}", isPositionsClosed);

        log.debug("Текущие цены: LONG {} = {}, SHORT {} = {}", longPosition.getSymbol(), longPosition.getCurrentPrice(), shortPosition.getSymbol(), shortPosition.getCurrentPrice());

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
                longPosition.getUnrealizedPnLUSDT(), longPosition.getUnrealizedPnLPercent(),
                longPosition.getOpeningFees(), longPosition.getFundingFees()
        );
        log.debug("➡️ Шорт: allocated = {}, unrealizedPnL = {} USDT ({} %), openingFee = {}, fundingFee = {}",
                shortAlloc,
                shortPosition.getUnrealizedPnLUSDT(), shortPosition.getUnrealizedPnLPercent(),
                shortPosition.getOpeningFees(), shortPosition.getFundingFees()
        );
        log.debug("➡️ Суммарные комиссии по паре {}: {}", pairData.getPairName(), totalFees);

        return totalUnrealizedPnlPercent.doubleValue() <= settings.getExitStop();
    }

}
