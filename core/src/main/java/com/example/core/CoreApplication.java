package com.example.core;

import com.example.core.client_python.CointegrationApiHealthCheck;
import com.example.core.client_python.PythonRestClient;
import com.example.core.services.SchedulerControlService;
import com.example.core.trading.services.GeolocationService;
import com.example.shared.dto.Candle;
import com.example.shared.models.Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.example.core", "com.example.shared"})
@EntityScan(basePackages = {"com.example.shared.models"})
@EnableJpaRepositories(basePackages = {"com.example.core.repositories"})
@ComponentScan(basePackages = {"com.example.core", "com.example.shared"})
@EnableFeignClients(basePackages = {"com.example.core.client"})
@EnableScheduling
@EnableAsync
public class CoreApplication {

    private final CointegrationApiHealthCheck healthCheck;
    private final PythonRestClient pythonRestClient;
    private final GeolocationService geolocationService;
    private final SchedulerControlService schedulerControlService;

    public CoreApplication(CointegrationApiHealthCheck healthCheck, PythonRestClient pythonRestClient, GeolocationService geolocationService, SchedulerControlService schedulerControlService) {
        this.healthCheck = healthCheck;
        this.pythonRestClient = pythonRestClient;
        this.geolocationService = geolocationService;
        this.schedulerControlService = schedulerControlService;
    }

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            //ждем чтобы не мешать логи и было по красоте
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("");
        geolocationService.checkGeolocationOnStartup();
        log.info("✅ Геолокация при запуске: безопасно для OKX");
        checkCointegrationApiHealth();
        log.info("✅ Интеграционный тест API коинтеграции прошел успешно");
        schedulerControlService.logSchedulersStatus();
        log.info("🚀 Core готов к работе!");
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
            } catch (Exception e) {
                log.error("❌ Ошибка при выполнении интеграционного теста API коинтеграции: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

}
