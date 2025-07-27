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
            return BigDecimal.ZERO;
        }

        // Используем фиксированный размер позиции из настроек
        BigDecimal totalAllocation = BigDecimal.valueOf(settings.getMaxShortMarginSize()).add(BigDecimal.valueOf(settings.getMaxLongMarginSize()));

        log.info("💰 Расчет размера позиций: общая аллокация {}$ (без учета плеча)", totalAllocation);

        // Не больше доступного баланса
        BigDecimal resultSize = totalAllocation.min(portfolio.getAvailableBalance());

        log.info("💰 Итоговый размер позиций: {}$ (ограничен балансом: {}$)", resultSize, portfolio.getAvailableBalance());
        return resultSize;
    }

}
