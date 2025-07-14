package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.services.SettingsService;
import com.example.statarbitrage.trading.interfaces.PortfolioManager;
import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.repositories.PortfolioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Менеджер виртуального портфолио
 */
@Slf4j
@Service
@DependsOn("dbInitializer")
public class VirtualPortfolioManager implements PortfolioManager {

    private final PortfolioRepository portfolioRepository;
    private final SettingsService settingsService;

    private Portfolio currentPortfolio;
    private final Object portfolioLock = new Object();

    public VirtualPortfolioManager(PortfolioRepository portfolioRepository, SettingsService settingsService) {
        this.portfolioRepository = portfolioRepository;
        this.settingsService = settingsService;
        loadPortfolio();
    }

    @Override
    @Transactional
    public void initializePortfolio(BigDecimal initialBalance) {
        synchronized (portfolioLock) {
            Optional<Portfolio> existingPortfolio = portfolioRepository.findFirstByOrderByIdDesc();

            if (existingPortfolio.isPresent()) {
                log.info("📊 Портфолио уже существует с балансом: {}", existingPortfolio.get().getTotalBalance());
                this.currentPortfolio = existingPortfolio.get();
                return;
            }

            // Создаем новое портфолио
            Portfolio portfolio = Portfolio.builder()
                    .totalBalance(initialBalance)
                    .availableBalance(initialBalance)
                    .reservedBalance(BigDecimal.ZERO)
                    .initialBalance(initialBalance)
                    .unrealizedPnL(BigDecimal.ZERO)
                    .realizedPnL(BigDecimal.ZERO)
                    .totalFeesAccrued(BigDecimal.ZERO)
                    .maxDrawdown(BigDecimal.ZERO)
                    .highWaterMark(initialBalance)
                    .activePositionsCount(0)
                    .createdAt(LocalDateTime.now())
                    .lastUpdated(LocalDateTime.now())
                    .build();

            this.currentPortfolio = saveWithRetry(portfolio);
            log.info("✅ Создано новое портфолио с начальным балансом: {}", initialBalance);
        }
    }

    @Override
    public Portfolio getCurrentPortfolio() {
        synchronized (portfolioLock) {
            if (currentPortfolio == null) {
                loadPortfolio();
            }
            return currentPortfolio;
        }
    }

    @Override
    @Transactional
    public boolean reserveBalance(BigDecimal amount) {
        synchronized (portfolioLock) {
            if (currentPortfolio.getAvailableBalance().compareTo(amount) < 0) {
                log.warn("⚠️ Недостаточно средств для резервирования: требуется {}, доступно {}",
                        amount, currentPortfolio.getAvailableBalance());
                return false;
            }

            currentPortfolio.setAvailableBalance(currentPortfolio.getAvailableBalance().subtract(amount));
            currentPortfolio.setReservedBalance(currentPortfolio.getReservedBalance().add(amount));
            currentPortfolio.setLastUpdated(LocalDateTime.now());

            currentPortfolio = saveWithRetry(currentPortfolio);
            log.debug("💰 Зарезервировано: {} | Доступно: {} | Зарезервировано: {}",
                    amount, currentPortfolio.getAvailableBalance(), currentPortfolio.getReservedBalance());
            return true;
        }
    }

    @Override
    @Transactional
    public void releaseReservedBalance(BigDecimal amount) {
        synchronized (portfolioLock) {
            currentPortfolio.setReservedBalance(currentPortfolio.getReservedBalance().subtract(amount));
            currentPortfolio.setAvailableBalance(currentPortfolio.getAvailableBalance().add(amount));
            currentPortfolio.setLastUpdated(LocalDateTime.now());

            currentPortfolio = saveWithRetry(currentPortfolio);
            log.debug("💸 Освобождено: {} | Доступно: {} | Зарезервировано: {}",
                    amount, currentPortfolio.getAvailableBalance(), currentPortfolio.getReservedBalance());
        }
    }

    @Override
    @Transactional
    public void onPositionOpened(Position position) {
        synchronized (portfolioLock) {
            currentPortfolio.setActivePositionsCount(currentPortfolio.getActivePositionsCount() + 1);
            currentPortfolio.setLastUpdated(LocalDateTime.now());

            currentPortfolio = saveWithRetry(currentPortfolio);
            log.info("📈 Открыта позиция: {} | Активных позиций: {}",
                    position.getSymbol(), currentPortfolio.getActivePositionsCount());
        }
    }

