package com.example.core.services;

import com.example.core.repositories.PortfolioHistoryRepository;
import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.dto.Portfolio;
import com.example.shared.models.PortfolioHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления историей портфолио
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioHistoryService {

    private final PortfolioHistoryRepository portfolioHistoryRepository;
    private final TradingIntegrationService tradingIntegrationService;

    /**
     * Сохраняет текущее состояние портфолио в историю
     */
    public void saveCurrentPortfolioSnapshot() {
        try {
            log.debug("🔄 Сохранение снапшота портфолио в историю");

            Portfolio currentPortfolio = tradingIntegrationService.getPortfolioInfo();
            if (currentPortfolio == null) {
                log.warn("⚠️ Текущий портфолио недоступен - снапшот не сохранен");
                return;
            }

            String providerType = tradingIntegrationService.getCurrentTradingMode().name();
            PortfolioHistory historyRecord = new PortfolioHistory(currentPortfolio, providerType);

            portfolioHistoryRepository.save(historyRecord);
            log.debug("✅ Снапшот портфолио сохранен: баланс={}, время={}",
                    currentPortfolio.getTotalBalance(), historyRecord.getSnapshotTime());

        } catch (Exception e) {
            log.error("❌ Ошибка при сохранении снапшота портфолио", e);
        }
    }

    /**
     * Автоматическое сохранение снапшота каждые 15 минут
     */
    @Scheduled(fixedRate = 900000) // 15 минут = 15 * 60 * 1000 мс
    public void schedulePortfolioSnapshot() {
        log.debug("📷 Запуск автоматического снапшота портфолио");
        saveCurrentPortfolioSnapshot();
    }

    /**
     * Получить историю портфолио за указанный период
     */
    public List<PortfolioHistory> getPortfolioHistory(LocalDateTime fromTime) {
        try {
            List<PortfolioHistory> history = portfolioHistoryRepository.findBySnapshotTimeGreaterThanEqual(fromTime);
            log.debug("📊 Получена история портфолио: {} записей с {}", history.size(), fromTime);
            return history;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении истории портфолио", e);
            return List.of();
        }
    }

    /**
     * Получить историю портфолио за период между двумя датами
     */
    public List<PortfolioHistory> getPortfolioHistory(LocalDateTime fromTime, LocalDateTime toTime) {
        try {
            List<PortfolioHistory> history = portfolioHistoryRepository.findBySnapshotTimeBetween(fromTime, toTime);
            log.debug("📊 Получена история портфолио: {} записей с {} по {}", history.size(), fromTime, toTime);
            return history;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении истории портфолио за период", e);
            return List.of();
        }
    }

    /**
     * Получить последнюю запись из истории
     */
    public PortfolioHistory getLatestRecord() {
        try {
            return portfolioHistoryRepository.findLatest();
        } catch (Exception e) {
            log.error("❌ Ошибка при получении последней записи истории портфолио", e);
            return null;
        }
    }

    /**
     * Очистка старых записей (старше 3 месяцев)
     */
    @Scheduled(cron = "0 0 2 * * ?") // Каждый день в 2:00 AM
    public void cleanupOldRecords() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(3);
            portfolioHistoryRepository.deleteBySnapshotTimeBefore(cutoffDate);
            log.info("🧹 Очищены записи истории портфолио старше {}", cutoffDate);
        } catch (Exception e) {
            log.error("❌ Ошибка при очистке старых записей истории портфолио", e);
        }
    }

    /**
     * Получить количество записей за период
     */
    public long getRecordCount(LocalDateTime fromTime) {
        try {
            return portfolioHistoryRepository.countBySnapshotTimeGreaterThanEqual(fromTime);
        } catch (Exception e) {
            log.error("❌ Ошибка при подсчете записей истории портфолио", e);
            return 0;
        }
    }
}