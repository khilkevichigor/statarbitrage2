package com.example.statarbitrage;

import com.example.statarbitrage.client_python.CointegrationApiHealthCheck;
import com.example.statarbitrage.client_python.PythonRestClient;
import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.processors.UpdateTradeProcessor;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import com.example.statarbitrage.trading.services.GeolocationService;
import com.example.statarbitrage.ui.dto.UpdateTradeRequest;
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
    private final GeolocationService geolocationService;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final PairDataRepository pairDataService;

    public StatarbitrageApplication(CointegrationApiHealthCheck healthCheck, PythonRestClient pythonRestClient, GeolocationService geolocationService, UpdateTradeProcessor updateTradeProcessor, PairDataRepository pairDataService) {
        this.healthCheck = healthCheck;
        this.pythonRestClient = pythonRestClient;
        this.geolocationService = geolocationService;
        this.updateTradeProcessor = updateTradeProcessor;
        this.pairDataService = pairDataService;
    }

    public static void main(String[] args) {
        SpringApplication.run(StatarbitrageApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Проверка геолокации при запуске
        geolocationService.checkGeolocationOnStartup();

        // Проверка Cointegration API
        checkCointegrationApiHealth();

        log.info("🚀 Статарбитраж приложение готово к работе!");

        updateTradingPairsAfterRestart();
    }

    private void updateTradingPairsAfterRestart() {
        pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).forEach(pairData -> updateTradeProcessor.updateTrade(UpdateTradeRequest.builder()
                .pairData(pairData)
                .build()));
    }

    private void checkCointegrationApiHealth() {
        log.debug("🚀 Проверка работоспособности API коинтеграции...");
        boolean healthy = healthCheck.isHealthy();

        if (healthy) {
            log.debug("🧪 Тестирование API коинтеграции...");
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
                        .maxPValue(0.05)
                        .build();

                pythonRestClient.discoverPairs(testData, settings);
                log.info("✅ Интеграционный тест API коинтеграции прошел успешно");

            } catch (Exception e) {
                log.error("❌ Ошибка при выполнении интеграционного теста API коинтеграции: {}", e.getMessage(), e);
            }
        }
    }

}
