package com.example.core.trading.services;

import com.example.core.services.SettingsService;
import com.example.core.trading.interfaces.TradingProvider;
import com.example.shared.models.Settings;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateMinimumLotRequirementsService {

    private final SettingsService settingsService;

    /**
     * Проверка пары на соответствие требованиям минимального лота.
     * Возвращает false, если минимальный лот для любой позиции превышает желаемую сумму более чем в 3 раза.
     */
    public boolean validate(TradingProvider provider, TradingPair tradingPair, BigDecimal longAmount, BigDecimal shortAmount) {
        // Проверка блэклиста ДО основной валидации
        for (String ticker : List.of(tradingPair.getLongTicker(), tradingPair.getShortTicker())) {
            if (isInBlacklist(ticker)) {
                log.warn("❌ БЛОКИРОВКА: Пара {} заблокирована из-за тикера {} в блэклисте минимальных лотов",
                        tradingPair.getPairName(), ticker);
                return false;
            }
        }
        try {
            BigDecimal longPrice = provider.getCurrentPrice(tradingPair.getLongTicker());
            BigDecimal shortPrice = provider.getCurrentPrice(tradingPair.getShortTicker());

            if (isInvalidPrice(longPrice) || isInvalidPrice(shortPrice)) {
                log.warn("⚠️ Не удалось получить корректные цены для проверки минимальных лотов для пары {}", tradingPair.getPairName());
                return true; // Разрешаем торговлю при отсутствии корректных данных о ценах
            }

            if (!validatePositionForMinimumLot(tradingPair.getLongTicker(), longAmount, longPrice)) {
                return false;
            }

            if (!validatePositionForMinimumLot(tradingPair.getShortTicker(), shortAmount, shortPrice)) {
                return false;
            }

            log.debug("✅ Пара {} прошла проверку минимальных лотов", tradingPair.getPairName());
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке минимальных лотов для {}: {}", tradingPair.getPairName(), e.getMessage(), e);
            return true; // Разрешаем торговлю при ошибке проверки
        }
    }

    private boolean isInvalidPrice(BigDecimal price) {
        return price == null || price.compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Проверка конкретной позиции на соответствие требованиям минимального лота.
     */
    private boolean validatePositionForMinimumLot(String symbol, BigDecimal desiredAmount, BigDecimal currentPrice) {
        try {
            BigDecimal desiredSize = desiredAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);

            // Минимальный лот округляем вниз до целого, минимум 1
            BigDecimal adjustedSize = desiredSize.setScale(0, RoundingMode.DOWN);
            if (adjustedSize.compareTo(BigDecimal.ONE) < 0) {
                adjustedSize = BigDecimal.ONE;
            }

            BigDecimal adjustedAmount = adjustedSize.multiply(currentPrice);
            BigDecimal excessRatio = adjustedAmount.divide(desiredAmount, 4, RoundingMode.HALF_UP);

            if (excessRatio.compareTo(BigDecimal.valueOf(3)) > 0) {
                log.warn("❌ БЛОКИРОВКА: {} минимальный лот требует сумму {} вместо желаемой {} (превышение в {} раз)",
                        symbol, adjustedAmount, desiredAmount, excessRatio);

                // Автоматически добавляем в блэклист при превышении лимита
                addToBlacklist(symbol);

                return false;
            }

            log.debug("✅ {} прошел проверку: желаемая сумма = {}, итоговая сумма = {}, соотношение = {}",
                    symbol, desiredAmount, adjustedAmount, excessRatio);
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке минимального лота для {}: {}", symbol, e.getMessage(), e);
            return true;
        }
    }

    /**
     * Проверяет, находится ли тикер в блэклисте минимальных лотов
     */
    private boolean isInBlacklist(String ticker) {
        try {
            Settings settings = settingsService.getSettings();
            String blacklist = settings.getMinimumLotBlacklist();

            if (blacklist == null || blacklist.trim().isEmpty()) {
                return false;
            }

            return Arrays.stream(blacklist.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .anyMatch(ticker.toUpperCase()::equals);

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке блэклиста для тикера {}: {}", ticker, e.getMessage(), e);
            return false; // При ошибке не блокируем торговлю
        }
    }

    /**
     * Автоматически добавляет тикер в блэклист минимальных лотов
     */
    private void addToBlacklist(String ticker) {
        try {
            Settings settings = settingsService.getSettings();
            String currentBlacklist = settings.getMinimumLotBlacklist();

            // Проверяем, не находится ли уже в блэклисте
            if (isInBlacklist(ticker)) {
                return;
            }

            String newBlacklist;
            if (currentBlacklist == null || currentBlacklist.trim().isEmpty()) {
                newBlacklist = ticker.toUpperCase();
            } else {
                newBlacklist = currentBlacklist + "," + ticker.toUpperCase();
            }

            settings.setMinimumLotBlacklist(newBlacklist);
            settingsService.save(settings); //todo не сохраняет!

            log.warn("🚫 АВТОБЛОКИРОВКА: Тикер {} автоматически добавлен в блэклист минимальных лотов", ticker);

        } catch (Exception e) {
            log.error("❌ Ошибка при добавлении тикера {} в блэклист: {}", ticker, e.getMessage(), e);
        }
    }
}