package com.example.statarbitrage.testdata;

import com.example.statarbitrage.common.dto.TradePairsStatisticsDto;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import com.example.statarbitrage.core.services.ExitReasonType;
import com.example.statarbitrage.core.services.StatisticsService;
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
        PairData pairData = new PairData();
        pairData.setEntryTime(System.currentTimeMillis());
        pairData.setProfitPercentChanges(BigDecimal.valueOf(1.0));
        pairData.setExitReason(ExitReasonType.EXIT_REASON_MANUALLY.getDescription());
        pairDataRepository.save(pairData);

        TradePairsStatisticsDto tradePairsStatisticsDto = statisticsService.collectStatistics();
        System.out.println(tradePairsStatisticsDto.getExitByManuallyToday());
    }
}
