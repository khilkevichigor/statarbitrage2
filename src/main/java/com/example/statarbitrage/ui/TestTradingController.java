package com.example.statarbitrage.ui;

import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.providers.RealOkxTradingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Контроллер для быстрого тестирования торговых позиций
 */
@Slf4j
@RestController
@RequestMapping("/api/test-trading")
@RequiredArgsConstructor
public class TestTradingController {

    private final RealOkxTradingProvider realOkxTradingProvider;

    /**
     * Тестовое открытие LONG позиции XRP на $0.5 с плечом x1
     * GET /api/test-trading/xrp-long
     */
    @GetMapping("/xrp-long")
    public String testXrpLong() {
        log.info("🧪 ТЕСТ: Открытие LONG позиции XRP-USDT-SWAP на $0.5 с плечом x1");

        try {
            String symbol = "XRP-USDT-SWAP";
            BigDecimal amount = BigDecimal.valueOf(0.5); // $0.5
            BigDecimal leverage = BigDecimal.valueOf(1.0); // x1

            TradeResult result = realOkxTradingProvider.openLongPosition(symbol, amount, leverage);

            if (result.isSuccess()) {
                log.info("✅ ТЕСТ УСПЕШЕН: LONG позиция открыта - ID: {}, Размер: {}, Цена: {}, Комиссия: {}",
                        result.getPositionId(), result.getExecutedSize(), result.getExecutionPrice(), result.getFees());
                return String.format("✅ SUCCESS: LONG XRP открыт | ID: %s | Размер: %s | Цена: %s | Комиссия: %s",
                        result.getPositionId(), result.getExecutedSize(), result.getExecutionPrice(), result.getFees());
            } else {
                log.error("❌ ТЕСТ ПРОВАЛЕН: {}", result.getErrorMessage());
                return "❌ FAILED: " + result.getErrorMessage();
            }

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА в тесте: {}", e.getMessage(), e);
            return "❌ CRITICAL ERROR: " + e.getMessage();
        }
    }

    /**
     * Тестовое открытие SHORT позиции XRP на $0.5 с плечом x1
     * GET /api/test-trading/xrp-short
     */
    @GetMapping("/xrp-short")
    public String testXrpShort() {
        log.info("🧪 ТЕСТ: Открытие SHORT позиции XRP-USDT-SWAP на $0.5 с плечом x1");

        try {
            String symbol = "XRP-USDT-SWAP";
            BigDecimal amount = BigDecimal.valueOf(0.5); // $0.5
            BigDecimal leverage = BigDecimal.valueOf(1.0); // x1

            TradeResult result = realOkxTradingProvider.openShortPosition(symbol, amount, leverage);

            if (result.isSuccess()) {
                log.info("✅ ТЕСТ УСПЕШЕН: SHORT позиция открыта - ID: {}, Размер: {}, Цена: {}, Комиссия: {}",
                        result.getPositionId(), result.getExecutedSize(), result.getExecutionPrice(), result.getFees());
                return String.format("✅ SUCCESS: SHORT XRP открыт | ID: %s | Размер: %s | Цена: %s | Комиссия: %s",
                        result.getPositionId(), result.getExecutedSize(), result.getExecutionPrice(), result.getFees());
            } else {
                log.error("❌ ТЕСТ ПРОВАЛЕН: {}", result.getErrorMessage());
                return "❌ FAILED: " + result.getErrorMessage();
            }

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА в тесте: {}", e.getMessage(), e);
            return "❌ CRITICAL ERROR: " + e.getMessage();
        }
    }

    /**
     * Параметризированный тест - можно менять сумму и плечо через URL параметры
     * GET /api/test-trading/xrp-custom?amount=2&leverage=5&direction=long
     */
    @GetMapping("/xrp-custom")
    public String testXrpCustom(
            @RequestParam(defaultValue = "0.5") double amount,
            @RequestParam(defaultValue = "1") double leverage,
            @RequestParam(defaultValue = "long") String direction) {

        log.info("🧪 ТЕСТ: Открытие {} позиции XRP-USDT-SWAP на ${} с плечом x{}",
                direction.toUpperCase(), amount, leverage);

        try {
            String symbol = "XRP-USDT-SWAP";
            BigDecimal amountBd = BigDecimal.valueOf(amount);
            BigDecimal leverageBd = BigDecimal.valueOf(leverage);

            TradeResult result;
            if ("short".equalsIgnoreCase(direction)) {
                result = realOkxTradingProvider.openShortPosition(symbol, amountBd, leverageBd);
            } else {
                result = realOkxTradingProvider.openLongPosition(symbol, amountBd, leverageBd);
            }

            if (result.isSuccess()) {
                log.info("✅ ТЕСТ УСПЕШЕН: {} позиция открыта - ID: {}, Размер: {}, Цена: {}, Комиссия: {}",
                        direction.toUpperCase(), result.getPositionId(), result.getExecutedSize(), result.getExecutionPrice(), result.getFees());
                return String.format("✅ SUCCESS: %s XRP (${}, x%s) | ID: %s | Размер: %s | Цена: %s | Комиссия: %s",
                        direction.toUpperCase(), amount, leverage, result.getPositionId(), result.getExecutedSize(), result.getExecutionPrice(), result.getFees());
            } else {
                log.error("❌ ТЕСТ ПРОВАЛЕН: {}", result.getErrorMessage());
                return "❌ FAILED: " + result.getErrorMessage();
            }

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА в тесте: {}", e.getMessage(), e);
            return "❌ CRITICAL ERROR: " + e.getMessage();
        }
    }

    /**
     * Закрытие всех открытых XRP позиций (для очистки после тестов)
     * GET /api/test-trading/close-all-xrp
     */
    @GetMapping("/close-all-xrp")
    public String closeAllXrpPositions() {
        log.info("🧪 ТЕСТ: Закрытие всех XRP позиций");

        try {
            // Получаем все активные позиции и закрываем XRP
            var activePositions = realOkxTradingProvider.getActivePositions();
            int closedCount = 0;

            for (var position : activePositions) {
                if (position.getSymbol().contains("XRP")) {
                    TradeResult closeResult = realOkxTradingProvider.closePosition(position.getPositionId());
                    if (closeResult.isSuccess()) {
                        closedCount++;
                        log.info("✅ Закрыта XRP позиция: {}", position.getPositionId());
                    } else {
                        log.warn("⚠️ Не удалось закрыть XRP позицию {}: {}", position.getPositionId(), closeResult.getErrorMessage());
                    }
                }
            }

            return String.format("✅ Закрыто %d XRP позиций", closedCount);

        } catch (Exception e) {
            log.error("❌ ОШИБКА при закрытии XRP позиций: {}", e.getMessage(), e);
            return "❌ ERROR: " + e.getMessage();
        }
    }
}