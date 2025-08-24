package com.example.core.common.utils;

import com.example.core.common.dto.TradePairsStatisticsDto;
import com.example.core.common.model.PairData;
import com.example.core.common.model.TradeStatus;
import com.example.core.core.repositories.PairDataRepository;
import com.example.core.core.services.ExitReasonType;
import com.example.core.core.services.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
@Order(1)
public class DbTestDataInitializer {
    private final PairDataRepository pairDataRepository;
    private final StatisticsService statisticsService;

    //    @PostConstruct
    public void initialize() {
        // Пример: сгенерировать 10 рандомных PairData
        for (int i = 0; i < 1; i++) {
            PairData pairData = generateRandomPairData();
            pairDataRepository.save(pairData);
        }

        TradePairsStatisticsDto tradePairsStatisticsDto = statisticsService.collectStatistics();
        System.out.println("🚀 Exit by manually today: " + tradePairsStatisticsDto.getExitByManuallyToday());
    }

    private PairData generateRandomPairData() {
        PairData pairData = new PairData();
        pairData.setEntryTime(System.currentTimeMillis());

        double portfolioBefore = randomBetween(800, 2000);
        double change = randomBetween(-0.2, 0.3); // от -20% до +30%
        double portfolioAfter = portfolioBefore * (1 + change);

        BigDecimal before = BigDecimal.valueOf(portfolioBefore);
        BigDecimal after = BigDecimal.valueOf(portfolioAfter);
        BigDecimal profit = after.subtract(before);
        BigDecimal percent = profit.divide(before, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));

        pairData.setPortfolioBeforeTradeUSDT(before);
        pairData.setPortfolioAfterTradeUSDT(after);
        pairData.setProfitUSDTChanges(profit);
        pairData.setProfitPercentChanges(percent);

        pairData.setMinutesToMinProfitPercent(randomInt(10, 200));
        pairData.setMinProfitPercentChanges(BigDecimal.valueOf(randomBetween(-50, 0)).setScale(2, BigDecimal.ROUND_HALF_UP));

        pairData.setMinutesToMaxProfitPercent(randomInt(10, 200));
        pairData.setMaxProfitPercentChanges(BigDecimal.valueOf(randomBetween(0, 60)).setScale(2, BigDecimal.ROUND_HALF_UP));

        pairData.setStatus(TradeStatus.CLOSED);
        pairData.setExitReason(ExitReasonType.EXIT_REASON_MANUALLY.name());

        return pairData;
    }

    private double randomBetween(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private int randomInt(int min, int max) {
        return (int) (min + Math.random() * (max - min));
    }
}
