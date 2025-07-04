package com.example.statarbitrage;

import com.example.statarbitrage.client_python.CointegrationApiHealthCheck;
import com.example.statarbitrage.client_python.PythonRestClient;
import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.model.Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@EnableAsync
@SpringBootApplication
@EnableScheduling
public class StatarbitrageApplication {

    private final CointegrationApiHealthCheck healthCheck;
    private final PythonRestClient pythonRestClient;

    public StatarbitrageApplication(CointegrationApiHealthCheck healthCheck, PythonRestClient pythonRestClient) {
        this.healthCheck = healthCheck;
        this.pythonRestClient = pythonRestClient;
    }

    public static void main(String[] args) {
        SpringApplication.run(StatarbitrageApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkCointegrationApiHealth() {
        log.info("🚀 Checking Cointegration API health...");
        boolean healthy = healthCheck.isHealthy();

        if (healthy) {
            log.info("🧪 Testing Cointegration API integration...");
            try {
                Map<String, List<Candle>> testData = new HashMap<>();
                testData.put("BTC-USDT", Arrays.asList(
                        Candle.builder().timestamp(1672531200000L).close(16500.0).build(),
                        Candle.builder().timestamp(1672534800000L).close(16520.0).build()
                ));
                testData.put("ETH-USDT", Arrays.asList(
                        Candle.builder().timestamp(1672531200000L).close(1200.0).build(),
                        Candle.builder().timestamp(1672534800000L).close(1205.0).build()
                ));

                Settings settings = Settings.builder()
                        .minCorrelation(0.85)
                        .minPvalue(0.05)
                        .build();

                pythonRestClient.discoverPairs(testData, settings);
                log.info("✅ Cointegration API integration test successful!");

            } catch (Exception e) {
                log.error("❌ Cointegration API integration test failed: {}", e.getMessage(), e);
            }
        }
    }

}
