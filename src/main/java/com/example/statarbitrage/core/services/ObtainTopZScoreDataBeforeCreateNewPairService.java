package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.utils.NumberFormatter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObtainTopZScoreDataBeforeCreateNewPairService {

    private final PairDataService pairDataService;
    private final PixelSpreadService pixelSpreadService;

    /**
     * Новая версия получения лучшей пары (КОНФИГУРИРУЕМЫЕ ВЕСА!)
     * <p>
     * Использует ПОЛНУЮ систему оценки качества пар:
     * - НАСТРАИВАЕМЫЕ ВЕСА через UI настройки
     * - ПИКСЕЛЬНЫЙ СПРЕД с равным весом коинтеграции (25 очков по умолчанию)
     * - Z-Score + Пиксельный спред + Коинтеграция + Качество модели + Статистика + Бонусы
     * - Максимальный скор: сумма всех весов (настраивается)
     */
    public Optional<ZScoreData> getBestZScoreData(Settings settings, List<ZScoreData> dataList, Map<String, List<Candle>> candlesMap) {
        if (dataList == null || dataList.isEmpty()) {
            return Optional.empty();
        }

        log.info("🎯 ОБЪЕДИНЕННАЯ СИСТЕМА: Фильтруем и выбираем лучшую пару из {} кандидатов за один проход!", dataList.size());

        // Сначала фильтруем данные (встроенная фильтрация)
        List<ZScoreData> filteredList = new ArrayList<>();
        Map<String, Integer> filterStats = new HashMap<>();
        double expected = settings.getExpectedZParamsCount();

        for (ZScoreData data : dataList) {
            String reason = shouldFilterPair(data, settings, expected);
            if (reason != null) {
                filterStats.merge(reason, 1, Integer::sum);
                log.debug("⚠️ Отфильтровано {}/{} — {}",
                        data.getUnderValuedTicker(), data.getOverValuedTicker(), reason);
            } else {
                filteredList.add(data);
                // Рассчитываем качественный скор для лога
                double qualityScore = calculatePairQualityScore(data, settings, candlesMap);
                log.info("📊 Пара {}/{} прошла фильтрацию. Качественный скор: {}",
                        data.getUnderValuedTicker(), data.getOverValuedTicker(),
                        NumberFormatter.format(qualityScore, 2));
            }
        }

        log.info("✅ После фильтрации осталось {} из {} пар", filteredList.size(), dataList.size());

        // Статистика фильтрации
        filterStats.forEach((reason, count) ->
                log.debug("📊 Фильтрация - {}: {} пар", reason, count));

        if (filteredList.isEmpty()) {
            log.warn("❌ Нет подходящих пар после фильтрации");
            return Optional.empty();
        }

        // Теперь оцениваем отфильтрованные пары
        List<PairCandidate> candidates = new ArrayList<>();

        for (ZScoreData z : filteredList) {
            PairCandidate candidate = evaluatePair(z, settings, candlesMap);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            log.warn("❌ Нет подходящих пар после оценки");
            return Optional.empty();
        }

        // Сортируем по композитному скору (лучший первый)
        candidates.sort(Comparator.comparingDouble(PairCandidate::getCompositeScore).reversed());

        PairCandidate best = candidates.get(0);
        log.info("🏆 ОБЪЕДИНЕННАЯ СИСТЕМА: Выбрана лучшая пара {}/{} с полным скором {}! Детали: Z-Score={}, Корр={}, P-Value(corr)={}, P-Value(coint)={}, R²={}",
                best.getData().getUnderValuedTicker(),
                best.getData().getOverValuedTicker(),
                NumberFormatter.format(best.getCompositeScore(), 2),
                NumberFormatter.format(best.getZScore(), 2),
                NumberFormatter.format(best.getCorrelation(), 3),
                NumberFormatter.format(best.getPValue(), 4),
                NumberFormatter.format(best.getAdfValue(), 4),
                NumberFormatter.format(best.getRSquared(), 3)
        );

        // Логируем топ-3 для анализа
        logTopCandidates(candidates);

        return Optional.of(best.getData());
    }

    /**
     * Оценивает пару с использованием НОВОЙ системы оценки качества
     * Упрощенный метод - вся логика скоринга в FilterIncompleteZScoreParamsServiceV2
     * ДОБАВЛЕНА ПРОВЕРКА на минимальный Z-Score
     */
    private PairCandidate evaluatePair(ZScoreData z, Settings settings, Map<String, List<Candle>> candlesMap) {
        List<ZScoreParam> params = z.getZScoreHistory();

        double zVal, pValue, adf, corr, rSquared;

        if (params != null && !params.isEmpty()) {
            // Старый формат с детальными параметрами
            ZScoreParam last = params.get(params.size() - 1);
            zVal = last.getZscore();
            pValue = last.getPvalue();
            adf = last.getAdfpvalue();
            corr = last.getCorrelation();
            rSquared = z.getAvgRSquared() != null ? z.getAvgRSquared() : 0.0;
        } else {
            // Новый формат с агрегированными данными
            if (z.getLatestZScore() == null || z.getPearsonCorr() == null) {
                log.warn("⚠️ Пропускаем пару с отсутствующими данными: {}/{}",
                        z.getUnderValuedTicker(), z.getOverValuedTicker());
                return null;
            }

            zVal = z.getLatestZScore();
            corr = z.getPearsonCorr();
            pValue = z.getPearsonCorrPValue() != null ? z.getPearsonCorrPValue() : 0.0;
            adf = z.getJohansenCointPValue() != null ? z.getJohansenCointPValue() : 0.0;
            rSquared = z.getAvgRSquared() != null ? z.getAvgRSquared() : 0.0;
        }

        // ====== ПОЛНЫЙ КАЛКУЛЯТОР СКОРА с ПИКСЕЛЬНЫМ СПРЕДОМ ======
        // Используем полную систему скоринга с настраиваемыми весами включая пиксельный спред!

        double fullQualityScore = calculatePairQualityScore(z, settings, candlesMap);

        return new PairCandidate(z, fullQualityScore, zVal, corr, adf, pValue, rSquared);
    }

    /**
     * Логирует топ кандидатов для анализа
     */
    private void logTopCandidates(List<PairCandidate> candidates) {
        log.info("🏅 Топ-3 кандидата:");

        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            PairCandidate candidate = candidates.get(i);
            ZScoreData data = candidate.getData();

            String johansenStatus = "❌";
            if (data.getJohansenCointPValue() != null && data.getJohansenCointPValue() > 0) {
                johansenStatus = String.format("✅ (p=%.4f)", data.getJohansenCointPValue());
            }

            log.info("   {}. {}/{} -> Скор: {}, Z: {}, Корр: {}, R²: {}, Johansen: {}, ADF: {}",
                    i + 1,
                    data.getUnderValuedTicker(),
                    data.getOverValuedTicker(),
                    NumberFormatter.format(candidate.getCompositeScore(), 2),
                    NumberFormatter.format(candidate.getZScore(), 2),
                    NumberFormatter.format(candidate.getCorrelation(), 3),
                    NumberFormatter.format(candidate.getRSquared(), 3),
                    johansenStatus,
                    NumberFormatter.format(candidate.getAdfValue(), 4)
            );
        }
    }

    /**
     * Определяет должна ли быть отфильтрована пара (МИНИМАЛЬНАЯ ФИЛЬТРАЦИЯ)
     * Остались только критические проверки - остальное через скоринг!
     * Возвращает причину фильтрации или null если пара прошла критичные фильтры
     */
    private String shouldFilterPair(ZScoreData data, Settings settings, double expectedSize) {
        List<ZScoreParam> params = data.getZScoreHistory();
        String pairName = data.getUnderValuedTicker() + "/" + data.getOverValuedTicker();

        log.debug("⚙️ МИНИМАЛЬНАЯ фильтрация пары {} (основная оценка через скоринг):", pairName);

        // ====== КРИТИЧЕСКИЕ ПРОВЕРКИ (только обязательные!) ======

        // 1. Проверка наличия данных
        String reason = isDataMissing(data, params) ? "Отсутствуют данные Z-score" : null;
        if (reason != null) {
            log.debug("   ❌ {}: {}", pairName, reason);
            return reason;
        }

        // 2. Проверка тикеров
        reason = isTickersInvalid(data) ? "Некорректные тикеры" : null;
        if (reason != null) {
            log.debug("   ❌ {}: {}", pairName, reason);
            return reason;
        }

        // 3. Проверка Z-Score на положительность (ключевой фильтр!)
        double currentZScore = getLatestZScore(data, params);
        if (currentZScore <= 0) {
            reason = String.format("Отрицательный/нулевой Z-score: %.2f (нет сигнала)", currentZScore);
            log.debug("   ❌ {}: {}", pairName, reason);
            return reason;
        }
        log.debug("   ✅ {}: Положительный Z-score: {}", pairName, NumberFormatter.format(currentZScore, 2));

        return null; // Пара прошла критические проверки
    }

    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ФИЛЬТРАЦИИ ============

    private boolean isDataMissing(ZScoreData data, List<ZScoreParam> params) {
        // Проверяем наличие Z-score данных в любом формате
        if (params != null && !params.isEmpty()) {
            return false; // Старый формат - есть данные
        }
        return data.getLatestZScore() == null; // Новый формат - проверяем latest_zscore
    }

    private boolean isTickersInvalid(ZScoreData data) {
        return data.getUnderValuedTicker() == null ||
                data.getOverValuedTicker() == null ||
                data.getUnderValuedTicker().isEmpty() ||
                data.getOverValuedTicker().isEmpty() ||
                data.getUnderValuedTicker().equals(data.getOverValuedTicker());
    }

    private Double getAdfPValue(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            ZScoreParam lastParam = params.get(params.size() - 1);
            return lastParam.getAdfpvalue();
        } else {
            // Новый формат API - используем avgAdfPvalue для ADF теста
            return data.getAvgAdfPvalue();
        }
    }

    private Double getCorrelationPValue(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            ZScoreParam lastParam = params.get(params.size() - 1);
            return lastParam.getPvalue();
        } else {
            // Новый формат API
            return data.getPearsonCorrPValue();
        }
    }

    private double getLatestZScore(ZScoreData data, List<ZScoreParam> params) {
        if (params != null && !params.isEmpty()) {
            // Старый формат API
            return params.get(params.size() - 1).getZscore();
        } else if (data.getLatestZScore() != null) {
            // Новый формат API
            return data.getLatestZScore();
        } else {
            return 0.0;
        }
    }

    private Double getRSquared(ZScoreData data) {
        // Приоритет новому формату
        if (data.getAvgRSquared() != null) {
            return data.getAvgRSquared();
        }
        return null;
    }

    // ============ СИСТЕМА ОЦЕНКИ КАЧЕСТВА ПАР ============

    /**
     * Рассчитывает качественный скор пары с КОНФИГУРИРУЕМЫМИ ВЕСАМИ из Settings
     */
    public double calculatePairQualityScore(ZScoreData data, Settings settings, Map<String, List<Candle>> candlesMap) {
        double totalScore = 0.0;
        List<ZScoreParam> params = data.getZScoreHistory();
        String pairName = data.getUnderValuedTicker() + "/" + data.getOverValuedTicker();

        log.info("🎯 Рассчет качественного скора для {} с НАСТРАИВАЕМЫМИ весами", pairName);

        // ====== 1. Z-SCORE СИЛА (настраиваемый вес) ======
        if (settings.isUseZScoreScoring()) {
            double zScore = getLatestZScore(data, params);
            double maxWeight = settings.getZScoreScoringWeight();
            double zScorePoints = Math.min(Math.abs(zScore) * (maxWeight / 5.0), maxWeight); // Нормализуем по весу
            totalScore += zScorePoints;
            log.info("  🎯 Z-Score компонент: {} очков (Z-score={}, вес={})",
                    NumberFormatter.format(zScorePoints, 1), NumberFormatter.format(zScore, 2), maxWeight);
        }

        // ====== 2. ПИКСЕЛЬНЫЙ СПРЕД (настраиваемый вес, высокий приоритет!) ======
        if (settings.isUsePixelSpreadScoring()) {
            double pixelSpreadScore = calculatePixelSpreadScoreComponent(data, settings, candlesMap);
            totalScore += pixelSpreadScore;
            log.info("  📏 Пиксельный спред: {} очков (вес={})",
                    NumberFormatter.format(pixelSpreadScore, 1), settings.getPixelSpreadScoringWeight());
        }

        // ====== 3. КОИНТЕГРАЦИЯ (настраиваемый вес) ======
        if (settings.isUseCointegrationScoring()) {
            double cointegrationScore = calculateCointegrationScoreComponent(data, params, settings);
            totalScore += cointegrationScore;
            log.info("  🔬 Коинтеграция: {} очков (вес={})",
                    NumberFormatter.format(cointegrationScore, 1), settings.getCointegrationScoringWeight());
        }

        // ====== 4. КАЧЕСТВО МОДЕЛИ (настраиваемый вес) ======
        if (settings.isUseModelQualityScoring()) {
            double modelQualityScore = calculateModelQualityScoreComponent(data, params, settings);
            totalScore += modelQualityScore;
            log.info("  📊 Качество модели: {} очков (вес={})",
                    NumberFormatter.format(modelQualityScore, 1), settings.getModelQualityScoringWeight());
        }

        // ====== 5. СТАТИСТИЧЕСКАЯ ЗНАЧИМОСТЬ (настраиваемый вес) ======
        if (settings.isUseStatisticsScoring()) {
            double statisticalScore = calculateStatisticalSignificanceScoreComponent(data, params, settings);
            totalScore += statisticalScore;
            log.info("  📊 Статистика: {} очков (вес={})",
                    NumberFormatter.format(statisticalScore, 1), settings.getStatisticsScoringWeight());
        }

        // ====== 6. БОНУСЫ (настраиваемый вес) ======
        if (settings.isUseBonusScoring()) {
            double bonusScore = calculateBonusScoreComponent(data, settings);
            totalScore += bonusScore;
            log.info("  🎁 Бонусы: {} очков (вес={})",
                    NumberFormatter.format(bonusScore, 1), settings.getBonusScoringWeight());
        }

        log.info("🏆 Итоговый скор для {}: {} очков (НАСТРАИВАЕМЫЕ ВЕСА)", pairName, NumberFormatter.format(totalScore, 1));
        return totalScore;
    }

    /**
     * Расчет скора пиксельного спреда (НОВАЯ ВЕРСИЯ: с расчетом из candlesMap)
     */
    private double calculatePixelSpreadScoreComponent(ZScoreData data, Settings settings, Map<String, List<Candle>> candlesMap) {
        try {
            String longTicker = data.getUnderValuedTicker();  // undervalued = long
            String shortTicker = data.getOverValuedTicker(); // overvalued = short

            // Сначала пробуем найти существующую PairData (старая логика)
            var existingPairs = pairDataService.findByTickers(longTicker, shortTicker);

            if (!existingPairs.isEmpty()) {
                var pairData = existingPairs.get(0);

                // Получаем статистику пиксельного спреда из существующей пары
                double avgSpread = pixelSpreadService.getAveragePixelSpread(pairData);

                if (avgSpread > 0) {
                    double maxWeight = settings.getPixelSpreadScoringWeight();
                    double totalScore = calculateScoreFromPixelSpread(avgSpread, maxWeight);

                    log.info("    📏 Пиксельный спред (существующая пара): avg={}px → {} баллов",
                            String.format("%.1f", avgSpread), String.format("%.1f", totalScore));
                    return totalScore;
                }
            }

            // НОВАЯ ЛОГИКА: если существующей пары нет, вычисляем из candlesMap
            if (candlesMap != null && candlesMap.containsKey(longTicker) && candlesMap.containsKey(shortTicker)) {
                List<Candle> longCandles = candlesMap.get(longTicker);
                List<Candle> shortCandles = candlesMap.get(shortTicker);

                // Используем быстрый метод (только текущие цены) для лучшей производительности при скоринге
                double currentSpread = pixelSpreadService.calculateCurrentPixelSpreadFromCandles(
                        longCandles, shortCandles, longTicker, shortTicker);

                if (currentSpread > 0) {
                    double maxWeight = settings.getPixelSpreadScoringWeight();
                    double totalScore = calculateScoreFromPixelSpread(currentSpread, maxWeight);

                    log.info("    📏 Пиксельный спред (вычисленный текущий): {}px → {} баллов",
                            String.format("%.1f", currentSpread), String.format("%.1f", totalScore));
                    return totalScore;
                }
            }

            log.debug("    📏 Нет данных для пиксельного спреда пары {}/{}", longTicker, shortTicker);
            return 0.0; // Нет данных о пиксельном спреде

        } catch (Exception e) {
            log.warn("    📏 Ошибка расчета скора пиксельного спреда: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Вычисляет скор из значения пиксельного спреда
     * ИСПРАВЛЕНО: реалистичные диапазоны для криптовалютного арбитража
     */
    private double calculateScoreFromPixelSpread(double avgSpread, double maxWeight) {
        double scoreRatio;

        if (avgSpread < 0) {
            // Ошибка: отрицательный спред невозможен
            log.warn("    📏 ОШИБКА: отрицательный пиксельный спред {}px", String.format("%.1f", avgSpread));
            scoreRatio = 0.0;
        } else if (avgSpread > 720) {
            // Ошибка: слишком большой спред, возможно некорректные данные
            log.warn("    📏 ОШИБКА: слишком большой пиксельный спред {}px (>720px)", String.format("%.1f", avgSpread));
            scoreRatio = 0.0;
        } else if (avgSpread <= 240) {
            // Низкий спред: 0-240px - постепенный рост от 10% до 60%
            scoreRatio = 0.1 + (avgSpread / 240.0) * 0.5; // 10% - 60%
        } else if (avgSpread <= 480) {
            // Нормальный спред: 240-480px - оптимальный диапазон 60%-100%
            scoreRatio = 0.6 + ((avgSpread - 240) / 240.0) * 0.4; // 60% - 100%
        } else {
            // Повышенный риск: 480-720px - убывающий от 100% до 30%
            scoreRatio = 1.0 - ((avgSpread - 480) / 240.0) * 0.7; // 100% - 30%
        }

        double score = maxWeight * scoreRatio;

        log.debug("    📏 Пиксельный спред {}px → ratio={:.1%} → {} баллов",
                String.format("%.1f", avgSpread), scoreRatio, String.format("%.1f", score));

        return score;
    }

    /**
     * Динамическая система весов для коинтеграции
     */
    private double calculateCointegrationScoreComponent(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        boolean hasJohansen = data.getJohansenCointPValue() != null && data.getJohansenCointPValue() > 0;
        boolean hasAdf = getAdfPValue(data, params) != null && getAdfPValue(data, params) > 0;

        String pairName = data.getUnderValuedTicker() + "/" + data.getOverValuedTicker();

        if (!hasJohansen && !hasAdf) {
            log.info("  🔬 {}: Нет данных коинтеграции", pairName);
            return 0.0;
        }

        double maxWeight = settings.getCointegrationScoringWeight();
        double score = 0.0;

        if (hasJohansen && hasAdf) {
            // ОБА ТЕСТА ДОСТУПНЫ - равные веса по 50% от полного веса
            log.info("  🔬 {}: Динамические веса - оба теста ({}+{})", pairName, maxWeight / 2, maxWeight / 2);

            // Johansen (50% от веса)
            double johansenPValue = data.getJohansenCointPValue();
            double johansenScore = Math.max(0, (0.05 - johansenPValue) / 0.05) * (maxWeight / 2.0);
            score += johansenScore;

            // ADF (50% от веса)
            Double adfPValue = getAdfPValue(data, params);
            double adfScore = Math.max(0, (0.05 - Math.min(adfPValue, 0.05)) / 0.05) * (maxWeight / 2.0);
            score += adfScore;

            log.info("    Johansen: {} очков (p-value={})",
                    NumberFormatter.format(johansenScore, 1),
                    NumberFormatter.format(johansenPValue, 6));
            log.info("    ADF: {} очков (p-value={})",
                    NumberFormatter.format(adfScore, 1),
                    NumberFormatter.format(adfPValue, 6));

        } else if (hasJohansen) {
            // ТОЛЬКО JOHANSEN - полный вес
            log.info("  🔬 {}: Динамические веса - только Johansen ({})", pairName, maxWeight);

            double johansenPValue = data.getJohansenCointPValue();
            double johansenScore = Math.max(0, (0.05 - johansenPValue) / 0.05) * maxWeight;
            score += johansenScore;

            log.info("     Johansen: {} очков (p-value={})",
                    NumberFormatter.format(johansenScore, 1),
                    NumberFormatter.format(johansenPValue, 6));

        } else if (hasAdf) {
            // ТОЛЬКО ADF - полный вес
            log.info("  🔬 {}: Динамические веса - только ADF ({})", pairName, maxWeight);

            Double adfPValue = getAdfPValue(data, params);
            double adfScore = Math.max(0, (0.05 - Math.min(adfPValue, 0.05)) / 0.05) * maxWeight;
            score += adfScore;

            log.info("    ADF: {} очков (p-value={})",
                    NumberFormatter.format(adfScore, 1),
                    NumberFormatter.format(adfPValue, 6));
        }

        // Небольшой бонус за trace statistic (только если есть Johansen) - 5% от веса
        if (hasJohansen && data.getJohansenTraceStatistic() != null && data.getJohansenCriticalValue95() != null) {
            if (data.getJohansenTraceStatistic() > data.getJohansenCriticalValue95()) {
                double traceBonus = maxWeight * 0.05; // 5% от основного веса
                score += traceBonus;
                log.info("    Бонус trace statistic: +{} очков", NumberFormatter.format(traceBonus, 1));
            }
        }

        return score;
    }

    private double calculateModelQualityScoreComponent(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        double maxWeight = settings.getModelQualityScoringWeight();
        double score = 0.0;

        // R-squared (75% от веса)
        Double rSquared = getRSquared(data);
        if (rSquared != null && rSquared > 0) {
            score += rSquared * (maxWeight * 0.75); // 75% от веса при R² = 1.0
        }

        // Стабильность (25% от веса)
        if (data.getStablePeriods() != null && data.getTotalObservations() != null && data.getTotalObservations() > 0) {
            double stabilityRatio = (double) data.getStablePeriods() / data.getTotalObservations();
            score += stabilityRatio * (maxWeight * 0.25); // 25% от веса
        }

        return score;
    }

    private double calculateStatisticalSignificanceScoreComponent(ZScoreData data, List<ZScoreParam> params, Settings settings) {
        double maxWeight = settings.getStatisticsScoringWeight();
        double score = 0.0;

        // Pearson корреляция P-value (50% от веса)
        Double pearsonPValue = getCorrelationPValue(data, params);
        if (pearsonPValue != null && pearsonPValue >= 0) {
            score += Math.max(0, (0.05 - Math.min(pearsonPValue, 0.05)) / 0.05) * (maxWeight * 0.5);
        }

        // Корреляция сила (50% от веса)
        if (data.getPearsonCorr() != null) {
            double absCorr = Math.abs(data.getPearsonCorr());
            score += Math.min(absCorr, 1.0) * (maxWeight * 0.5);
        }

        return score;
    }

    private double calculateBonusScoreComponent(ZScoreData data, Settings settings) {
        double maxWeight = settings.getBonusScoringWeight();
        double bonusScore = 0.0;

        // Бонус за полноту данных Johansen (30% от веса)
        if (data.getJohansenCointPValue() != null && data.getJohansenTraceStatistic() != null) {
            bonusScore += maxWeight * 0.3;
        }

        // Бонус за стабильность (20% от веса)
        if (data.getStablePeriods() != null && data.getTotalObservations() != null) {
            bonusScore += maxWeight * 0.2;
        }

        // Бонус за качество модели (30% от веса)
        if (data.getAvgRSquared() != null && data.getAvgRSquared() > 0.8) {
            bonusScore += maxWeight * 0.3;
        }

        return bonusScore;
    }

    /**
     * Класс для хранения кандидата с метриками
     */
    @Data
    private static class PairCandidate {
        // Getters
        private final ZScoreData data;
        private final double compositeScore;
        private final double zScore;
        private final double correlation;
        private final double adfValue;
        private final double pValue;
        private final double rSquared;

        public PairCandidate(ZScoreData data, double compositeScore, double zScore,
                             double correlation, double adfValue, double pValue, double rSquared) {
            this.data = data;
            this.compositeScore = compositeScore;
            this.zScore = zScore;
            this.correlation = correlation;
            this.adfValue = adfValue;
            this.pValue = pValue;
            this.rSquared = rSquared;
        }
    }
}
