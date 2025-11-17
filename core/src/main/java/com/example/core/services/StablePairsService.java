package com.example.core.services;

import com.example.core.repositories.PairRepository;
import com.example.shared.enums.PairType;
import com.example.shared.enums.StabilityRating;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис для работы с постоянным списком стабильных пар для мониторинга
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StablePairsService {
    private final PairRepository pairRepository;

    /**
     * Создать зеркальную пару для исходной пары
     *
     * @param originalPair исходная пара
     * @return зеркальная пара
     */
    public Pair createMirrorPair(Pair originalPair) {
        return Pair.builder()
                .type(originalPair.getType())
                .status(originalPair.getStatus())
                .tickerA(originalPair.getTickerB()) // Меняем местами тикеры
                .tickerB(originalPair.getTickerA())
                .pairName(originalPair.getTickerB() + "/" + originalPair.getTickerA()) // Меняем местами в названии
                .totalScore(originalPair.getTotalScore())
                .stabilityRating(originalPair.getStabilityRating())
                .isTradeable(originalPair.isTradeable())
                .dataPoints(originalPair.getDataPoints())
                .candleCount(originalPair.getCandleCount())
                .analysisTimeSeconds(originalPair.getAnalysisTimeSeconds())
                .timeframe(originalPair.getTimeframe())
                .period(originalPair.getPeriod())
                .searchDate(originalPair.getSearchDate())
                .isInMonitoring(false) // Зеркальные пары не добавляем в мониторинг
                .searchSettings(originalPair.getSearchSettings())
                .analysisResults(originalPair.getAnalysisResults())
                .build();
    }

    /**
     * Универсальный метод получения стабильных пар с фильтрами
     *
     * @param includeMonitoring включать ли пары в мониторинге
     * @param includeFound      включать ли найденные пары (не в мониторинге)
     * @param ratings           список рейтингов для фильтрации (null для всех рейтингов)
     * @return список стабильных пар с учетом фильтров
     */
    public List<Pair> getStablePairsWithFilters(boolean includeMonitoring, boolean includeFound, List<StabilityRating> ratings) {
        log.info("🔍 Получение стабильных пар с фильтрами: мониторинг={}, найденные={}, рейтинги={}",
                includeMonitoring, includeFound, ratings);

        List<Pair> filteredPairs = pairRepository.findStablePairsWithFilters(includeMonitoring, includeFound, ratings);

        log.info("✅ Найдено {} стабильных пар с указанными фильтрами", filteredPairs.size());

        return filteredPairs;
    }

    /**
     * Получить хорошие стабильные пары на основе настроек чекбоксов
     *
     * @param useMonitoring     использовать ли пары в мониторинге
     * @param useFound          использовать ли найденные пары
     * @param useScoreFiltering использовать ли фильтрацию по скору
     * @param minStabilityScore минимальный скор стабильности (используется только при useScoreFiltering=true)
     * @return список стабильных пар с хорошими рейтингами или скором
     */
    public List<Pair> getGoodStablePairsBySettings(boolean useMonitoring, boolean useFound,
                                                   boolean useScoreFiltering, int minStabilityScore) {
        if (useScoreFiltering) {
            return getStablePairsByScore(useMonitoring, useFound, minStabilityScore);
        } else {
            // Использование старой логики с рейтингами
            List<StabilityRating> goodRatings = List.of(
                    StabilityRating.MARGINAL,
                    StabilityRating.GOOD,
                    StabilityRating.EXCELLENT
            );

            log.debug("🔍 Получение хороших стабильных пар по рейтингам: мониторинг={}, найденные={}, рейтинги={}",
                    useMonitoring, useFound, goodRatings);

            return getStablePairsWithFilters(useMonitoring, useFound, goodRatings);
        }
    }

    /**
     * Получить стабильные пары на основе минимального скора
     *
     * @param includeMonitoring включать ли пары в мониторинге
     * @param includeFound      включать ли найденные пары (не в мониторинге)
     * @param minScore          минимальный скор стабильности
     * @return список стабильных пар с скором больше или равно minScore
     */
    public List<Pair> getStablePairsByScore(boolean includeMonitoring, boolean includeFound, int minScore) {
        log.info("🔍 Получение стабильных пар по скору: мониторинг={}, найденные={}, минимальный скор={}",
                includeMonitoring, includeFound, minScore);

        List<Pair> filteredPairs = pairRepository.findStablePairsByScore(includeMonitoring, includeFound, minScore);

        log.info("✅ Найдено {} стабильных пар с скором >= {}", filteredPairs.size(), minScore);

        return filteredPairs;
    }
}