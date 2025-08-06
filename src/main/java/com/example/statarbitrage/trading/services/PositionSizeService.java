package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.model.Portfolio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionSizeService {

    public BigDecimal calculatePositionSize(TradingProvider provider, Settings settings) {
        Portfolio portfolio = provider.getPortfolio();
        if (portfolio == null) {
            log.warn("⚠️ Портфель отсутствует у провайдера, размер позиции = 0");
            return BigDecimal.ZERO;
        }

        BigDecimal maxShort = BigDecimal.valueOf(settings.getMaxShortMarginSize());
        BigDecimal maxLong = BigDecimal.valueOf(settings.getMaxLongMarginSize());
        BigDecimal totalAllocation = maxShort.add(maxLong);

        log.debug("💰 Расчет размера позиций: общая аллокация {} USDT (без учета плеча)", totalAllocation);

        BigDecimal availableBalance = portfolio.getAvailableBalance();
        BigDecimal resultSize = totalAllocation.min(availableBalance);

        log.debug("💰 Итоговый размер позиций: {} USDT (ограничен балансом: {} USDT)", resultSize, availableBalance);
        return resultSize;
    }

}