    @Override
    @Transactional
    public void onPositionClosed(Position position, BigDecimal pnl, BigDecimal fees) {
        synchronized (portfolioLock) {
            // Обновляем баланс
            currentPortfolio.setTotalBalance(currentPortfolio.getTotalBalance().add(pnl));
            currentPortfolio.setRealizedPnL(currentPortfolio.getRealizedPnL().add(pnl));
            currentPortfolio.setTotalFeesAccrued(currentPortfolio.getTotalFeesAccrued().add(fees));
            currentPortfolio.setActivePositionsCount(currentPortfolio.getActivePositionsCount() - 1);

            // Обновляем доступный баланс
            currentPortfolio.setAvailableBalance(currentPortfolio.getAvailableBalance().add(position.getAllocatedAmount()).add(pnl));

            // Обновляем максимальную просадку
            currentPortfolio.updateMaxDrawdown();

            currentPortfolio.setLastUpdated(LocalDateTime.now());
            currentPortfolio = saveWithRetry(currentPortfolio);

            log.info("📉 Закрыта позиция: {} | PnL: {} | Общий баланс: {} | Активных позиций: {}",
                    position.getSymbol(), pnl, currentPortfolio.getTotalBalance(),
                    currentPortfolio.getActivePositionsCount());
        }
    }

    @Override
    public BigDecimal calculateMaxPositionSize() {
        synchronized (portfolioLock) {
            Settings settings = settingsService.getSettings();

            // Получаем процент от портфолио на одну позицию (по умолчанию 10%)
            BigDecimal maxPositionPercent = BigDecimal.valueOf(10); // Можно вынести в настройки

            BigDecimal maxByPercent = currentPortfolio.getTotalBalance()
                    .multiply(maxPositionPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Не больше доступного баланса
            return maxByPercent.min(currentPortfolio.getAvailableBalance());
        }
    }

    @Override
    public boolean hasAvailableBalance(BigDecimal amount) {
        synchronized (portfolioLock) {
            return currentPortfolio.getAvailableBalance().compareTo(amount) >= 0;
        }
    }

    @Override
    public BigDecimal getPortfolioReturn() {
        synchronized (portfolioLock) {
            return currentPortfolio.getTotalReturn();
        }
    }

    @Override
    public BigDecimal getMaxDrawdown() {
        synchronized (portfolioLock) {
            return currentPortfolio.getMaxDrawdown();
        }
    }

    @Override
    @Transactional
    public void updatePortfolioValue() {
        synchronized (portfolioLock) {
            // Этот метод вызывается при обновлении цен позиций
            // Нереализованная прибыль будет рассчитана при следующем запросе
            currentPortfolio.updateMaxDrawdown();
            currentPortfolio.setLastUpdated(LocalDateTime.now());
            currentPortfolio = saveWithRetry(currentPortfolio);
        }
    }

    @Override
    @Transactional
    public void savePortfolio() {
        synchronized (portfolioLock) {
            if (currentPortfolio != null) {
                currentPortfolio.setLastUpdated(LocalDateTime.now());
                currentPortfolio = saveWithRetry(currentPortfolio);
            }
        }
    }

    @Override
    public void loadPortfolio() {
        synchronized (portfolioLock) {
            Optional<Portfolio> portfolio = portfolioRepository.findFirstByOrderByIdDesc();

            if (portfolio.isPresent()) {
                this.currentPortfolio = portfolio.get();
                log.info("📊 Загружено портфолио: баланс {}, доходность {}%",
                        currentPortfolio.getTotalBalance(), currentPortfolio.getTotalReturn());
            } else {
                // Инициализируем с настройками по умолчанию
                Settings settings = settingsService.getSettings();
                BigDecimal initialVirtualBalance = BigDecimal.valueOf(settings.getInitialVirtualBalance());
                initializePortfolio(initialVirtualBalance);
            }
        }
    }

    /**
     * Сохранение портфолио с повторными попытками для обработки блокировок SQLite
     */
    private Portfolio saveWithRetry(Portfolio portfolio) {
        int maxRetries = 3;
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                return portfolioRepository.save(portfolio);
            } catch (DataAccessException e) {
                attempts++;
                if (e.getMessage() != null && e.getMessage().contains("SQLITE_BUSY")) {
                    log.warn("⚠️ База данных заблокирована, попытка {}/{}: {}", attempts, maxRetries, e.getMessage());
                    if (attempts >= maxRetries) {
                        log.error("❌ Не удалось сохранить портфолио после {} попыток", maxRetries);
                        throw new RuntimeException("Не удалось сохранить портфолио из-за блокировки базы данных", e);
                    }
                    try {
                        Thread.sleep(50 + (attempts * 25L)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Прерван поток при повторной попытке сохранения", ie);
                    }
                } else {
                    throw e; // Другие исключения сразу пробрасываем
                }
            }
        }

        throw new RuntimeException("Неожиданная ошибка в saveWithRetry");
    }
}