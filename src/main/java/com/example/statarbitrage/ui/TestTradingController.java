package com.example.statarbitrage.ui;

import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.providers.RealOkxTradingProvider;
import com.example.statarbitrage.trading.services.GeolocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер для быстрого тестирования торговых позиций
 */
@Slf4j
@RestController
@RequestMapping("/api/test-trading")
@RequiredArgsConstructor
public class TestTradingController {

    private final RealOkxTradingProvider realOkxTradingProvider;
    private final GeolocationService geolocationService;

    /**
     * Тестовое открытие LONG позиции XRP на $0.5 с плечом x1
     * GET /api/test-trading/xrp-long
     */
    @GetMapping("/xrp-long")
    public String testXrpLong() {
        log.info("🧪 ТЕСТ: Открытие LONG позиции XRP-USDT-SWAP на $0.5 с плечом x1");

        // ЗАЩИТА: Проверяем геолокацию перед вызовом OKX API
        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return "❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!";
        }

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

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return "❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!";
        }

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

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return "❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!";
        }

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

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return "❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!";
        }

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

    /**
     * Получение списка всех активных позиций (открытые позиции, реализованный PnL есть)
     * GET /api/test-trading/positions
     */
    @GetMapping("/positions")
    public List<Position> getAllActivePositions() {
        log.info("Получение списка всех активных позиций");

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return Collections.emptyList();
        }

        return realOkxTradingProvider.getActivePositions();
    }

    /**
     * Получение информации о конкретной позиции по ID
     * GET /api/test-trading/position?id=12345
     */
    @GetMapping("/position")
    public Position getPositionById(@RequestParam String id) {
        log.info("Получение информации о позиции с ID: {}", id);

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return null;
        }

        return realOkxTradingProvider.getPosition(id);
    }

    /**
     * Получение позиций по символу (например, XRP-USDT-SWAP)
     * GET /api/test-trading/positions-by-symbol?symbol=XRP-USDT-SWAP
     */
    @GetMapping("/positions-by-symbol")
    public List<Position> getPositionsBySymbol(@RequestParam String symbol) {
        log.info("Получение позиций по символу: {}", symbol);

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return Collections.emptyList();
        }

        return realOkxTradingProvider.getActivePositions().stream()
                .filter(p -> p.getSymbol().equalsIgnoreCase(symbol))
                .collect(Collectors.toList());
    }

    /**
     * Получение детализированной информации о позиции
     * GET /api/test-trading/position-details?id=12345
     */
    @GetMapping("/position-details")
    public String getPositionDetails(@RequestParam String id) {
        log.info("Получение детализированной информации о позиции с ID: {}", id);

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return "❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!";
        }

        Position position = realOkxTradingProvider.getPosition(id);

        if (position == null) {
            return "Позиция с ID " + id + " не найдена";
        }

        return String.format(
                "📊 Детали позиции %s (%s):\n" +
                        "🔹 Статус: %s\n" +
                        "🔹 Направление: %s\n" +
                        "🔹 Размер: %s\n" +
                        "🔹 Цена входа: %s USDT\n" +
                        "🔹 Текущая цена: %s USDT\n" +
                        "🔹 Нереализованный PnL: %s USDT (%s %%)\n" +
                        "🔹 Реализованный PnL: %s USDT\n" +
                        "🔹 Плечо: %sx\n" +
                        "🔹 Маржа: %s USDT\n" +
                        "🔹 Время открытия: %s\n" +
                        "🔹 Последнее обновление: %s",
                position.getSymbol(),
                position.getPositionId(),
                position.getStatus(),
                position.getType(),
                position.getSize(),
                position.getEntryPrice(),
                position.getCurrentPrice(),
                position.getUnrealizedPnLUSDT(),
                position.getUnrealizedPnLPercent(),
                position.getRealizedPnLUSDT(),
                position.getLeverage(),
                position.getAllocatedAmount(),
                position.getOpenTime(),
                position.getLastUpdated()
        );
    }

    /**
     * Принудительная синхронизация позиций с OKX
     * GET /api/test-trading/sync-positions
     */
    @GetMapping("/sync-positions")
    public String syncPositionsWithOkx() {
        log.info("Запуск принудительной синхронизации позиций с OKX");

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return "❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!";
        }

        try {
            realOkxTradingProvider.updatePositionPrices();
            return "Синхронизация позиций с OKX выполнена успешно";
        } catch (Exception e) {
            log.error("Ошибка при синхронизации позиций: {}", e.getMessage(), e);
            return "Ошибка при синхронизации: " + e.getMessage();
        }
    }

    /**
     * Получение сводной информации по всем позициям
     * GET /api/test-trading/positions-summary
     */
    @GetMapping("/positions-summary")
    public String getPositionsSummary() {
        log.info("Получение сводной информации по всем позициям");

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
            return "❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!";
        }

        List<Position> positions = realOkxTradingProvider.getActivePositions();

        if (positions.isEmpty()) {
            return "Нет активных позиций";
        }

        BigDecimal totalUnrealizedPnl = positions.stream()
                .map(Position::getUnrealizedPnLUSDT)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRealizedPnl = positions.stream()
                .map(Position::getRealizedPnLUSDT)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return String.format(
                "📊 Сводка по позициям:\n" +
                        "🔹 Всего позиций: %d\n" +
                        "🔹 Общий нереализованный PnL: %s USDT\n" +
                        "🔹 Общий реализованный PnL: %s USDT\n" +
                        "🔹 Детали позиций:\n%s",
                positions.size(),
                totalUnrealizedPnl,
                totalRealizedPnl,
                positions.stream()
                        .map(p -> String.format("   - %s (%s): PnL=%s USDT (%s %%)",
                                p.getSymbol(),
                                p.getType(),
                                p.getUnrealizedPnLUSDT(),
                                p.getUnrealizedPnLPercent()))
                        .collect(Collectors.joining("\n"))
        );
    }
}