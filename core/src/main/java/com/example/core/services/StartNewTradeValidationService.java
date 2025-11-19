package com.example.core.services;

import com.example.core.trading.services.TradingIntegrationService;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.models.Settings;
import com.example.shared.models.Pair;
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

    public boolean validateTickers(Pair tradingPair, ZScoreData zScoreData) {
        return Objects.equals(tradingPair.getLongTicker(), zScoreData.getUnderValuedTicker()) &&
                Objects.equals(tradingPair.getShortTicker(), zScoreData.getOverValuedTicker());
    }

    public boolean validateAutoTrading(Pair pair, boolean checkAutoTrading) {
        if (!checkAutoTrading) {
            log.debug("🔧 Ручной запуск трейда - проверка автотрейдинга пропущена для пары {}", pair.getPairName());
            return true;
        }

        Settings currentSettings = settingsService.getSettings(); //снова читаем из бд
        log.debug("📖 Процессор: Читаем настройки из БД: autoTrading={}", currentSettings.isAutoTradingEnabled());

        if (!currentSettings.isAutoTradingEnabled()) {
            log.warn("⚠️ Автотрейдинг отключен! Пропускаю открытие нового трейда для пары {}", pair.getPairName());
            return false;
        }

        log.debug("✅ Процессор: Автотрейдинг включен, продолжаем");
        return true;
    }

    public boolean isLastZLessThenMinZ(Pair pair, Settings settings) {
        if (pair == null) {
            throw new IllegalArgumentException("pairData is null");
        }

        double zScore = pair.getZScoreCurrent() != null ? pair.getZScoreCurrent().doubleValue() : 0.0;
        if (zScore < settings.getMinZ()) {
            if (zScore < 0) {
                log.warn("⚠️ Пропускаю пару {}. Z-скор {} < 0", pair.getPairName(), zScore);
            } else {
                log.warn("⚠️ Пропускаю пару {}. Z-скор {} < Z-скор Min {}", pair.getPairName(), zScore, settings.getMinZ());
            }
            return true;
        }

        return false;
    }

    public boolean validateBalance(Pair pair, Settings settings) {
        if (!tradingIntegrationServiceImpl.canOpenNewPair(settings)) {
            log.warn("⚠️ Недостаточно средств в торговом депо для открытия пары {}", pair.getPairName());
            return false;
        }
        return true;
    }

    public boolean validateZScoreDeclineFilter(ZScoreData zScoreData, Settings settings) {
        // Если фильтр отключен, пропускаем проверку
        if (!settings.isUseZScoreDeclineFilter()) {
            log.debug("✅ Фильтр снижения zScore отключен");
            return true;
        }

        // Получаем количество свечей для проверки
        int candlesCount = settings.getZScoreDeclineCandlesCount();
        
        // Получаем исторические данные zScore
        var zScoreHistory = zScoreData.getZScoreHistory();
        if (zScoreHistory == null || zScoreHistory.isEmpty()) {
            log.warn("⚠️ Нет данных истории zScore для проверки фильтра снижения");
            return false;
        }

        // Проверяем, что у нас достаточно точек для анализа
        if (zScoreHistory.size() < candlesCount) {
            log.warn("⚠️ Недостаточно данных zScore для проверки фильтра снижения. Требуется {}, доступно {}", 
                    candlesCount, zScoreHistory.size());
            return false;
        }

        // Берём последние N точек zScore (самые свежие в конце списка)
        var recentZScores = zScoreHistory.subList(zScoreHistory.size() - candlesCount, zScoreHistory.size());
        
        // Создаём строку с последовательностью zScore для логирования
        StringBuilder zScoreSequence = new StringBuilder();
        for (int i = 0; i < recentZScores.size(); i++) {
            double zScore = recentZScores.get(i).getZscore();
            zScoreSequence.append(String.format("%.2f", zScore));
            if (i < recentZScores.size() - 1) {
                zScoreSequence.append("->");
            }
        }
        
        log.info("📊 Проверка фильтра снижения zScore для {} точек. Последовательность: {}", 
                candlesCount, zScoreSequence);
        
        // Проверяем тенденцию снижения
        boolean isDecreasing = true;
        for (int i = 1; i < recentZScores.size(); i++) {
            double previousZScore = recentZScores.get(i - 1).getZscore();
            double currentZScore = recentZScores.get(i).getZscore();
            
            if (currentZScore >= previousZScore) {
                isDecreasing = false;
                log.debug("❌ Снижение прервано на позиции {}: {} >= {}", 
                        i, String.format("%.2f", currentZScore), String.format("%.2f", previousZScore));
                break;
            } else {
                log.debug("✅ Снижение на позиции {}: {} -> {}", 
                        i, String.format("%.2f", previousZScore), String.format("%.2f", currentZScore));
            }
        }

        if (!isDecreasing) {
            log.warn("⚠️ Фильтр снижения zScore: тенденция снижения НЕ обнаружена. Последовательность: {}", 
                    zScoreSequence);
            return false;
        }

        log.info("✅ Фильтр снижения zScore: обнаружена тенденция снижения! Последовательность: {}", 
                zScoreSequence);
        return true;
    }
}
