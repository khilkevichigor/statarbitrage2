package com.example.core.services;

import com.example.core.repositories.PairRepository;
import com.example.shared.enums.PairType;
import com.example.shared.enums.StabilityRating;
import com.example.shared.models.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы с постоянным списком стабильных пар для мониторинга
 */
@Slf4j
@Service
public class StablePairsService {

    private final PairRepository pairRepository;

    @Autowired
    public StablePairsService(PairRepository pairRepository) {
        this.pairRepository = pairRepository;
    }

    /**
     * Получить все стабильные пары из постоянного списка мониторинга
     * @return список пар в мониторинге
     */
    public List<Pair> getStablePairsInMonitoring() {
        log.info("🔍 Получение стабильных пар из постоянного списка мониторинга");
        
        List<Pair> monitoringPairs = pairRepository.findStablePairsInMonitoring();
        
        log.info("✅ Найдено {} стабильных пар в постоянном списке мониторинга", monitoringPairs.size());
        
        return monitoringPairs;
    }

    /**
     * Получить стабильные пары из постоянного списка мониторинга с указанными рейтингами (enum)
     * @param ratings список рейтингов для фильтрации
     * @return список пар в мониторинге с указанными рейтингами
     */
    public List<Pair> getStablePairsInMonitoringByStabilityRatings(List<StabilityRating> ratings) {
        log.info("🔍 Получение стабильных пар из постоянного списка мониторинга с рейтингами: {}", ratings);
        
        List<Pair> monitoringPairs = pairRepository.findStablePairsInMonitoringByStabilityRatings(ratings);
        
        log.info("✅ Найдено {} стабильных пар в постоянном списке мониторинга с рейтингами {}", 
                monitoringPairs.size(), ratings);
        
        return monitoringPairs;
    }

    /**
     * Получить стабильные пары из постоянного списка мониторинга с указанными рейтингами (строки) - для обратной совместимости
     * @deprecated Используйте {@link #getStablePairsInMonitoringByStabilityRatings(List)} с enum
     */
    @Deprecated
    public List<Pair> getStablePairsInMonitoringByRatings(List<String> ratings) {
        List<StabilityRating> enumRatings = ratings.stream()
                .map(StabilityRating::fromString)
                .toList();
        return getStablePairsInMonitoringByStabilityRatings(enumRatings);
    }

    /**
     * Получить хорошие стабильные пары из постоянного списка мониторинга (MARGINAL, GOOD и EXCELLENT)
     * @return список пар в мониторинге с хорошими рейтингами
     */
    public List<Pair> getGoodStablePairsInMonitoring() {
        List<StabilityRating> goodRatings = List.of(
                StabilityRating.MARGINAL, 
                StabilityRating.GOOD, 
                StabilityRating.EXCELLENT
        );
        log.info("🔍 Получение хороших стабильных пар из постоянного списка мониторинга: {}", goodRatings);
        
        return getStablePairsInMonitoringByStabilityRatings(goodRatings);
    }

    /**
     * Создать зеркальные пары для списка исходных пар
     * @param originalPairs исходные пары
     * @return список всех пар (исходные + зеркальные)
     */
    public List<Pair> createPairsWithMirrors(List<Pair> originalPairs) {
        log.info("🪞 Создание зеркальных пар для {} исходных пар", originalPairs.size());
        
        List<Pair> allPairs = new ArrayList<>(originalPairs);
        
        for (Pair originalPair : originalPairs) {
            Pair mirrorPair = createMirrorPair(originalPair);
            allPairs.add(mirrorPair);
            
            log.info("🪞 Создана зеркальная пара: {} -> {}",
                    originalPair.getPairName(), mirrorPair.getPairName());
        }
        
        log.info("✅ Создано {} пар с зеркальными (исходных: {}, зеркальных: {})", 
                allPairs.size(), originalPairs.size(), originalPairs.size());
        
        return allPairs;
    }

    /**
     * Создать зеркальную пару для исходной пары
     * @param originalPair исходная пара
     * @return зеркальная пара
     */
    private Pair createMirrorPair(Pair originalPair) {
        return Pair.builder()
                .type(PairType.STABLE)
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
     * Получить названия пар для использования в анализе zScore (только с хорошими рейтингами)
     * @return список названий пар (исходные + зеркальные)
     */
    public List<String> getPairNamesForZScoreAnalysis() {
        log.info("📊 Получение названий пар для анализа zScore (только GOOD и EXCELLENT)");
        
        List<Pair> monitoringPairs = getGoodStablePairsInMonitoring();
        List<Pair> allPairs = createPairsWithMirrors(monitoringPairs);
        
        List<String> pairNames = allPairs.stream()
                .map(Pair::getPairName)
                .collect(Collectors.toList());
        
        log.info("📊 Подготовлено {} названий пар для анализа zScore: {}", 
                pairNames.size(), pairNames);
        
        return pairNames;
    }

    /**
     * Получить все пары (исходные + зеркальные) для анализа zScore (только с хорошими рейтингами)
     * @return список всех пар для анализа
     */
    public List<Pair> getPairsForZScoreAnalysis() {
        log.info("📊 Получение всех пар для анализа zScore (только GOOD и EXCELLENT)");
        
        List<Pair> monitoringPairs = getGoodStablePairsInMonitoring();
        List<Pair> allPairs = createPairsWithMirrors(monitoringPairs);
        
        log.info("📊 Подготовлено {} пар для анализа zScore (исходных: {}, зеркальных: {})", 
                allPairs.size(), monitoringPairs.size(), monitoringPairs.size());
        
        return allPairs;
    }

    /**
     * Проверить, есть ли стабильные пары в мониторинге (с хорошими рейтингами)
     * @return true если есть пары в мониторинге с рейтингами GOOD или EXCELLENT
     */
    public boolean hasStablePairsInMonitoring() {
        List<Pair> monitoringPairs = getGoodStablePairsInMonitoring();
        boolean hasPairs = !monitoringPairs.isEmpty();
        
        log.info("🔍 Проверка наличия хороших стабильных пар в мониторинге: {}", hasPairs);
        
        return hasPairs;
    }

    /**
     * Получить количество стабильных пар в мониторинге
     * @return количество пар в мониторинге
     */
    public int getStablePairsInMonitoringCount() {
        List<Pair> monitoringPairs = getStablePairsInMonitoring();
        int count = monitoringPairs.size();
        
        log.info("📊 Количество стабильных пар в мониторинге: {}", count);
        
        return count;
    }
}