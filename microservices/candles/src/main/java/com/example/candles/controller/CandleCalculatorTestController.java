//package com.example.candles.controller;
//
//import com.example.candles.utils.CandleCalculatorUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
///**
// * Контроллер для тестирования утилитного класса CandleCalculatorUtil
// */
//@RestController
//@RequestMapping("/api/candle-calculator")
//@Slf4j
//public class CandleCalculatorTestController {
//
//    /**
//     * Тестирование расчета количества свечей для одной комбинации
//     * <p>
//     * GET /api/candle-calculator/test?timeframe=1H&period=месяц
//     */
//    @GetMapping("/test")
//    public ResponseEntity<?> testSingleCalculation(
//            @RequestParam String timeframe,
//            @RequestParam String period
//    ) {
//        log.info("🧮 ТЕСТ РАСЧЕТА: timeframe={}, period={}", timeframe, period);
//
//        try {
//            int candlesCount = CandleCalculatorUtil.calculateCandlesCount("", timeframe, period);
//            int allowedDifference = CandleCalculatorUtil.getAllowedDifference(timeframe, candlesCount);
//            String tolerance = CandleCalculatorUtil.getToleranceDescription(timeframe);
//            String details = CandleCalculatorUtil.getCalculationDetails(timeframe, period);
//
//            return ResponseEntity.ok(Map.of(
//                    "success", true,
//                    "timeframe", timeframe,
//                    "period", period,
//                    "candlesCount", candlesCount,
//                    "allowedDifference", allowedDifference,
//                    "tolerance", tolerance,
//                    "details", details
//            ));
//
//        } catch (Exception e) {
//            log.error("❌ ОШИБКА ТЕСТА: {}", e.getMessage(), e);
//            return ResponseEntity.ok(Map.of(
//                    "success", false,
//                    "error", e.getMessage()
//            ));
//        }
//    }
//
//    /**
//     * Тестирование всех поддерживаемых комбинаций
//     * <p>
//     * GET /api/candle-calculator/test-all
//     */
//    @GetMapping("/test-all")
//    public ResponseEntity<?> testAllCombinations() {
//        log.info("🧮 ТЕСТ ВСЕХ КОМБИНАЦИЙ");
//
//        String[] timeframes = {"1m", "5m", "15m", "1H", "4H", "1D", "1W", "1M"};
//        String[] periods = {"день", "неделя", "месяц", "6 месяцев", "1 год", "2 года", "3 года"};
//
//        List<Map<String, Object>> results = new ArrayList<>();
//        int successCount = 0;
//        int errorCount = 0;
//
//        for (String timeframe : timeframes) {
//            for (String period : periods) {
//                try {
//                    int candlesCount = CandleCalculatorUtil.calculateCandlesCount("", timeframe, period);
//                    int allowedDifference = CandleCalculatorUtil.getAllowedDifference(timeframe, candlesCount);
//                    String tolerance = CandleCalculatorUtil.getToleranceDescription(timeframe);
//
//                    results.add(Map.of(
//                            "timeframe", timeframe,
//                            "period", period,
//                            "candlesCount", candlesCount,
//                            "allowedDifference", allowedDifference,
//                            "tolerance", tolerance,
//                            "success", true
//                    ));
//                    successCount++;
//
//                } catch (Exception e) {
//                    results.add(Map.of(
//                            "timeframe", timeframe,
//                            "period", period,
//                            "error", e.getMessage(),
//                            "success", false
//                    ));
//                    errorCount++;
//                    log.error("❌ ОШИБКА для {}+{}: {}", timeframe, period, e.getMessage());
//                }
//            }
//        }
//
//        log.info("✅ ТЕСТ ЗАВЕРШЕН: успешно {}, ошибок {}", successCount, errorCount);
//
//        return ResponseEntity.ok(Map.of(
//                "success", true,
//                "totalTests", timeframes.length * periods.length,
//                "successCount", successCount,
//                "errorCount", errorCount,
//                "results", results
//        ));
//    }
//
//    /**
//     * Тестирование валидации количества свечей
//     * <p>
//     * POST /api/candle-calculator/test-validation
//     * Body: {"timeframe": "1H", "expectedCount": 720, "actualCount": 721}
//     */
//    @PostMapping("/test-validation")
//    public ResponseEntity<?> testValidation(@RequestBody Map<String, Object> request) {
//        String timeframe = (String) request.get("timeframe");
//        int expectedCount = (Integer) request.get("expectedCount");
//        int actualCount = (Integer) request.get("actualCount");
//
//        log.info("🔍 ТЕСТ ВАЛИДАЦИИ: timeframe={}, expected={}, actual={}", timeframe, expectedCount, actualCount);
//
//        try {
//            boolean isValid = CandleCalculatorUtil.isValidCandlesCount(timeframe, expectedCount, actualCount);
//            int allowedDifference = CandleCalculatorUtil.getAllowedDifference(timeframe, expectedCount);
//            int actualDifference = Math.abs(actualCount - expectedCount);
//            String tolerance = CandleCalculatorUtil.getToleranceDescription(timeframe);
//
//            return ResponseEntity.ok(Map.of(
//                    "success", true,
//                    "timeframe", timeframe,
//                    "expectedCount", expectedCount,
//                    "actualCount", actualCount,
//                    "actualDifference", actualDifference,
//                    "allowedDifference", allowedDifference,
//                    "tolerance", tolerance,
//                    "isValid", isValid,
//                    "validation", isValid ? "✅ Прошла" : "❌ Провалена"
//            ));
//
//        } catch (Exception e) {
//            log.error("❌ ОШИБКА ВАЛИДАЦИИ: {}", e.getMessage(), e);
//            return ResponseEntity.ok(Map.of(
//                    "success", false,
//                    "error", e.getMessage()
//            ));
//        }
//    }
//
//    /**
//     * Показать примеры использования
//     * <p>
//     * GET /api/candle-calculator/examples
//     */
//    @GetMapping("/examples")
//    public ResponseEntity<?> getExamples() {
//        List<Map<String, Object>> examples = List.of(
//                Map.of(
//                        "description", "1 час за месяц",
//                        "timeframe", "1H",
//                        "period", "месяц",
//                        "expectedCandles", "720 (30 дней * 24 часа)",
//                        "tolerance", "±24 (1 день)"
//                ),
//                Map.of(
//                        "description", "1 день за год",
//                        "timeframe", "1D",
//                        "period", "1 год",
//                        "expectedCandles", "365 дней",
//                        "tolerance", "точное соответствие"
//                ),
//                Map.of(
//                        "description", "5 минут за неделю",
//                        "timeframe", "5m",
//                        "period", "неделя",
//                        "expectedCandles", "2016 (7 * 24 * 12)",
//                        "tolerance", "±288 (1 день)"
//                ),
//                Map.of(
//                        "description", "1 неделя за 2 года",
//                        "timeframe", "1W",
//                        "period", "2 года",
//                        "expectedCandles", "104 (2 * 52)",
//                        "tolerance", "точное соответствие"
//                )
//        );
//
//        return ResponseEntity.ok(Map.of(
//                "success", true,
//                "examples", examples,
//                "supportedTimeframes", List.of("1m", "5m", "15m", "1H", "4H", "1D", "1W", "1M"),
//                "supportedPeriods", List.of("день", "неделя", "месяц", "6 месяцев", "1 год", "2 года", "3 года"),
//                "toleranceRules", Map.of(
//                        "minutesAndHours", "Минутные (1m, 5m, 15m) и часовые (1H, 4H) таймфреймы: погрешность ±1 день",
//                        "daysAndAbove", "Дневные и выше (1D, 1W, 1M): точное соответствие 1:1"
//                )
//        ));
//    }
//}