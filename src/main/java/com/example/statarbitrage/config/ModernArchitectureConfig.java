package com.example.statarbitrage.config;

import com.example.statarbitrage.adapter.TradingPairAdapter;
import com.example.statarbitrage.client.PythonRestClient;
import com.example.statarbitrage.processor.ModernFetchPairsProcessor;
import com.example.statarbitrage.processor.ModernStartTradeProcessor;
import com.example.statarbitrage.processor.ModernUpdateTradeProcessor;
import com.example.statarbitrage.service.ModernPairDataService;
import com.example.statarbitrage.service.ModernValidateService;
import com.example.statarbitrage.service.ModernZScoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация новой архитектуры с TradingPair
 * Позволяет переключаться между старой и новой архитектурой
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "trading.architecture.modern.enabled", havingValue = "true", matchIfMissing = false)
public class ModernArchitectureConfig {

    // PythonRestClient и TradingPairAdapter теперь используют @Component аннотации

    @Bean
    public ModernZScoreService modernZScoreService(TradingPairAdapter adapter) {
        log.info("🔧 Создание ModernZScoreService");
        return new ModernZScoreService(adapter);
    }

    @Bean
    public ModernPairDataService modernPairDataService() {
        log.info("🔧 Создание ModernPairDataService");
        return new ModernPairDataService();
    }

    @Bean
    public ModernValidateService modernValidateService() {
        log.info("🔧 Создание ModernValidateService");
        return new ModernValidateService();
    }

    @Bean
    public ModernFetchPairsProcessor modernFetchPairsProcessor(
            ModernZScoreService zScoreService,
            ModernPairDataService pairDataService) {
        log.info("🔧 Создание ModernFetchPairsProcessor");
        return new ModernFetchPairsProcessor(zScoreService, pairDataService);
    }

    @Bean
    public ModernStartTradeProcessor modernStartTradeProcessor(
            ModernZScoreService zScoreService,
            ModernPairDataService pairDataService) {
        log.info("🔧 Создание ModernStartTradeProcessor");
        return new ModernStartTradeProcessor(zScoreService, pairDataService);
    }

    @Bean
    public ModernUpdateTradeProcessor modernUpdateTradeProcessor(
            ModernZScoreService zScoreService,
            ModernPairDataService pairDataService) {
        log.info("🔧 Создание ModernUpdateTradeProcessor");
        return new ModernUpdateTradeProcessor(zScoreService, pairDataService);
    }
}