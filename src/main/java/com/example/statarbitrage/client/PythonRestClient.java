package com.example.statarbitrage.client;

import com.example.statarbitrage.dto.TradingPair;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Обновленный REST клиент для работы с Python API
 * Использует упрощенную структуру TradingPair
 */
@Slf4j
@Component("modernPythonRestClient")
public class PythonRestClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    
    @Value("${python.service.url:http://localhost:8282}")
    private String pythonServiceUrl;

    public PythonRestClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Обновленный метод для поиска торговых пар
     * Использует новый эндпоинт /discover-pairs
     */
    public List<TradingPair> discoverTradingPairs(Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🔍 Поиск торговых пар через Python API: {} тикеров", candlesMap.size());
        
        DiscoveryRequest requestBody = new DiscoveryRequest(candlesMap, settings);
        
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonServiceUrl + "/discover-pairs"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120)) // Увеличенный таймаут для анализа
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("❌ Ошибка от Python API: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Python service error: " + response.statusCode());
            }

            // Парсинг ответа от Python API
            Map<String, Object> responseMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
            
            boolean success = (Boolean) responseMap.get("success");
            if (!success) {
                log.error("❌ Python API вернул success=false");
                throw new RuntimeException("Python service returned failure");
            }
            
            int pairsFound = (Integer) responseMap.get("pairs_found");
            log.info("✅ Найдено {} торговых пар", pairsFound);
            
            // Извлекаем results и преобразуем в TradingPair
            List<Object> results = (List<Object>) responseMap.get("results");
            String resultsJson = objectMapper.writeValueAsString(results);
            
            List<TradingPair> tradingPairs = objectMapper.readValue(resultsJson, new TypeReference<>() {});
            
            // Устанавливаем timestamp для каждой пары
            long currentTime = System.currentTimeMillis();
            tradingPairs.forEach(pair -> pair.setTimestamp(currentTime));
            
            log.info("🎯 Десериализовано {} объектов TradingPair", tradingPairs.size());
            return tradingPairs;
            
        } catch (JsonProcessingException e) {
            log.error("❌ Ошибка сериализации JSON: {}", e.getMessage());
            throw new RuntimeException("JSON processing error", e);
        } catch (IOException | InterruptedException e) {
            log.error("❌ Ошибка HTTP запроса: {}", e.getMessage());
            throw new RuntimeException("HTTP request error", e);
        }
    }

    /**
     * Детальный анализ конкретной пары
     */
    public TradingPair analyzePairDetailed(String ticker1, String ticker2, 
                                          Map<String, List<Candle>> candlesMap, 
                                          Settings settings) {
        log.info("🔬 Детальный анализ пары: {} / {}", ticker1, ticker2);
        
        // Фильтруем данные только для нужных тикеров
        Map<String, List<Candle>> pairData = Map.of(
            ticker1, candlesMap.get(ticker1),
            ticker2, candlesMap.get(ticker2)
        );
        
        PairAnalysisRequest requestBody = new PairAnalysisRequest(pairData, settings, true);
        
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonServiceUrl + "/analyze-pair"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("❌ Ошибка анализа пары: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Pair analysis error: " + response.statusCode());
            }

            Map<String, Object> responseMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object result = responseMap.get("result");
            
            String resultJson = objectMapper.writeValueAsString(result);
            TradingPair tradingPair = objectMapper.readValue(resultJson, TradingPair.class);
            
            tradingPair.setTimestamp(System.currentTimeMillis());
            
            log.info("✅ Детальный анализ завершен: {}", tradingPair.getDisplayName());
            return tradingPair;
            
        } catch (JsonProcessingException e) {
            log.error("❌ Ошибка обработки ответа анализа: {}", e.getMessage());
            throw new RuntimeException("Analysis response processing error", e);
        } catch (IOException | InterruptedException e) {
            log.error("❌ Ошибка HTTP запроса анализа: {}", e.getMessage());
            throw new RuntimeException("Analysis HTTP request error", e);
        }
    }

    // === ВНУТРЕННИЕ КЛАССЫ ДЛЯ ЗАПРОСОВ ===
    
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class DiscoveryRequest {
        private Map<String, List<Candle>> candles_map;
        private Settings settings;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PairAnalysisRequest {
        private Map<String, List<Candle>> pair;
        private Settings settings;
        private boolean include_full_zscore_history;
    }
}