package com.example.core.services;

import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.models.Settings;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartNewTradeValidationService {
    private final SettingsService settingsService;
    private final TradingIntegrationService tradingIntegrationServiceImpl;

    public void validateRequest(StartNewTradeRequest request) {
        if (request == null || request.getTradingPair() == null) {
            throw new IllegalArgumentException("Неверный запрос на начало нового трейда");
        }
    }

    public boolean validateTickers(TradingPair tradingPair, ZScoreData zScoreData) {
        return Objects.equals(tradingPair.getLongTicker(), zScoreData.getUnderValuedTicker()) &&
                Objects.equals(tradingPair.getShortTicker(), zScoreData.getOverValuedTicker());
    }

    public boolean validateAutoTrading(TradingPair tradingPair, boolean checkAutoTrading) {
        if (!checkAutoTrading) {
            log.debug("🔧 Ручной запуск трейда - проверка автотрейдинга пропущена для пары {}", tradingPair.getPairName());
            return true;
        }

        Settings currentSettings = settingsService.getSettings(); //снова читаем из бд
        log.debug("📖 Процессор: Читаем настройки из БД: autoTrading={}", currentSettings.isAutoTradingEnabled());

        if (!currentSettings.isAutoTradingEnabled()) {
            log.warn("⚠️ Автотрейдинг отключен! Пропускаю открытие нового трейда для пары {}", tradingPair.getPairName());
            return false;
        }

        log.debug("✅ Процессор: Автотрейдинг включен, продолжаем");
        return true;
    }

    public boolean isLastZLessThenMinZ(TradingPair tradingPair, Settings settings) {
        if (tradingPair == null) {
            throw new IllegalArgumentException("pairData is null");
        }

        double zScore = tradingPair.getZScoreCurrent();
        if (zScore < settings.getMinZ()) {
            if (zScore < 0) {
                log.warn("⚠️ Пропускаю пару {}. Z-скор {} < 0", tradingPair.getPairName(), zScore);
            } else {
                log.warn("⚠️ Пропускаю пару {}. Z-скор {} < Z-скор Min {}", tradingPair.getPairName(), zScore, settings.getMinZ());
            }
            return true;
        }

        return false;
    }

    public boolean validateBalance(TradingPair tradingPair, Settings settings) {
        if (!tradingIntegrationServiceImpl.canOpenNewPair(settings)) {
            log.warn("⚠️ Недостаточно средств в торговом депо для открытия пары {}", tradingPair.getPairName());
            return false;
        }
        return true;
    }
}
