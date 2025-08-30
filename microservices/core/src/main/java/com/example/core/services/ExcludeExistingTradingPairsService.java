package com.example.core.services;

import com.example.core.repositories.TradingPairRepository;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradingPair;
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

    private final TradingPairRepository tradingPairRepository;

    /**
     * Исключает из списка ZScoreData те пары, которые уже торгуются
     */
    public void exclude(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            log.debug("Список ZScoreData пуст, пропускаем исключение торговых пар.");
            return;
        }

        List<TradingPair> tradingPairs = tradingPairRepository.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        if (tradingPairs.isEmpty()) {
            log.debug("Нет активных торговых пар, все ZScoreData будут использоваться.");
            return;
        }

        Set<String> existingKeys = tradingPairs.stream()
                .map(pair -> buildKey(pair.getLongTicker(), pair.getShortTicker()))
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
