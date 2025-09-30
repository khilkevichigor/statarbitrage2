package com.example.core.experemental;

import com.example.core.client.CandlesFeignClient;
import com.example.core.client.OkxFeignClient;
import com.example.core.experemental.stability.dto.StabilityRequestDto;
import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.example.core.experemental.stability.service.StabilityAnalysisService;
import com.example.core.services.SettingsService;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stability")
@RequiredArgsConstructor
@Slf4j
public class StabilityAnalysisController {

    private final StabilityAnalysisService stabilityAnalysisService;
    private final CandlesFeignClient candlesFeignClient;
    private final OkxFeignClient okxFeignClient;
    private final SettingsService settingsService;

    //todo для лучших пар делать глубокий анализ - для начала считать максимальную раздвижку нормализованных цен, пересечения для 1Д, и тд что бы была полная аналитика че ждать от пары

    /**
     * Эндпоинт для анализа стабильности всех возможных пар
     * Получает все тикеры из системы и отправляет их на анализ стабильности в Python
     */
    @GetMapping("/analyze-all-pairs")
    public ResponseEntity<StabilityResponseDto> analyzeAllPairsStability() {
        log.info("🔍 Начало анализа стабильности всех пар...");

        long startTime = System.currentTimeMillis();

        try {
            // Получаем настройки из базы
            Settings settings = settingsService.getSettings();
            log.info("📋 Получены настройки: minWindowSize={}, minCorr={}",
                    settings.getMinWindowSize(), settings.getMinCorrelation());

            // Получаем все свечи для всех доступных тикеров (без исключений)
            Map<String, List<Candle>> candlesMap = getAllCandles(settings);

            if (candlesMap.isEmpty()) {
                log.warn("⚠️ Не удалось получить данные свечей");
                return ResponseEntity.badRequest().build();
            }

            log.info("📊 Получены данные для {} тикеров: {}",
                    candlesMap.size(), String.join(", ", candlesMap.keySet()));

            // Конвертируем настройки в Map для Python API
            Map<String, Object> settingsMap = convertSettingsToMap(settings);

            // Создаем запрос для Python API
            StabilityRequestDto request = new StabilityRequestDto(candlesMap, settingsMap);

            // Отправляем запрос на анализ стабильности
            StabilityResponseDto response = stabilityAnalysisService.analyzeStability(request);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("✅ Анализ стабильности завершен за {} секунд. Найдено торгуемых пар: {}/{}",
                    totalTime / 1000.0,
                    response.getTradeablePairsFound(),
                    response.getTotalPairsAnalyzed());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("❌ Ошибка при анализе стабильности (время: {}с): {}",
                    totalTime / 1000.0, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получает все доступные свечи из системы через новый эндпоинт
     * Использует /api/candles/all-available для получения всех тикеров и их свечей
     */
    private Map<String, List<Candle>> getAllCandles(Settings settings) {
        try {
            log.info("📈 Получение всех доступных свечей через расширенный эндпоинт /all-extended...");

            ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                    .timeframe(settings.getTimeframe())
                    .candleLimit((int) settings.getCandleLimit())
                    .minVolume(settings.getMinVolume())
                    .useMinVolumeFilter(settings.isUseMinVolumeFilter())
                    .minimumLotBlacklist(settings.getMinimumLotBlacklist())
                    .tickers(null) // Получаем все доступные тикеры
                    .excludeTickers(null) // Никого не исключаем
                    .build();

            long startTime = System.currentTimeMillis();
            Map<String, List<Candle>> candlesMap = candlesFeignClient.getValidatedCandlesExtended(request);
            long elapsed = System.currentTimeMillis() - startTime;

            if (candlesMap == null || candlesMap.isEmpty()) {
                log.warn("⚠️ Получен пустой результат от сервиса свечей");
                return new HashMap<>();
            }

            log.info("✅ Получено {} тикеров с свечами за {} сек через новый эндпоинт",
                    candlesMap.size(), String.format("%.2f", elapsed / 1000.0));

            // Показываем статистику по свечам
            if (!candlesMap.isEmpty()) {
                int totalCandles = candlesMap.values().stream()
                        .mapToInt(List::size)
                        .sum();

                List<String> tickers = List.copyOf(candlesMap.keySet());
                log.info("📊 Статистика: {} тикеров, всего {} свечей",
                        candlesMap.size(), totalCandles);

                log.info("🎯 Тикеры: {}",
                        tickers.size() > 15 ?
                                String.join(", ", tickers.subList(0, 15)) + "... и еще " + (tickers.size() - 15) :
                                String.join(", ", tickers));
            }

            return candlesMap;

        } catch (Exception e) {
            log.error("❌ Ошибка при получении свечей через новый эндпоинт: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Конвертирует объект Settings в Map для Python API анализа стабильности
     * Python StabilityAnalyzer использует только minWindowSize из настроек
     */
    private Map<String, Object> convertSettingsToMap(Settings settings) {
        Map<String, Object> map = new HashMap<>();

        // Основной параметр который использует Python StabilityAnalyzer
        map.put("minWindowSize", 100);

        // Добавляем дополнительные параметры, которые могут понадобиться
        map.put("minCorrelation", 0.1);
        map.put("maxPValue", 1);
        map.put("maxAdfValue", 1);
        map.put("minRSquared", 0.1);
        map.put("minZ", -10);
        map.put("candleLimit", 300);
        map.put("timeframe", "1D");

        log.info("🔧 Настройки для Python анализа стабильности: {}", map);
        return map;
    }
}