package com.example.core.services;

import com.example.shared.dto.ZScoreData;
import com.example.shared.models.Candle;
import com.example.shared.models.Settings;
import com.example.shared.models.ZScoreParam;
import com.example.shared.utils.NumberFormatter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObtainTopZScoreDataBeforeCreateNewPairService {

    private final TradingPairService tradingPairService;
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

        log.debug("🎯 ОБЪЕДИНЕННАЯ СИСТЕМА: Фильтруем и выбираем лучшую пару из {} кандидатов за один проход!", dataList.size());

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
                log.debug("📊 Пара {}/{} прошла фильтрацию. Качественный скор: {}",
                        data.getUnderValuedTicker(), data.getOverValuedTicker(),
                        NumberFormatter.format(qualityScore, 2));
            }
        }

        log.debug("✅ После фильтрации осталось {} из {} пар", filteredList.size(), dataList.size());

        // Статистика фильтрации
        filterStats.forEach((reason, count) ->
                log.debug("📊 Фильтрация - {}: {} пар", reason, count));

        if (filteredList.isEmpty()) {
            log.debug("❌ Нет подходящих пар после фильтрации");
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
            log.debug("❌ Нет подходящих пар после оценки");
            return Optional.empty();
        }

        // Сортируем по композитному скору (лучший первый)
        candidates.sort(Comparator.comparingDouble(PairCandidate::getCompositeScore).reversed());

        PairCandidate best = candidates.get(0);
        log.debug("🏆 ОБЪЕДИНЕННАЯ СИСТЕМА: Выбрана лучшая пара {}/{} с полным скором {}! Детали: Z-Score={}, Корр={}, P-Value(corr)={}, P-Value(coint)={}, R²={}",
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
        log.debug("🏅 Топ-3 кандидата:");

        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            PairCandidate candidate = candidates.get(i);
            ZScoreData data = candidate.getData();

            String johansenStatus = "❌";
            if (data.getJohansenCointPValue() != null && data.getJohansenCointPValue() > 0) {
                johansenStatus = String.format("✅ (p=%.4f)", data.getJohansenCointPValue());
            }

            log.debug("   {}. {}/{} -> Скор: {}, Z: {}, Корр: {}, R²: {}, Johansen: {}, ADF: {}",
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

        // 4. Проверка минимального Z-Score (если включена в настройках)
        if (settings.isUseMinZFilter()) {
            double minZ = settings.getMinZ();
            if (currentZScore < minZ) {
                reason = String.format("Z-score ниже минимума: %.2f < %.2f", currentZScore, minZ);
                log.debug("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.debug("   ✅ {}: Z-score выше минимума: {} >= {}", pairName,
                    NumberFormatter.format(currentZScore, 2), NumberFormatter.format(minZ, 2));
        }

        // 5. Проверка максимального P-Value корреляции (если включена в настройках)
        if (settings.isUseMaxPValueFilter()) {
            Double correlationPValue = getCorrelationPValue(data, params);
            double maxPValue = settings.getMaxPValue();
            if (correlationPValue != null && correlationPValue > maxPValue) {
                reason = String.format("P-Value корреляции выше максимума: %.6f > %.6f", correlationPValue, maxPValue);
                log.debug("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.debug("   ✅ {}: P-Value корреляции в норме: {} <= {}", pairName,
                    correlationPValue != null ? NumberFormatter.format(correlationPValue, 6) : "null",
                    NumberFormatter.format(maxPValue, 6));
        }

        // 6. Проверка максимального ADF P-Value (если включена в настройках)
        if (settings.isUseMaxAdfValueFilter()) {
            Double adfPValue = getAdfPValue(data, params);
            double maxAdfValue = settings.getMaxAdfValue();
            if (adfPValue != null && adfPValue > maxAdfValue) {
                reason = String.format("ADF P-Value выше максимума: %.6f > %.6f", adfPValue, maxAdfValue);
                log.debug("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.debug("   ✅ {}: ADF P-Value в норме: {} <= {}", pairName,
                    adfPValue != null ? NumberFormatter.format(adfPValue, 6) : "null",
                    NumberFormatter.format(maxAdfValue, 6));
        }

        // 7. Проверка минимального R-Squared (если включена в настройках)
        if (settings.isUseMinRSquaredFilter()) {
            Double rSquared = getRSquared(data);
            double minRSquared = settings.getMinRSquared();
            if (rSquared == null || rSquared < minRSquared) {
                reason = String.format("R-Squared ниже минимума: %s < %.3f",
                        rSquared != null ? String.format("%.3f", rSquared) : "null", minRSquared);
                log.debug("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.debug("   ✅ {}: R-Squared выше минимума: {} >= {}", pairName,
                    NumberFormatter.format(rSquared, 3), NumberFormatter.format(minRSquared, 3));
        }

        // 8. Проверка минимальной корреляции (если включена в настройках)
        if (settings.isUseMinCorrelationFilter()) {
            Double correlation = data.getPearsonCorr();
            double minCorrelation = settings.getMinCorrelation();
            if (correlation == null || Math.abs(correlation) < minCorrelation) {
                reason = String.format("Корреляция ниже минимума: %s < %.3f",
                        correlation != null ? String.format("%.3f", Math.abs(correlation)) : "null", minCorrelation);
                log.debug("   ❌ {}: {}", pairName, reason);
                return reason;
            }
            log.debug("   ✅ {}: Корреляция выше минимума: {} >= {}", pairName,
                    NumberFormatter.format(Math.abs(correlation), 3), NumberFormatter.format(minCorrelation, 3));
        }

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

        log.debug("🎯 Рассчет качественного скора для {} с НАСТРАИВАЕМЫМИ весами", pairName);

        // ====== 1. Z-SCORE СИЛА (настраиваемый вес) ======
        if (settings.isUseZScoreScoring()) {
            double zScore = getLatestZScore(data, params);
            double maxWeight = settings.getZScoreScoringWeight();
            double zScorePoints = Math.min(Math.abs(zScore) * (maxWeight / 5.0), maxWeight); // Нормализуем по весу
            totalScore += zScorePoints;
            log.debug("  🎯 Z-Score компонент: {} очков (Z-score={}, вес={})",
                    NumberFormatter.format(zScorePoints, 1), NumberFormatter.format(zScore, 2), maxWeight);
        }

        // ====== 2. ПИКСЕЛЬНЫЙ СПРЕД (настраиваемый вес, высокий приоритет!) ======
        if (settings.isUsePixelSpreadScoring()) {
            double pixelSpreadScore = calculatePixelSpreadScoreComponent(data, settings, candlesMap);
            totalScore += pixelSpreadScore;
            log.debug("  📏 Пиксельный спред: {} очков (вес={})",
                    NumberFormatter.format(pixelSpreadScore, 1), settings.getPixelSpreadScoringWeight());
        }

        // ====== 3. КОИНТЕГРАЦИЯ (настраиваемый вес) ======
        if (settings.isUseCointegrationScoring()) {
            double cointegrationScore = calculateCointegrationScoreComponent(data, params, settings);
            totalScore += cointegrationScore;
            log.debug("  🔬 Коинтеграция: {} очков (вес={})",
                    NumberFormatter.format(cointegrationScore, 1), settings.getCointegrationScoringWeight());
        }

        // ====== 4. КАЧЕСТВО МОДЕЛИ (настраиваемый вес) ======
        if (settings.isUseModelQualityScoring()) {
            double modelQualityScore = calculateModelQualityScoreComponent(data, params, settings);
            totalScore += modelQualityScore;
            log.debug("  📊 Качество модели: {} очков (вес={})",
                    NumberFormatter.format(modelQualityScore, 1), settings.getModelQualityScoringWeight());
        }

        // ====== 5. СТАТИСТИЧЕСКАЯ ЗНАЧИМОСТЬ (настраиваемый вес) ======
        if (settings.isUseStatisticsScoring()) {
            double statisticalScore = calculateStatisticalSignificanceScoreComponent(data, params, settings);
            totalScore += statisticalScore;
            log.debug("  📊 Статистика: {} очков (вес={})",
                    NumberFormatter.format(statisticalScore, 1), settings.getStatisticsScoringWeight());
        }

        // ====== 6. БОНУСЫ (настраиваемый вес) ======
        if (settings.isUseBonusScoring()) {
            double bonusScore = calculateBonusScoreComponent(data, settings);
            totalScore += bonusScore;
            log.debug("  🎁 Бонусы: {} очков (вес={})",
                    NumberFormatter.format(bonusScore, 1), settings.getBonusScoringWeight());
        }

        log.debug("🏆 Итоговый скор для {}: {} очков (НАСТРАИВАЕМЫЕ ВЕСА)", pairName, NumberFormatter.format(totalScore, 1));
        return totalScore;
    }

    /**
     * Расчет скора пиксельного спреда (НОВАЯ ВЕРСИЯ: с расчетом из candlesMap и волатильностью)
     */
    private double calculatePixelSpreadScoreComponent(ZScoreData data, Settings settings, Map<String, List<Candle>> candlesMap) {
        try {
            String longTicker = data.getUnderValuedTicker();  // undervalued = long
            String shortTicker = data.getOverValuedTicker(); // overvalued = short

            // Сначала пробуем найти существующую PairData (старая логика)
            var existingPairs = tradingPairService.findByTickers(longTicker, shortTicker);

            if (!existingPairs.isEmpty()) {
                var pairData = existingPairs.get(0);

                // Получаем статистику пиксельного спреда из существующей пары
                double avgSpread = pixelSpreadService.getAveragePixelSpread(pairData);

                if (avgSpread > 0) {
                    double maxWeight = settings.getPixelSpreadScoringWeight();
                    double baseScore = calculateScoreFromPixelSpread(avgSpread, maxWeight);

                    log.debug("    📏 Пиксельный спред (существующая пара): avg={}px → {} баллов",
                            String.format("%.1f", avgSpread), String.format("%.1f", baseScore));
                    return baseScore;
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
                    double baseScore = calculateScoreFromPixelSpread(currentSpread, maxWeight);

                    // Добавляем бонус за волатильность пиксельного спреда
                    double volatilityBonus = calculateVolatilityBonusFromCandles(longCandles, shortCandles, maxWeight);

                    // Добавляем бонус за достаточно большой текущий пиксельный спред
                    double currentSpreadBonus = calculateCurrentSpreadBonus(currentSpread, maxWeight);

                    double totalScore = baseScore + volatilityBonus + currentSpreadBonus;

                    log.debug("    📏 Пиксельный спред (вычисленный): {}px → {} баллов (базовый: {}, волатильность: {}, текущий спред: {})",
                            String.format("%.1f", currentSpread), String.format("%.1f", totalScore),
                            String.format("%.1f", baseScore), String.format("%.1f", volatilityBonus),
                            String.format("%.1f", currentSpreadBonus));
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

        log.debug("    📏 Пиксельный спред {}px → ratio={}% → {} баллов",
                String.format("%.1f", avgSpread), String.format("%.0f", scoreRatio * 100), String.format("%.1f", score));

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
            log.debug("  🔬 {}: Нет данных коинтеграции", pairName);
            return 0.0;
        }

        double maxWeight = settings.getCointegrationScoringWeight();
        double score = 0.0;

        if (hasJohansen && hasAdf) {
            // ОБА ТЕСТА ДОСТУПНЫ - равные веса по 50% от полного веса
            log.debug("  🔬 {}: Динамические веса - оба теста ({}+{})", pairName, maxWeight / 2, maxWeight / 2);

            // Johansen (50% от веса)
            double johansenPValue = data.getJohansenCointPValue();
            double johansenScore = Math.max(0, (0.05 - johansenPValue) / 0.05) * (maxWeight / 2.0);
            score += johansenScore;

            // ADF (50% от веса)
            Double adfPValue = getAdfPValue(data, params);
            double adfScore = Math.max(0, (0.05 - Math.min(adfPValue, 0.05)) / 0.05) * (maxWeight / 2.0);
            score += adfScore;

            log.debug("    Johansen: {} очков (p-value={})",
                    NumberFormatter.format(johansenScore, 1),
                    NumberFormatter.format(johansenPValue, 6));
            log.debug("    ADF: {} очков (p-value={})",
                    NumberFormatter.format(adfScore, 1),
                    NumberFormatter.format(adfPValue, 6));

        } else if (hasJohansen) {
            // ТОЛЬКО JOHANSEN - полный вес
            log.debug("  🔬 {}: Динамические веса - только Johansen ({})", pairName, maxWeight);

            double johansenPValue = data.getJohansenCointPValue();
            double johansenScore = Math.max(0, (0.05 - johansenPValue) / 0.05) * maxWeight;
            score += johansenScore;

            log.debug("     Johansen: {} очков (p-value={})",
                    NumberFormatter.format(johansenScore, 1),
                    NumberFormatter.format(johansenPValue, 6));

        } else if (hasAdf) {
            // ТОЛЬКО ADF - полный вес
            log.debug("  🔬 {}: Динамические веса - только ADF ({})", pairName, maxWeight);

            Double adfPValue = getAdfPValue(data, params);
            double adfScore = Math.max(0, (0.05 - Math.min(adfPValue, 0.05)) / 0.05) * maxWeight;
            score += adfScore;

            log.debug("    ADF: {} очков (p-value={})",
                    NumberFormatter.format(adfScore, 1),
                    NumberFormatter.format(adfPValue, 6));
        }

        // Небольшой бонус за trace statistic (только если есть Johansen) - 5% от веса
        if (hasJohansen && data.getJohansenTraceStatistic() != null && data.getJohansenCriticalValue95() != null) {
            if (data.getJohansenTraceStatistic() > data.getJohansenCriticalValue95()) {
                double traceBonus = maxWeight * 0.05; // 5% от основного веса
                score += traceBonus;
                log.debug("    Бонус trace statistic: +{} очков", NumberFormatter.format(traceBonus, 1));
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
     * Вычисляет бонус за волатильность НАСТОЯЩЕГО пиксельного спреда из данных свечей
     * Использует PixelSpreadService для правильного расчета пиксельного спреда на основе чарта
     * Анализирует циклы пиксельного спреда между низким (10%) и высоким (90%) диапазонами
     */
    private double calculateVolatilityBonusFromCandles(List<Candle> longCandles, List<Candle> shortCandles, double maxWeight) {
        try {
            if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
                return 0.0;
            }

            log.debug("    🎯 Начинаем анализ волатильности пиксельного спреда: LONG {} свечей, SHORT {} свечей",
                    longCandles.size(), shortCandles.size());

            // Используем PixelSpreadService для правильного расчета пиксельного спреда по всем свечам
            List<Double> pixelSpreads = calculatePixelSpreadHistoryFromCandles(longCandles, shortCandles);

            if (pixelSpreads.size() < 10) {
                log.debug("    🎯 Недостаточно данных для анализа волатильности ({} точек)", pixelSpreads.size());
                return 0.0;
            }

            // Определяем диапазоны для анализа циклов
            double minSpread = pixelSpreads.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxSpread = pixelSpreads.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

            if (maxSpread - minSpread < 50) {
                log.debug("    🎯 Низкая волатильность пиксельного спреда: диапазон {}px", String.format("%.1f", maxSpread - minSpread));
                return 0.0; // Слишком низкая волатильность
            }

            log.debug("    🎯 Диапазон пиксельного спреда: {}-{}px на {} точках",
                    String.format("%.1f", minSpread), String.format("%.1f", maxSpread), pixelSpreads.size());

            // Анализируем циклы между низким (10% от диапазона) и высоким (90% от диапазона)
            double lowThreshold = minSpread + (maxSpread - minSpread) * 0.1;   // 10% от диапазона
            double highThreshold = minSpread + (maxSpread - minSpread) * 0.9;  // 90% от диапазона

            int cycles = 0;
            boolean wasInLowZone = false;
            boolean wasInHighZone = false;

            for (double spread : pixelSpreads) {
                if (spread <= lowThreshold) {
                    if (!wasInLowZone && wasInHighZone) {
                        // Переходим из высокой зоны в низкую - начинается новый цикл
                        wasInHighZone = false;
                    }
                    wasInLowZone = true;
                } else if (spread >= highThreshold && wasInLowZone) {
                    // Цикл завершен: от низкого к высокому
                    cycles++;
                    wasInLowZone = false;
                    wasInHighZone = true;
                }
            }

            // Начисляем бонус за волатильность
            double volatilityBonus = 0.0;
            if (cycles >= 2) {
                // Бонус 15% от веса за хорошую волатильность (2+ цикла)
                volatilityBonus = maxWeight * 0.15;
                log.debug("    🎯 БОНУС волатильности пиксельного спреда: {} циклов → +{} баллов (15% от веса)",
                        cycles, String.format("%.1f", volatilityBonus));
            } else if (cycles == 1) {
                // Малый бонус 7% от веса за умеренную волатильность (1 цикл)
                volatilityBonus = maxWeight * 0.07;
                log.debug("    🎯 Малый бонус волатильности пиксельного спреда: {} цикл → +{} баллов (7% от веса)",
                        cycles, String.format("%.1f", volatilityBonus));
            } else {
                log.debug("    🎯 Нет циклов волатильности пиксельного спреда: пороги {}-{}px, циклы: {}",
                        String.format("%.1f", lowThreshold), String.format("%.1f", highThreshold), cycles);
            }

            return volatilityBonus;

        } catch (Exception e) {
            log.warn("    🎯 Ошибка анализа волатильности пиксельного спреда: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Вычисляет историю пиксельного спреда из свечей, используя логику PixelSpreadService
     * Но без создания PairData (упрощенная версия для анализа волатильности)
     */
    private List<Double> calculatePixelSpreadHistoryFromCandles(List<Candle> longCandles, List<Candle> shortCandles) {
        List<Double> pixelSpreads = new ArrayList<>();

        // Сортировка по времени
        List<Candle> sortedLongCandles = new ArrayList<>(longCandles);
        List<Candle> sortedShortCandles = new ArrayList<>(shortCandles);
        sortedLongCandles.sort(Comparator.comparing(Candle::getTimestamp));
        sortedShortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Извлекаем цены и времена
        List<Date> longTimes = sortedLongCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = sortedLongCandles.stream().map(Candle::getClose).toList();

        List<Date> shortTimes = sortedShortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = sortedShortCandles.stream().map(Candle::getClose).toList();

        // Найти диапазон цен для нормализации (как в PixelSpreadService)
        double minLongPrice = longPrices.stream().min(Double::compareTo).orElse(0.0);
        double maxLongPrice = longPrices.stream().max(Double::compareTo).orElse(1.0);
        double longPriceRange = maxLongPrice - minLongPrice;

        double minShortPrice = shortPrices.stream().min(Double::compareTo).orElse(0.0);
        double maxShortPrice = shortPrices.stream().max(Double::compareTo).orElse(1.0);
        double shortPriceRange = maxShortPrice - minShortPrice;

        if (longPriceRange == 0.0 || shortPriceRange == 0.0) {
            return pixelSpreads; // Возвращаем пустой список
        }

        // Используем стандартный диапазон Z-Score для нормализации (как в PixelSpreadService)
        double minZScore = -3.0;
        double maxZScore = 3.0;
        double zRange = maxZScore - minZScore;

        // Нормализация long цен в диапазон Z-Score
        List<Double> scaledLongPrices = longPrices.stream()
                .map(price -> minZScore + ((price - minLongPrice) / longPriceRange) * zRange)
                .toList();

        // Нормализация short цен в диапазон Z-Score  
        List<Double> scaledShortPrices = shortPrices.stream()
                .map(price -> minZScore + ((price - minShortPrice) / shortPriceRange) * zRange)
                .toList();

        // Создаем синхронизированные временные точки (как в PixelSpreadService)
        Set<Long> allTimestamps = new HashSet<>();
        longTimes.forEach(date -> allTimestamps.add(date.getTime()));
        shortTimes.forEach(date -> allTimestamps.add(date.getTime()));

        List<Long> sortedTimestamps = allTimestamps.stream().sorted().toList();

        // Константы как в PixelSpreadService
        int chartHeight = 720;

        // Находим диапазон масштабированных значений
        double minValue = Math.min(
                scaledLongPrices.stream().min(Double::compareTo).orElse(-3.0),
                scaledShortPrices.stream().min(Double::compareTo).orElse(-3.0)
        );
        double maxValue = Math.max(
                scaledLongPrices.stream().max(Double::compareTo).orElse(3.0),
                scaledShortPrices.stream().max(Double::compareTo).orElse(3.0)
        );

        // Вычисляем пиксельное расстояние для всех временных точек (как в PixelSpreadService)
        for (Long timestamp : sortedTimestamps) {
            Double longPrice = findNearestPriceForVolatility(longTimes, scaledLongPrices, timestamp);
            Double shortPrice = findNearestPriceForVolatility(shortTimes, scaledShortPrices, timestamp);

            if (longPrice != null && shortPrice != null) {
                double longPixelY = convertValueToPixelForVolatility(longPrice, minValue, maxValue, chartHeight);
                double shortPixelY = convertValueToPixelForVolatility(shortPrice, minValue, maxValue, chartHeight);
                double pixelDistance = Math.abs(longPixelY - shortPixelY);
                pixelSpreads.add(pixelDistance);
            }
        }

        return pixelSpreads;
    }

    /**
     * Находит ближайшую цену для заданного времени (копия метода из PixelSpreadService)
     */
    private Double findNearestPriceForVolatility(List<Date> timeAxis, List<Double> prices, long targetTimestamp) {
        if (timeAxis.isEmpty() || prices.isEmpty()) return null;

        int bestIndex = 0;
        long bestDiff = Math.abs(timeAxis.get(0).getTime() - targetTimestamp);

        for (int i = 1; i < timeAxis.size(); i++) {
            long diff = Math.abs(timeAxis.get(i).getTime() - targetTimestamp);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIndex = i;
            }
        }

        return prices.get(bestIndex);
    }

    /**
     * Конвертирует значение в пиксельную координату Y (копия метода из PixelSpreadService)
     */
    private double convertValueToPixelForVolatility(double value, double minValue, double maxValue, int chartHeight) {
        if (maxValue - minValue == 0) return chartHeight / 2.0;

        // Нормализуем значение в диапазон [0, 1]
        double normalized = (value - minValue) / (maxValue - minValue);

        // Конвертируем в пиксели (Y=0 вверху, Y=chartHeight внизу)
        return chartHeight - (normalized * chartHeight);
    }

    /**
     * Вычисляет бонус за достаточно большой текущий пиксельный спред
     * Предотвращает выбор пар с низким или нулевым спредом
     */
    private double calculateCurrentSpreadBonus(double currentSpread, double maxWeight) {
        double bonusRatio = 0.0;

        if (currentSpread <= 0) {
            // Нулевой спред - никаких бонусов
            bonusRatio = 0.0;
            log.debug("    📐 Нулевой текущий спред: нет бонуса");
        } else if (currentSpread < 30) {
            // Очень низкий спред: 0-30px - штраф (-10% от веса)
            bonusRatio = -0.1;
            log.debug("    📐 ШТРАФ за очень низкий текущий спред: {}px → {}% штрафа",
                    String.format("%.1f", currentSpread), String.format("%.0f", bonusRatio * 100));
        } else if (currentSpread < 60) {
            // Низкий спред: 30-60px - без бонусов и штрафов
            bonusRatio = 0.0;
            log.debug("    📐 Низкий текущий спред: {}px → нет бонуса",
                    String.format("%.1f", currentSpread));
        } else if (currentSpread < 120) {
            // Умеренный спред: 60-120px - малый бонус 5%
            bonusRatio = 0.15;
            log.debug("    📐 Умеренный текущий спред: {}px → +{}% бонуса",
                    String.format("%.1f", currentSpread), String.format("%.0f", bonusRatio * 100));
        } else if (currentSpread < 240) {
            // Хороший спред: 120-240px - хороший бонус 10%
            bonusRatio = 0.30;
            log.debug("    📐 БОНУС за хороший текущий спред: {}px → +{}% бонуса",
                    String.format("%.1f", currentSpread), String.format("%.0f", bonusRatio * 100));
        } else if (currentSpread <= 480) {
            // Отличный спред: 240-480px - максимальный бонус 15%
            bonusRatio = 0.45;
            log.debug("    📐 БОЛЬШОЙ БОНУС за отличный текущий спред: {}px → +{}% бонуса",
                    String.format("%.1f", currentSpread), String.format("%.0f", bonusRatio * 100));
        } else {
            // Слишком большой спред: >480px - уменьшающийся бонус
            double excessRatio = Math.min((currentSpread - 480) / 240.0, 1.0); // До 720px
            bonusRatio = 0.15 - (excessRatio * 0.1); // От 15% до 5%
            log.debug("    📐 Большой текущий спред: {}px → +{}% убывающего бонуса",
                    String.format("%.1f", currentSpread), String.format("%.0f", bonusRatio * 100));
        }

        double bonus = maxWeight * bonusRatio;

        if (bonus != 0.0) {
            log.debug("    📐 Бонус за текущий спред: {} баллов", String.format("%.1f", bonus));
        }

        return bonus;
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
