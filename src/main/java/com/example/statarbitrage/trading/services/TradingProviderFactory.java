package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.providers.Real3CommasTradingProvider;
import com.example.statarbitrage.trading.providers.VirtualTradingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Фабрика для создания и выбора провайдеров торговли
 */
@Slf4j
@Service
public class TradingProviderFactory {

    private final VirtualTradingProvider virtualTradingProvider;
    private final Real3CommasTradingProvider real3CommasTradingProvider;

    // Текущий активный провайдер
    private TradingProvider currentProvider;
    private TradingProviderType currentProviderType;

    public TradingProviderFactory(VirtualTradingProvider virtualTradingProvider,
                                  Real3CommasTradingProvider real3CommasTradingProvider) {
        this.virtualTradingProvider = virtualTradingProvider;
        this.real3CommasTradingProvider = real3CommasTradingProvider;

        // По умолчанию используем виртуальную торговлю
        switchToProvider(TradingProviderType.VIRTUAL);
    }

    /**
     * Получить текущий активный провайдер
     */
    public TradingProvider getCurrentProvider() {
        return currentProvider;
    }

    /**
     * Получить тип текущего провайдера
     */
    public TradingProviderType getCurrentProviderType() {
        return currentProviderType;
    }

    /**
     * Переключиться на другой провайдер торговли
     */
    public boolean switchToProvider(TradingProviderType providerType) {
        try {
            TradingProvider newProvider = createProvider(providerType);

            if (newProvider == null) {
                log.error("❌ Не удалось создать провайдер типа: {}", providerType);
                return false;
            }

            // Проверяем подключение
            if (!newProvider.isConnected()) {
                log.warn("⚠️ Провайдер {} не подключен", providerType.getDisplayName());
                if (providerType.isReal()) {
                    return false; // Для реальной торговли требуется подключение
                }
            }

            this.currentProvider = newProvider;
            this.currentProviderType = providerType;

            log.info("✅ Переключились на провайдер: {}", providerType.getDisplayName());
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при переключении на провайдер {}: {}", providerType, e.getMessage());
            return false;
        }
    }

    /**
     * Получить провайдер по типу
     */
    public TradingProvider getProvider(TradingProviderType providerType) {
        return createProvider(providerType);
    }

    /**
     * Проверить доступность провайдера
     */
    public boolean isProviderAvailable(TradingProviderType providerType) {
        try {
            TradingProvider provider = createProvider(providerType);
            return provider != null && provider.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получить список всех доступных провайдеров
     */
    public TradingProviderType[] getAvailableProviders() {
        return TradingProviderType.values();
    }

    /**
     * Безопасное переключение с fallback на виртуальную торговлю
     */
    public void safeSwitchToProvider(TradingProviderType providerType) {
        if (!switchToProvider(providerType)) {
            log.warn("⚠️ Не удалось переключиться на {}, возвращаемся к виртуальной торговле",
                    providerType.getDisplayName());
            switchToProvider(TradingProviderType.VIRTUAL);
        }
    }

    private TradingProvider createProvider(TradingProviderType providerType) {
        return switch (providerType) {
            case VIRTUAL -> virtualTradingProvider;
            case REAL_3COMMAS -> real3CommasTradingProvider;
            case REAL_OKX -> {
                log.warn("⚠️ Провайдер OKX пока не реализован");
                yield null;
            }
            case REAL_BINANCE -> {
                log.warn("⚠️ Провайдер Binance пока не реализован");
                yield null;
            }
            case REAL_BYBIT -> {
                log.warn("⚠️ Провайдер Bybit пока не реализован");
                yield null;
            }
        };
    }
}