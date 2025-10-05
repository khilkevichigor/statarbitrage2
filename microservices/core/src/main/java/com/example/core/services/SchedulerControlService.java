package com.example.core.services;

import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Сервис для управления шедуллерами через настройки
 * Предоставляет централизованную проверку вкл/откл состояния шедуллеров
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerControlService {

    private final SettingsService settingsService;

    /**
     * Проверка включения UpdateTradesScheduler (обновление торговых пар каждую минуту)
     */
    public boolean isUpdateTradesSchedulerEnabled() {
        try {
            Settings settings = settingsService.getSettings();
            Boolean enabled = settings.getSchedulerUpdateTradesEnabled();
            boolean result = enabled != null ? enabled : true; // По умолчанию включен если null
            log.debug("📅 UpdateTradesScheduler: {}", result ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН");
            return result;
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке состояния UpdateTradesScheduler: {}", e.getMessage());
            return true; // По умолчанию включен при ошибке
        }
    }

    /**
     * Проверка включения StablePairsScheduler (поиск стабильных пар ночью)
     */
    public boolean isStablePairsSchedulerEnabled() {
        try {
            Settings settings = settingsService.getSettings();
            Boolean enabled = settings.getSchedulerStablePairsEnabled();
            boolean result = enabled != null ? enabled : true;
            log.debug("📅 StablePairsScheduler: {}", result ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН");
            return result;
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке состояния StablePairsScheduler: {}", e.getMessage());
            return true; // По умолчанию включен при ошибке
        }
    }

    /**
     * Проверка включения Portfolio Snapshot Scheduler (снапшот каждые 15 минут)
     */
    public boolean isPortfolioSnapshotSchedulerEnabled() {
        try {
            Settings settings = settingsService.getSettings();
            Boolean enabled = settings.getSchedulerPortfolioSnapshotEnabled();
            boolean result = enabled != null ? enabled : true;
            log.debug("📅 PortfolioSnapshotScheduler: {}", result ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН");
            return result;
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке состояния PortfolioSnapshotScheduler: {}", e.getMessage());
            return true; // По умолчанию включен при ошибке
        }
    }

    /**
     * Проверка включения Portfolio Cleanup Scheduler (очистка каждый день в 2:00)
     */
    public boolean isPortfolioCleanupSchedulerEnabled() {
        try {
            Settings settings = settingsService.getSettings();
            Boolean enabled = settings.getSchedulerPortfolioCleanupEnabled();
            boolean result = enabled != null ? enabled : true;
            log.debug("📅 PortfolioCleanupScheduler: {}", result ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН");
            return result;
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке состояния PortfolioCleanupScheduler: {}", e.getMessage());
            return true; // По умолчанию включен при ошибке
        }
    }

    /**
     * Получить CRON выражение для StablePairsScheduler
     */
    public String getStablePairsSchedulerCron() {
        try {
            Settings settings = settingsService.getSettings();
            String cron = settings.getSchedulerStablePairsCron();
            log.debug("📅 StablePairsScheduler CRON: {}", cron);
            return cron != null ? cron : "0 10 1 * * *"; // По умолчанию 02:10
        } catch (Exception e) {
            log.error("❌ Ошибка при получении CRON для StablePairsScheduler: {}", e.getMessage());
            return "0 10 1 * * *"; // По умолчанию
        }
    }

    /**
     * Получить CRON выражение для Portfolio Cleanup Scheduler
     */
    public String getPortfolioCleanupSchedulerCron() {
        try {
            Settings settings = settingsService.getSettings();
            String cron = settings.getSchedulerPortfolioCleanupCron();
            log.debug("📅 PortfolioCleanupScheduler CRON: {}", cron);
            return cron != null ? cron : "0 0 2 * * ?"; // По умолчанию 02:00
        } catch (Exception e) {
            log.error("❌ Ошибка при получении CRON для PortfolioCleanupScheduler: {}", e.getMessage());
            return "0 0 2 * * ?"; // По умолчанию
        }
    }

    /**
     * Логирование состояния всех шедуллеров
     */
    public void logSchedulersStatus() {
        log.info("📅 === СОСТОЯНИЕ ШЕДУЛЛЕРОВ ===");
        log.info("📅 UpdateTradesScheduler (каждую минуту): {}", 
                isUpdateTradesSchedulerEnabled() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН");
        log.info("📅 StablePairsScheduler ({}): {}", 
                getStablePairsSchedulerCron(),
                isStablePairsSchedulerEnabled() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН");
        log.info("📅 PortfolioSnapshotScheduler (каждые 15 минут): {}", 
                isPortfolioSnapshotSchedulerEnabled() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН");
        log.info("📅 PortfolioCleanupScheduler ({}): {}", 
                getPortfolioCleanupSchedulerCron(),
                isPortfolioCleanupSchedulerEnabled() ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН");
        log.info("📅 ================================");
    }

    /**
     * Проверка включения любых шедуллеров для Candles микросервиса (для справки)
     */
    public boolean isCandleCacheSyncSchedulerEnabled() {
        try {
            Settings settings = settingsService.getSettings();
            Boolean enabled = settings.getSchedulerCandleCacheSyncEnabled();
            return enabled != null ? enabled : true;
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке состояния CandleCacheSyncScheduler: {}", e.getMessage());
            return true;
        }
    }

    public boolean isCandleCacheUpdateSchedulerEnabled() {
        try {
            Settings settings = settingsService.getSettings();
            Boolean enabled = settings.getSchedulerCandleCacheUpdateEnabled();
            return enabled != null ? enabled : true;
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке состояния CandleCacheUpdateScheduler: {}", e.getMessage());
            return true;
        }
    }

    public boolean isCandleCacheStatsSchedulerEnabled() {
        try {
            Settings settings = settingsService.getSettings();
            Boolean enabled = settings.getSchedulerCandleCacheStatsEnabled();
            return enabled != null ? enabled : true;
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке состояния CandleCacheStatsScheduler: {}", e.getMessage());
            return true;
        }
    }
}