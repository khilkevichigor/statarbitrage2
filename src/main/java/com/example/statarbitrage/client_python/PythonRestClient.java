package com.example.statarbitrage.client_python;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.dto.cointegration.*;
import com.example.statarbitrage.common.model.Settings;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PythonRestClient {

    private static final ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Value("${cointegration.api.url}")
    private String baseUrl;

    public List<ZScoreData> fetchZScoreData(Settings settings, Map<String, List<Candle>> candlesMap) {
        return discoverPairs(candlesMap, settings);
    }

    public List<ZScoreData> discoverPairs(Map<String, List<Candle>> candlesMap, Settings settings) {
        Map<String, Object> settingsMap = convertSettingsToMap(settings);
        Map<String, List<ApiCandle>> apiCandlesMap = convertCandlesMap(candlesMap);
        DiscoveryRequest requestBody = new DiscoveryRequest(apiCandlesMap, settingsMap);

        DiscoveryResponse response = sendRequestWithRestTemplate("/discover-pairs", requestBody, new TypeReference<>() {
        });
        return response.getResults();
    }

    public ZScoreData analyzePair(Map<String, List<Candle>> pair, Settings settings, boolean includeFullZscoreHistory) {
        Map<String, Object> settingsMap = convertSettingsToMap(settings);
        Map<String, List<ApiCandle>> apiPair = convertCandlesMap(pair);
        PairAnalysisRequest requestBody = new PairAnalysisRequest(apiPair, settingsMap, includeFullZscoreHistory);

        PairAnalysisResponse response = sendRequestWithRestTemplate("/analyze-pair", requestBody, new TypeReference<PairAnalysisResponse>() {
        });

        if (response.isSuccess()) {
            return convertPairAnalysisResultToZScoreData(response.getResult());
        } else {
            throw new RuntimeException("❌ Python API вернул success=false");
        }
    }

    public boolean validateCointegration(String ticker1, String ticker2, Map<String, List<Candle>> candlesMap, Settings settings) {
        Map<String, Object> settingsMap = convertSettingsToMap(settings);
        Map<String, List<ApiCandle>> apiCandlesMap = convertCandlesMap(candlesMap);
        ValidationRequest requestBody = new ValidationRequest(ticker1, ticker2, apiCandlesMap, settingsMap);

        Map<String, Object> result = sendRequest("/validate-cointegration", requestBody, new TypeReference<Map<String, Object>>() {
        });
        return Boolean.TRUE.equals(result.get("is_cointegrated"));
    }

    private Map<String, Object> convertSettingsToMap(Settings settings) {
        try {
            String json = objectMapper.writeValueAsString(settings);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("❌ Ошибка конвертации настроек в мапу", e);
        }
    }

    private Map<String, List<ApiCandle>> convertCandlesMap(Map<String, List<Candle>> candlesMap) {
        return candlesMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(candle -> new ApiCandle(candle.getTimestamp(), candle.getClose()))
                                .collect(Collectors.toList())
                ));
    }

    private <T> T sendRequestWithRestTemplate(String endpoint, Object requestBody, TypeReference<T> responseType) {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            String url = baseUrl + endpoint;
            log.info("📤 Отправляю запрос в {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            log.debug("📥 Ответ от {}: статус={}", baseUrl + endpoint, response.getStatusCode());

            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("❌ Ошибка от API коинтеграции: {} - {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("❌ Ошибка API коинтеграции: " + response.getStatusCode() + " - " + response.getBody());
            }

            return objectMapper.readValue(response.getBody(), responseType);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("❌ Ошибка обработки JSON", e);
        } catch (Exception e) {
            throw new RuntimeException("❌ Ошибка при выполнении запроса: " + e.getMessage(), e);
        }
    }

    private <T> T sendRequest(String endpoint, Object requestBody, TypeReference<T> responseType) {
        String json;
        try {
            json = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("❌ Ошибка сериализации запроса", e);
        }

        log.info("📤 Sending request to {}: {}", baseUrl + endpoint, json);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException("Ошибка отправки запроса", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Запрос прервался", e);
        }

        log.debug("📥 Response from {}: status={}, body={}", baseUrl + endpoint, response.statusCode(), response.body());

        if (response.statusCode() != 200) {
            log.error("❌ Ошибка от API коинтеграции: {} - {}", response.statusCode(), response.body());
            log.error("❌ URL запроса: {}", baseUrl + endpoint);
            log.error("❌ Тело запроса: {}", json);

            String errorDetails = response.body();
            if (response.statusCode() == 422) {
                throw new RuntimeException("❌ Ошибка валидации: " + errorDetails);
            }
            throw new RuntimeException("❌ Ошибка API коинтеграции: " + response.statusCode() + " - " + errorDetails);
        }

        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("❌ Ошибка парсинга ответа", e);
        }
    }

    // Здесь сетим zscoreParams
    private ZScoreData convertPairAnalysisResultToZScoreData(PairAnalysisResult result) {
        ZScoreData zScoreData = new ZScoreData();
        zScoreData.setOvervaluedTicker(result.getOvervaluedTicker());
        zScoreData.setUndervaluedTicker(result.getUndervaluedTicker());
        zScoreData.setCorrelation(result.getCorrelation());
        zScoreData.setCorrelation_pvalue(result.getCorrelation_pvalue());
        zScoreData.setCointegration_pvalue(result.getCointegration_pvalue());
        zScoreData.setLatest_zscore(result.getLatest_zscore());
        zScoreData.setTotal_observations(result.getTotal_observations());

        //todo здесь сетить все поля из PairAnalysisResult
        zScoreData.setIsCointegrated(result.getIs_cointegrated());
        zScoreData.setDataQuality(result.getData_quality());
        zScoreData.setCointegrationDetails(result.getCointegration_details());

        // Конвертируем zscore_history в zscoreParams если есть
        if (result.getZscore_history() != null && !result.getZscore_history().isEmpty()) {
            List<ZScoreParam> zscoreParams = result.getZscore_history().stream()
                    .map(item -> {
                        ZScoreParam param = new ZScoreParam();
                        param.setTimestamp(item.getTimestamp());
                        param.setZscore(item.getZscore());
                        param.setCorrelation(result.getCorrelation());
                        param.setPvalue(result.getCorrelation_pvalue());
                        param.setAdfpvalue(item.getAdf_pvalue() != null ? item.getAdf_pvalue() : result.getCointegration_pvalue());
                        return param;
                    })
                    .collect(Collectors.toList());
            zScoreData.setZscoreParams(zscoreParams);
        }

        return zScoreData;
    }
}
