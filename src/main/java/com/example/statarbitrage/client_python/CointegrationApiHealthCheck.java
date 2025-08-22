package com.example.statarbitrage.client_python;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
public class CointegrationApiHealthCheck {

    @Value("${cointegration.api.url}")
    private String baseUrl;

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            boolean healthy = response.statusCode() == 200;
            if (healthy) {
                log.info("✅ API коинтеграции в норме");
            } else {
                log.warn("⚠️ Ошибка API коинтеграции: {}", response.statusCode());
            }
            return healthy;

        } catch (IOException | InterruptedException e) {
            log.error("❌ Ошибка проверки работоспособности API коинтеграции: {}", e.getMessage());
            return false;
        }
    }
}