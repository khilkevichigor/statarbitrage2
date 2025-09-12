package com.example.core.client;

import com.example.shared.dto.Candle;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.models.Settings;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "candles-service", url = "${candles.service.url:http://localhost:8091}")
public interface CandlesFeignClient {

    @PostMapping("/api/candles/applicable-map")
    Map<String, List<Candle>> getApplicableCandlesMap(@RequestBody CandlesRequest request);

    /**
     * Новый метод для получения всех доступных свечей для анализа стабильности
     */
    @PostMapping("/api/candles/all")
    Map<String, List<Candle>> getAllCandles(@RequestBody Settings settings);

    /**
     * Метод для получения большого количества свечей с пагинацией через candles микросервис
     * Микросервис сам будет делать несколько запросов к OKX API для получения нужного количества свечей
     */
    @PostMapping("/api/candles/all-extended")
    Map<String, List<Candle>> getAllCandlesExtended(@RequestBody ExtendedCandlesRequest request);
}