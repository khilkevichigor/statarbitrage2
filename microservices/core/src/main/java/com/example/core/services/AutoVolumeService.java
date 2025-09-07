package com.example.core.services;

import com.example.core.repositories.TradingPairRepository;
import com.example.core.trading.services.OkxPortfolioManager;
import com.example.shared.dto.Portfolio;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Сервис для расчета автоматического объема позиций на основе доступных USDT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoVolumeService {
    
    private final OkxPortfolioManager okxPortfolioManager;
    private final TradingPairRepository tradingPairRepository;

    /**
     * Данные автообъема
     */
    public static class AutoVolumeData {
        private final BigDecimal longVolume;
        private final BigDecimal shortVolume;
        private final BigDecimal reserveAmount;
        
        public AutoVolumeData(BigDecimal longVolume, BigDecimal shortVolume, BigDecimal reserveAmount) {
            this.longVolume = longVolume;
            this.shortVolume = shortVolume;
            this.reserveAmount = reserveAmount;
        }
        
        public BigDecimal getLongVolume() { return longVolume; }
        public BigDecimal getShortVolume() { return shortVolume; }
        public BigDecimal getReserveAmount() { return reserveAmount; }
    }

    /**
     * Получение свободного USDT баланса с OKX
     */
    public BigDecimal getAvailableUsdtBalance() {
        try {
            Portfolio portfolio = okxPortfolioManager.getCurrentPortfolio();
            if (portfolio == null) {
                log.warn("⚠️ Не удалось получить портфолио для расчета автообъема");
                return BigDecimal.ZERO;
            }
            
            BigDecimal availableBalance = portfolio.getAvailableBalance();
            log.info("💰 Доступный USDT баланс: {}", availableBalance);
            return availableBalance;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при получении доступного USDT баланса: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Получение количества активных торговых пар со статусом TRADING
     */
    public int getActiveTradingPairsCount() {
        try {
            int count = tradingPairRepository.countByStatus(TradeStatus.TRADING);
            log.info("📊 Количество активных TRADING пар: {}", count);
            return count;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении количества активных пар: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Расчет автообъема по формуле:
     * объем_лонг = объем_шорт = свободные_USDT / (2 * (кол-во_пар - TRADING_пары + 1))
     * +1 часть остается в USDT для усреднений и просадок
     */
    public AutoVolumeData calculateAutoVolume(Settings settings) {
        try {
            BigDecimal availableUsdt = getAvailableUsdtBalance();
            int activeTradingPairs = getActiveTradingPairsCount();
            int totalPairsToTrade = (int) settings.getUsePairs();
            
            log.info("🔢 Исходные данные для расчета автообъема:");
            log.info("💰 Доступно USDT: {}", availableUsdt);
            log.info("📊 Активные TRADING пары: {}", activeTradingPairs);
            log.info("⚙️ Общее количество пар: {}", totalPairsToTrade);
            
            if (availableUsdt.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Недостаточно средств для расчета автообъема");
                return new AutoVolumeData(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            
            // Расчет по формуле: свободные_USDT / (2 * (кол-во_пар - TRADING_пары + 1))
            int denominator = 2 * (totalPairsToTrade - activeTradingPairs + 1);
            
            if (denominator <= 0) {
                log.warn("⚠️ Некорректные параметры для расчета автообъема. Знаменатель: {}", denominator);
                return new AutoVolumeData(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            
            BigDecimal positionSize = availableUsdt.divide(
                BigDecimal.valueOf(denominator), 
                2, 
                RoundingMode.HALF_UP
            );
            
            // Рассчитываем резерв (1 часть)
            BigDecimal reserve = availableUsdt.divide(
                BigDecimal.valueOf(totalPairsToTrade - activeTradingPairs + 1),
                2, 
                RoundingMode.HALF_UP
            );
            
            log.info("📈 Рассчитанный объем позиции (лонг/шорт): {}", positionSize);
            log.info("💾 Резерв на усреднения: {}", reserve);
            
            return new AutoVolumeData(positionSize, positionSize, reserve);
            
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете автообъема: {}", e.getMessage());
            return new AutoVolumeData(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /**
     * Логирование текущего состояния автообъема
     */
    public void logAutoVolumeStatus(Settings settings) {
        try {
            AutoVolumeData autoVolume = calculateAutoVolume(settings);
            
            if (settings.isAutoVolumeEnabled()) {
                log.info("✅ Автообъем включен. Рассчитанный автообъем: лонг={}, шорт={}, на усреднение={}", 
                    autoVolume.getLongVolume(), 
                    autoVolume.getShortVolume(), 
                    autoVolume.getReserveAmount());
            } else {
                log.info("Автообъем выключен. Автообъем был бы: лонг={}, шорт={}, на усреднение={}",
                    autoVolume.getLongVolume(), 
                    autoVolume.getShortVolume(), 
                    autoVolume.getReserveAmount());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при логировании состояния автообъема: {}", e.getMessage());
        }
    }

    /**
     * Получение размера позиции для лонг с учетом настроек автообъема
     */
    public BigDecimal getLongPositionSize(Settings settings) {
        try {
            if (settings.isAutoVolumeEnabled()) {
                AutoVolumeData autoVolume = calculateAutoVolume(settings);
                return autoVolume.getLongVolume();
            } else {
                return BigDecimal.valueOf(settings.getMaxLongMarginSize());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении размера лонг позиции: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Получение размера позиции для шорт с учетом настроек автообъема
     */
    public BigDecimal getShortPositionSize(Settings settings) {
        try {
            if (settings.isAutoVolumeEnabled()) {
                AutoVolumeData autoVolume = calculateAutoVolume(settings);
                return autoVolume.getShortVolume();
            } else {
                return BigDecimal.valueOf(settings.getMaxShortMarginSize());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении размера шорт позиции: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // Методы совместимости для использования без передачи настроек (будут вызываться из SettingsService)
    public AutoVolumeData calculateAutoVolume() {
        // Создаем временные настройки с дефолтными значениями для расчета
        Settings defaultSettings = Settings.builder()
                .usePairs(5) // Дефолтное значение //todo тсремно!!!
                .build();
        return calculateAutoVolume(defaultSettings);
    }
}