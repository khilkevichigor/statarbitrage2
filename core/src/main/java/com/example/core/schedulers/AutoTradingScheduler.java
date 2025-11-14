package com.example.core.schedulers;

import com.example.core.processors.FetchPairsProcessor;
import com.example.core.processors.StartNewTradeProcessor;
import com.example.core.services.SchedulerControlService;
import com.example.core.services.SettingsService;
import com.example.core.trading.services.OkxPortfolioManager;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 🤖 Шедуллер автоматического трейдинга
 * Проверяет хорошие пары из таблиц "Найденные стабильные пары" и "Постоянный список для мониторинга"
 * Открывает новые позиции автоматически при включенном автотрейдинге
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoTradingScheduler {

    private final SettingsService settingsService;
    private final SchedulerControlService schedulerControlService;
    private final FetchPairsProcessor fetchPairsProcessor;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final OkxPortfolioManager okxPortfolioManager;

    /**
     * Автоматический поиск и открытие новых торговых позиций
     * Запускается каждые 5 минут
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void autoTradingScheduled() {
        log.debug("🤖 Проверка автотрейдинга в {}", LocalDateTime.now());

        try {
            // Проверяем включен ли автотрейдинг шедуллер
            if (!schedulerControlService.isAutoTradingSchedulerEnabled()) {
                log.debug("📅 AutoTradingScheduler отключен в настройках - пропускаем выполнение");
                return;
            }

            Settings settings = settingsService.getSettings();

            log.info("🤖 Запуск автоматического трейдинга в {}", LocalDateTime.now());

            // Проверяем можем ли открыть новые позиции
            if (!canOpenNewPositions(settings)) {
                log.info("🚫 Нельзя открывать новые позиции - лимит достигнут");
                return;
            }

            // Ищем хорошие пары из стабильных источников
            List<Pair> candidatePairs = findCandidatePairs(settings);
            if (candidatePairs.isEmpty()) {
                log.debug("📊 Не найдено подходящих пар для автотрейдинга");
                return;
            }

            // Пытаемся открыть новые торговые позиции
            int newTradesOpened = openNewTrades(candidatePairs, settings);
            
            if (newTradesOpened > 0) {
                log.info("✅ Автотрейдинг: открыто {} новых позиций", newTradesOpened);
            } else {
                log.info("📊 Автотрейдинг: новые позиции не открыты");
            }

        } catch (Exception e) {
            log.error("💥 Критическая ошибка в автотрейдинге: {}", e.getMessage(), e);
        }
    }

    /**
     * Проверяет можем ли открыть новые позиции
     */
    private boolean canOpenNewPositions(Settings settings) {
        try {
            // Получаем текущее количество открытых позиций с биржи
            int currentPositions = okxPortfolioManager.getActivePositionsCount();
            
            // Вычисляем максимальное количество позиций (пары * 2)
            int maxPairs = (int) settings.getUsePairs();
            int maxPositions = maxPairs * 2;
            
            // Проверяем есть ли место для новой пары (2 позиции)
            boolean canOpen = (currentPositions + 2) <= maxPositions;
            
            log.debug("📊 Позиции: текущие={}, максимум={}, можно_открыть={}", 
                    currentPositions, maxPositions, canOpen);
            
            return canOpen;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке возможности открытия позиций: {}", e.getMessage());
            return false; // Безопасно отказываемся от торговли при ошибке
        }
    }

    /**
     * Ищет кандидатов для автотрейдинга из стабильных пар
     */
    private List<Pair> findCandidatePairs(Settings settings) {
        try {
            log.debug("🔍 Поиск кандидатов для автотрейдинга из стабильных источников");

            // Создаем запрос для поиска пар
            // Ограничиваем количество до 1 пары для консервативного подхода
            FetchPairsRequest request = FetchPairsRequest.builder()
                    .countOfPairs(1) // Берем только 1 лучшую пару за раз
                    .build();

            // Используем FetchPairsProcessor для получения лучших пар
            List<Pair> pairs = fetchPairsProcessor.fetchPairs(request);
            
            log.debug("📋 Найдено {} кандидатов для автотрейдинга", pairs.size());
            
            return pairs;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при поиске кандидатов для автотрейдинга: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Открывает новые торговые позиции
     */
    private int openNewTrades(List<Pair> candidatePairs, Settings settings) {
        int successCount = 0;
        
        for (Pair pair : candidatePairs) {
            try {
                log.info("🚀 Попытка открыть автоматическую позицию для пары: {}", pair.getPairName());
                
                // Создаем запрос на открытие новой торговой позиции
                StartNewTradeRequest tradeRequest = StartNewTradeRequest.builder()
                        .tradingPair(pair)
                        .checkAutoTrading(true) // Включаем проверку автотрейдинга
                        .build();
                
                // Пытаемся открыть торговую позицию
                Pair result = startNewTradeProcessor.startNewTrade(tradeRequest);
                
                if (result != null && TradeStatus.TRADING.equals(result.getStatus())) {
                    successCount++;
                    log.info("✅ Автотрейдинг: успешно открыта позиция для пары {}", pair.getPairName());
                    
                    // Проверяем можем ли открыть еще одну позицию
                    if (!canOpenNewPositions(settings)) {
                        log.info("🚫 Лимит позиций достигнут - прекращаем автотрейдинг");
                        break;
                    }
                } else {
                    log.warn("⚠️ Автотрейдинг: не удалось открыть позицию для пары {}", pair.getPairName());
                }
                
            } catch (Exception e) {
                log.error("❌ Ошибка при открытии автоматической позиции для пары {}: {}", 
                        pair.getPairName(), e.getMessage());
            }
        }
        
        return successCount;
    }
}