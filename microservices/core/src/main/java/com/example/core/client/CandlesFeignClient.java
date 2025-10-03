package com.example.core.client;

import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "candles-service", url = "${candles.service.url:http://localhost:8091}")
public interface CandlesFeignClient {

    /**
     * Метод для получения большого количества свечей с пагинацией через candles микросервис
     * Микросервис сам будет делать несколько запросов к OKX API для получения нужного количества свечей
     */
    @Deprecated
    @PostMapping("/api/candles/all-extended")
    Map<String, List<Candle>> getAllCandlesExtended(@RequestBody ExtendedCandlesRequest request);

    /**
     * Получить валидированные свечи из кэша для множества тикеров (новый улучшенный метод)
     * Возвращает данные в том же формате что и getAllCandlesExtended для совместимости
     * Догружает и сохраняет в КЭШ
     */
    @PostMapping("/api/candles-processor/validated-cache-extended")
    Map<String, List<Candle>> getValidatedCacheExtended(@RequestBody ExtendedCandlesRequest request);

    // ============= МЕТОДЫ ДЛЯ УПРАВЛЕНИЯ КЭШЕМ СВЕЧЕЙ =============

    /**
     * Получить статистику кэша свечей
     */
    @GetMapping("/api/cache/statistics")
    Map<String, Object> getCacheStatistics(@RequestParam(required = false) String exchange);

    /**
     * Принудительная загрузка свечей
     */
    @PostMapping("/api/cache/force-load")
    Map<String, String> forceLoadCandles(@RequestBody Map<String, Object> request);

    /**
     * Запуск полной предзагрузки
     */
    @PostMapping("/api/cache/full-preload")
    Map<String, String> startFullPreload(@RequestBody Map<String, Object> request);

    /**
     * Запуск ежедневного обновления
     */
    @PostMapping("/api/cache/daily-update")
    Map<String, String> startDailyUpdate(@RequestBody Map<String, Object> request);

    /**
     * Обновление количества потоков
     */
    @PostMapping("/api/cache/thread-count")
    Map<String, String> updateThreadCount(@RequestBody Map<String, Object> request);

    /**
     * Обновление периода принудительной загрузки
     */
    @PostMapping("/api/cache/force-load-period")
    Map<String, String> updateForceLoadPeriod(@RequestBody Map<String, Object> request);

    /**
     * Обновление настроек расписания
     */
    @PostMapping("/api/cache/schedule-update")
    Map<String, String> updateSchedules(@RequestBody Map<String, Object> request);

    /**
     * Health check кэша
     */
    @GetMapping("/api/cache/health")
    Map<String, Object> getCacheHealth();
}