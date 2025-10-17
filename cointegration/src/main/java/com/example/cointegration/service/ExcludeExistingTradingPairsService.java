package com.example.cointegration.service;

import com.example.cointegration.repositories.PairRepository;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.PairType;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcludeExistingTradingPairsService {

    private final PairRepository tradingPairRepository;

    /**
     * Исключает из списка ZScoreData те пары, которые уже торгуются
     */
    public void exclude(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            log.debug("Список ZScoreData пуст, пропускаем исключение торговых пар.");
            return;
        }

        List<Pair> tradingPairs = tradingPairRepository.findByTypeAndStatusOrderByCreatedAtDesc(PairType.TRADING, TradeStatus.TRADING);
        if (tradingPairs.isEmpty()) {
            log.debug("Нет активных торговых пар, все ZScoreData будут использоваться.");
            return;
        }

        Set<String> existingKeys = tradingPairs.stream()
                .map(pair -> buildKey(pair.getTickerA(), pair.getTickerB()))
                .collect(Collectors.toSet());

        int beforeSize = zScoreDataList.size();

        zScoreDataList.removeIf(z ->
                existingKeys.contains(buildKey(z.getUnderValuedTicker(), z.getOverValuedTicker()))
        );

        int removed = beforeSize - zScoreDataList.size();
        if (removed > 0) {
            log.info("Исключено {} уже торгующихся пар из ZScoreData", removed);
        } else {
            log.debug("Нет совпадений с активными торговыми парами — ничего не исключено.");
        }
    }

    /**
     * Строит уникальный ключ пары, не зависящий от порядка тикеров
     */
    private String buildKey(String ticker1, String ticker2) {
        return Stream.of(ticker1, ticker2)
                .sorted()
                .collect(Collectors.joining("-"));
    }
}
