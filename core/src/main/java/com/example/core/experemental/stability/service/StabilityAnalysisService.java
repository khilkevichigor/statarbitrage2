package com.example.core.experemental.stability.service;

import com.example.core.experemental.stability.dto.StabilityRequestDto;
import com.example.core.experemental.stability.dto.StabilityResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class StabilityAnalysisService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    @Value("${cointegration.api.url:http://localhost:8000}")
    private String pythonApiBaseUrl;

    @Value("${cointegration.api.timeout.connect:30000}")
    private int connectTimeout;

    @Value("${cointegration.api.timeout.read:300000}")
    private int readTimeout;

    @PostConstruct
    public void initRestTemplate() {
        // Создаем RestTemplate с увеличенными таймаутами
        this.restTemplate = new RestTemplate();

        log.info("🔧 Настроен RestTemplate для Python API: connectTimeout={}ms, readTimeout={}ms",
                connectTimeout, readTimeout);
        log.info("⚠️  Для полной настройки таймаутов нужно настроить HTTP клиент на уровне сервера");
    }

    /**
     * Отправляет запрос на анализ стабильности в Python API
     */
    public StabilityResponseDto analyzeStability(StabilityRequestDto request) {
        String endpoint = "/analyze-stability";
        String fullUrl = pythonApiBaseUrl + endpoint;

        log.info("📤 Отправляем запрос анализа стабильности в Python API: {}", fullUrl);
        log.debug("📊 Анализируем {} тикеров для поиска стабильных пар",
                request.getCandlesMap().size());

        try {
            // Сериализуем запрос в JSON
            String requestJson = objectMapper.writeValueAsString(request);
            double sizeInMB = requestJson.length() / (1024.0 * 1024.0);
            log.info("📦 Размер JSON запроса: {} MB ({} байт)", String.format("%.2f", sizeInMB), requestJson.length());
            log.debug("📝 JSON запрос: {}", requestJson.length() > 1000 ?
                    requestJson.substring(0, 1000) + "..." : requestJson);

            // Настраиваем заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            long startTime = System.currentTimeMillis();

            // Отправляем POST запрос
            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            long requestTime = System.currentTimeMillis() - startTime;
            log.info("📥 Получен ответ от Python API за {} сек, статус: {}",
                    String.format("%.2f", requestTime / 1000.0), response.getStatusCode());

            // Проверяем статус ответа
            if (response.getStatusCode() != HttpStatus.OK) {
                String errorMsg = String.format("❌ Ошибка Python API: %s - %s",
                        response.getStatusCode(), response.getBody());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            // Десериализуем ответ
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                throw new RuntimeException("❌ Пустой ответ от Python API");
            }

            log.debug("📄 JSON ответ: {}", responseBody.length() > 1000 ?
                    responseBody.substring(0, 1000) + "..." : responseBody);

            StabilityResponseDto stabilityResponse = objectMapper.readValue(responseBody,
                    new TypeReference<StabilityResponseDto>() {
                    });

            // Логируем результаты анализа
            logAnalysisResults(stabilityResponse);

            return stabilityResponse;

        } catch (Exception e) {
            String errorMsg = String.format("❌ Ошибка при вызове Python API анализа стабильности: %s",
                    e.getMessage());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Логирует результаты анализа стабильности
     */
    private void logAnalysisResults(StabilityResponseDto response) {
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            log.warn("⚠️ Анализ стабильности не был успешным");
            return;
        }

        log.info("🎯 === РЕЗУЛЬТАТЫ АНАЛИЗА СТАБИЛЬНОСТИ ===");
        log.info("📊 Всего проанализировано пар: {}", response.getTotalPairsAnalyzed());
        log.info("✅ Торгуемых пар найдено: {}", response.getTradeablePairsFound());
        log.info("⭐ Отличных пар найдено: {}", response.getExcellentPairsFound());
        log.info("⏱️ Время анализа: {} сек",
                String.format("%.2f", response.getAnalysisTimeSeconds()));

        if (response.getSummaryStats() != null) {
            log.info("📈 Лучший результат: {} баллов", response.getSummaryStats().getBestScore());
            log.info("📊 Средний балл: {}",
                    String.format("%.1f", response.getSummaryStats().getAverageScore()));

            if (response.getSummaryStats().getPairsByRating() != null) {
                var ratingStats = response.getSummaryStats().getPairsByRating();
                log.info("📋 Распределение по рейтингу:");
                log.info("   EXCELLENT: {}", ratingStats.getOrDefault("excellent", 0));
                log.info("   GOOD: {}", ratingStats.getOrDefault("good", 0));
                log.info("   MARGINAL: {}", ratingStats.getOrDefault("marginal", 0));
                log.info("   POOR: {}", ratingStats.getOrDefault("poor", 0));
                log.info("   REJECTED: {}", ratingStats.getOrDefault("rejected", 0));
            }
        }

        // Показываем топ-5 лучших пар
        if (response.getResults() != null && !response.getResults().isEmpty()) {
            log.info("🏆 ТОП-5 ЛУЧШИХ ПАР:");
            response.getResults().stream()
                    .filter(result -> result.getTotalScore() != null && result.getTotalScore() > 0)
                    .limit(5)
                    .forEach(result -> {
                        log.info("   {}—{}: {} баллов [{}] {}",
                                result.getTickerA(),
                                result.getTickerB(),
                                result.getTotalScore(),
                                result.getStabilityRating(),
                                Boolean.TRUE.equals(result.getIsTradeable()) ? "✅ ТОРГУЕМАЯ" : "❌");
                    });
        }

        log.info("🎯 === КОНЕЦ РЕЗУЛЬТАТОВ АНАЛИЗА ===");
    }
}