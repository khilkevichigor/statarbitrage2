package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.TradingProviderSwitchResult;
import com.example.statarbitrage.trading.providers.Real3CommasTradingProvider;
import com.example.statarbitrage.trading.providers.RealOkxTradingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Фабрика для создания и выбора провайдеров торговли
 */
@Slf4j
@Service
public class TradingProviderFactory {

    private final Real3CommasTradingProvider real3CommasTradingProvider;
    private final RealOkxTradingProvider realOkxTradingProvider;

    // Текущий активный провайдер
    private TradingProvider currentProvider;
    private TradingProviderType currentProviderType;

    public TradingProviderFactory(
            Real3CommasTradingProvider real3CommasTradingProvider,
            RealOkxTradingProvider realOkxTradingProvider) {
        this.real3CommasTradingProvider = real3CommasTradingProvider;
        this.realOkxTradingProvider = realOkxTradingProvider;

        // По умолчанию используем okx
        switchToProvider(TradingProviderType.REAL_OKX);
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
     * Переключиться на другой провайдер торговли с детальной информацией
     */
    public TradingProviderSwitchResult switchToProviderWithDetails(TradingProviderType providerType) {
        try {
            TradingProvider newProvider = createProvider(providerType);

            if (newProvider == null) {
                log.error("❌ Не удалось создать провайдер типа: {}", providerType);
                return createProviderNotImplementedResult(providerType);
            }

            // Проверяем подключение
            if (!newProvider.isConnected()) {
                log.warn("⚠️ Провайдер {} не подключен", providerType.getDisplayName());
                return createConnectionErrorResult(providerType);
            }

            this.currentProvider = newProvider;
            this.currentProviderType = providerType;

            log.info("✅ Переключились на провайдер: {}", providerType.getDisplayName());
            return TradingProviderSwitchResult.success();

        } catch (Exception e) {
            log.error("❌ Ошибка при переключении на провайдер {}: {}", providerType, e.getMessage());
            return TradingProviderSwitchResult.failure(
                    TradingProviderSwitchResult.SwitchErrorType.INTERNAL_ERROR,
                    e.getMessage(),
                    "Произошла внутренняя ошибка системы",
                    "Попробуйте перезапустить приложение или обратитесь в техподдержку"
            );
        }
    }

    /**
     * Переключиться на другой провайдер торговли
     */
    public void switchToProvider(TradingProviderType providerType) {
        try {
            TradingProvider newProvider = createProvider(providerType);

            if (newProvider == null) {
                log.error("❌ Не удалось создать провайдер типа: {}", providerType);
                return;
            }

            // Проверяем подключение
            if (!newProvider.isConnected()) {
                log.warn("⚠️ Провайдер {} не подключен", providerType.getDisplayName());
                return; // Для реальной торговли требуется подключение
            }

            this.currentProvider = newProvider;
            this.currentProviderType = providerType;

            log.info("✅ Переключились на провайдер: {}", providerType.getDisplayName());

        } catch (Exception e) {
            log.error("❌ Ошибка при переключении на провайдер {}: {}", providerType, e.getMessage());
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

    private TradingProvider createProvider(TradingProviderType providerType) {
        return switch (providerType) {
            case REAL_3COMMAS -> real3CommasTradingProvider;
            case REAL_OKX -> realOkxTradingProvider;
        };
    }

    private TradingProviderSwitchResult createProviderNotImplementedResult(TradingProviderType providerType) {
        return switch (providerType) {
            case REAL_3COMMAS -> TradingProviderSwitchResult.failure(
                    TradingProviderSwitchResult.SwitchErrorType.PROVIDER_NOT_IMPLEMENTED,
                    "3Commas провайдер не реализован",
                    "Режим 3Commas API пока не доступен",
                    "Используйте виртуальную торговлю или OKX API"
            );
            default -> TradingProviderSwitchResult.failure(
                    TradingProviderSwitchResult.SwitchErrorType.PROVIDER_NOT_IMPLEMENTED,
                    "Провайдер " + providerType + " не реализован",
                    "Данный режим торговли пока не доступен",
                    "Используйте виртуальную торговлю"
            );
        };
    }

    private TradingProviderSwitchResult createConnectionErrorResult(TradingProviderType providerType) {
        return switch (providerType) {
            case REAL_OKX -> TradingProviderSwitchResult.failure(
                    TradingProviderSwitchResult.SwitchErrorType.CONFIGURATION_MISSING,
                    "OKX API ключи не настроены",
                    "Для использования OKX API необходимо настроить ключи доступа",
                    "Добавьте API ключи в application.properties: okx.api.key, okx.api.secret, okx.api.passphrase"
            );
            case REAL_3COMMAS -> TradingProviderSwitchResult.failure(
                    TradingProviderSwitchResult.SwitchErrorType.CONFIGURATION_MISSING,
                    "3Commas API ключи не настроены",
                    "Для использования 3Commas API необходимо настроить ключи доступа",
                    "Добавьте API ключи в настройки приложения"
            );
            default -> TradingProviderSwitchResult.failure(
                    TradingProviderSwitchResult.SwitchErrorType.CONNECTION_ERROR,
                    "Не удалось подключиться к " + providerType.getDisplayName(),
                    "Проверьте настройки подключения",
                    "Убедитесь, что API ключи корректны и есть интернет соединение"
            );
        };
    }
}