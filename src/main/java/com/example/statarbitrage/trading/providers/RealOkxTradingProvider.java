package com.example.statarbitrage.trading.providers;

import com.example.statarbitrage.client_okx.OkxClient;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.*;
import com.example.statarbitrage.trading.services.OkxPortfolioManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Реальная торговля через OKX API
 * Выполняет настоящие торговые операции через OKX биржу
 * ВСЕ МЕТОДЫ ПОЛНОСТЬЮ СИНХРОННЫЕ!
 */
@Slf4j
@Service
public class RealOkxTradingProvider implements TradingProvider {

    private final OkxPortfolioManager okxPortfolioManager;
    private final OkxClient okxClient;

    // OKX API конфигурация
    @Value("${okx.api.key:}")
    private String apiKey;

    @Value("${okx.api.secret:}")
    private String apiSecret;

    @Value("${okx.api.passphrase:}")
    private String passphrase;

    @Value("${okx.api.sandbox:true}")
    private boolean isSandbox;

    // HTTP клиент для OKX API
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    // Хранилище позиций для синхронизации с OKX
    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();
    private final List<TradeResult> tradeHistory = new ArrayList<>();

    // Кэш информации о торговых инструментах
    private final ConcurrentHashMap<String, InstrumentInfo> instrumentInfoCache = new ConcurrentHashMap<>();

    // Константы OKX API
    private static final String PROD_BASE_URL = "https://www.okx.com";
    private static final String SANDBOX_BASE_URL = "https://www.okx.com";
    private static final String TRADE_ORDER_ENDPOINT = "/api/v5/trade/order";
    private static final String TRADE_POSITIONS_ENDPOINT = "/api/v5/account/positions";
    private static final String ACCOUNT_BALANCE_ENDPOINT = "/api/v5/account/balance";
    private static final String MARKET_TICKER_ENDPOINT = "/api/v5/market/ticker";
    private static final String PUBLIC_INSTRUMENTS_ENDPOINT = "/api/v5/public/instruments";

    public RealOkxTradingProvider(OkxPortfolioManager okxPortfolioManager, OkxClient okxClient) {
        this.okxPortfolioManager = okxPortfolioManager;
        this.okxClient = okxClient;
    }

    @Override
    public Portfolio getPortfolio() {
        try {
            // Получаем реальный баланс с OKX через OkxPortfolioManager
            return okxPortfolioManager.getCurrentPortfolio();
        } catch (Exception e) {
            log.error("Ошибка при получении портфолио: {}", e.getMessage());
            return okxPortfolioManager.getCurrentPortfolio();
        }
    }

    @Override
    public boolean hasAvailableBalance(BigDecimal amount) {
        try {
            return okxPortfolioManager.hasAvailableBalance(amount);
        } catch (Exception e) {
            log.error("Ошибка при проверке баланса: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public TradeResult openLongPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        try {
            // Проверяем подключение к API
            if (!isConnected()) {
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                        "Нет подключения к OKX API");
            }

            // Проверяем доступность средств
            if (!hasAvailableBalance(amount)) {
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                        "Недостаточно средств для открытия позиции");
            }

            // Получаем текущую цену
            BigDecimal currentPrice = getCurrentPrice(symbol);
            if (currentPrice == null) {
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                        "Не удалось получить текущую цену");
            }

            // Рассчитываем размер позиции
            BigDecimal positionSize = amount.multiply(leverage)
                    .divide(currentPrice, 8, RoundingMode.HALF_UP);

            // Корректируем размер позиции согласно lot size
            positionSize = adjustPositionSizeToLotSize(symbol, positionSize);
            if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                        "Размер позиции слишком мал для торговли");
            }

            // Создаем заявку на OKX
            String orderId = placeOrder(symbol, "buy", "long", positionSize.toString(),
                    currentPrice.toString(), leverage.toString());

            if (orderId == null) {
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                        "Не удалось создать заявку на OKX");
            }

            // Резервируем средства
            if (!okxPortfolioManager.reserveBalance(amount)) {
                // Пытаемся отменить заявку
                cancelOrder(orderId, symbol);
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol,
                        "Не удалось зарезервировать средства");
            }

            // Рассчитываем комиссии
            BigDecimal fees = calculateFees(amount, leverage);

            // Создаем позицию
            String positionId = UUID.randomUUID().toString();
            Position position = Position.builder()
                    .positionId(positionId)
                    .symbol(symbol)
                    .type(PositionType.LONG)
                    .size(positionSize)
                    .entryPrice(currentPrice)
                    .currentPrice(currentPrice)
                    .leverage(leverage)
                    .allocatedAmount(amount)
                    .unrealizedPnL(BigDecimal.ZERO)
                    .unrealizedPnLPercent(BigDecimal.ZERO)
                    .openingFees(fees)
                    .status(PositionStatus.OPEN)
                    .openTime(LocalDateTime.now())
                    .lastUpdated(LocalDateTime.now())
                    .externalOrderId(orderId) // Сохраняем ID заявки OKX
                    .build();

            // Сохраняем позицию
            positions.put(positionId, position);

            // Уведомляем портфолио
            okxPortfolioManager.onPositionOpened(position);

            // Создаем результат
            TradeResult result = TradeResult.success(positionId, TradeOperationType.OPEN_LONG,
                    symbol, positionSize, currentPrice, fees);
            result.setPnl(BigDecimal.ZERO);
            result.setExternalOrderId(orderId);

            tradeHistory.add(result);

            log.info("🟢 Открыта LONG позиция на OKX: {} | Размер: {} | Цена: {} | OrderID: {}",
                    symbol, positionSize, currentPrice, orderId);

            return result;

        } catch (Exception e) {
            log.error("Ошибка при открытии LONG позиции {}: {}", symbol, e.getMessage());
            return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, e.getMessage());
        }
    }

    @Override
    public TradeResult openShortPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        try {
            // Проверяем подключение к API
            if (!isConnected()) {
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                        "Нет подключения к OKX API");
            }

            // Проверяем доступность средств
            if (!hasAvailableBalance(amount)) {
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                        "Недостаточно средств для открытия позиции");
            }

            // Получаем текущую цену
            BigDecimal currentPrice = getCurrentPrice(symbol);
            if (currentPrice == null) {
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                        "Не удалось получить текущую цену");
            }

            // Рассчитываем размер позиции
            BigDecimal positionSize = amount.multiply(leverage)
                    .divide(currentPrice, 8, RoundingMode.HALF_UP);

            // Корректируем размер позиции согласно lot size
            positionSize = adjustPositionSizeToLotSize(symbol, positionSize);
            if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                        "Размер позиции слишком мал для торговли");
            }

            // Создаем заявку на OKX
            String orderId = placeOrder(symbol, "sell", "short", positionSize.toString(),
                    currentPrice.toString(), leverage.toString());

            if (orderId == null) {
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                        "Не удалось создать заявку на OKX");
            }

            // Резервируем средства
            if (!okxPortfolioManager.reserveBalance(amount)) {
                // Пытаемся отменить заявку
                cancelOrder(orderId, symbol);
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol,
                        "Не удалось зарезервировать средства");
            }

            // Рассчитываем комиссии
            BigDecimal fees = calculateFees(amount, leverage);

            // Создаем позицию
            String positionId = UUID.randomUUID().toString();
            Position position = Position.builder()
                    .positionId(positionId)
                    .symbol(symbol)
                    .type(PositionType.SHORT)
                    .size(positionSize)
                    .entryPrice(currentPrice)
                    .currentPrice(currentPrice)
                    .leverage(leverage)
                    .allocatedAmount(amount)
                    .unrealizedPnL(BigDecimal.ZERO)
                    .unrealizedPnLPercent(BigDecimal.ZERO)
                    .openingFees(fees)
                    .status(PositionStatus.OPEN)
                    .openTime(LocalDateTime.now())
                    .lastUpdated(LocalDateTime.now())
                    .externalOrderId(orderId) // Сохраняем ID заявки OKX
                    .build();

            // Сохраняем позицию
            positions.put(positionId, position);

            // Уведомляем портфолио
            okxPortfolioManager.onPositionOpened(position);

            // Создаем результат
            TradeResult result = TradeResult.success(positionId, TradeOperationType.OPEN_SHORT,
                    symbol, positionSize, currentPrice, fees);
            result.setPnl(BigDecimal.ZERO);
            result.setExternalOrderId(orderId);

            tradeHistory.add(result);

            log.info("🔴 Открыта SHORT позиция на OKX: {} | Размер: {} | Цена: {} | OrderID: {}",
                    symbol, positionSize, currentPrice, orderId);

            return result;

        } catch (Exception e) {
            log.error("Ошибка при открытии SHORT позиции {}: {}", symbol, e.getMessage());
            return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, e.getMessage());
        }
    }

    @Override
    public TradeResult closePosition(String positionId) {
        try {
            Position position = positions.get(positionId);
            if (position == null) {
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, "UNKNOWN",
                        "Позиция не найдена: " + positionId);
            }

            if (position.getStatus() != PositionStatus.OPEN) {
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(),
                        "Позиция не открыта: " + position.getStatus());
            }

            // Получаем текущую цену
            BigDecimal currentPrice = getCurrentPrice(position.getSymbol());
            if (currentPrice == null) {
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(),
                        "Не удалось получить текущую цену");
            }

            // Создаем заявку на закрытие позиции
            String closeOrderId = placeCloseOrder(position.getSymbol(),
                    position.getType() == PositionType.LONG ? "sell" : "buy",
                    position.getSize().toString(), currentPrice.toString());

            if (closeOrderId == null) {
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(),
                        "Не удалось создать заявку на закрытие");
            }

            // Обновляем цену и рассчитываем PnL
            position.setCurrentPrice(currentPrice);
            position.calculateUnrealizedPnL();

            // Рассчитываем комиссии за закрытие
            BigDecimal closingFees = calculateFees(position.getAllocatedAmount(), position.getLeverage());
            BigDecimal totalFees = position.getOpeningFees().add(closingFees);

            // Финальный PnL с учетом комиссий
            BigDecimal finalPnL = position.getUnrealizedPnL().subtract(closingFees);

            // Закрываем позицию
            position.setStatus(PositionStatus.CLOSED);
            position.setLastUpdated(LocalDateTime.now());

            // Освобождаем средства и уведомляем портфолио
            okxPortfolioManager.releaseReservedBalance(position.getAllocatedAmount());
            okxPortfolioManager.onPositionClosed(position, finalPnL, totalFees);

            // Удаляем из активных позиций
            positions.remove(positionId);

            // Создаем результат
            TradeResult result = TradeResult.success(positionId, TradeOperationType.CLOSE_POSITION,
                    position.getSymbol(), position.getSize(), currentPrice, closingFees);
            result.setPnl(finalPnL);
            result.setExternalOrderId(closeOrderId);

            tradeHistory.add(result);

            log.info("⚫ Закрыта позиция на OKX: {} {} | Цена: {} | PnL: {} | OrderID: {}",
                    position.getSymbol(), position.getDirectionString(), currentPrice, finalPnL, closeOrderId);

            return result;

        } catch (Exception e) {
            log.error("Ошибка при закрытии позиции {}: {}", positionId, e.getMessage());
            return TradeResult.failure(TradeOperationType.CLOSE_POSITION, "UNKNOWN", e.getMessage());
        }
    }

    @Override
    public List<Position> getActivePositions() {
        try {
            // Синхронизируем позиции с OKX
            syncPositionsWithOkx();
            return new ArrayList<>(positions.values());
        } catch (Exception e) {
            log.error("Ошибка при получении активных позиций: {}", e.getMessage());
            return new ArrayList<>(positions.values());
        }
    }

    @Override
    public Position getPosition(String positionId) {
        return positions.get(positionId);
    }

    @Override
    public void updatePositionPrices() {
        try {
            for (Position position : positions.values()) {
                try {
                    BigDecimal currentPrice = getCurrentPrice(position.getSymbol());
                    if (currentPrice != null) {
                        position.setCurrentPrice(currentPrice);
                        position.calculateUnrealizedPnL();
                        position.setLastUpdated(LocalDateTime.now());
                    }
                } catch (Exception e) {
                    log.warn("Не удалось обновить цену для позиции {}: {}",
                            position.getPositionId(), e.getMessage());
                }
            }

            // Обновляем портфолио
            okxPortfolioManager.updatePortfolioValue();

        } catch (Exception e) {
            log.error("Ошибка при обновлении цен позиций: {}", e.getMessage());
        }
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        try {
            // Используем существующий OkxClient для получения цены
            JsonArray ticker = okxClient.getTicker(symbol);
            if (ticker != null && ticker.size() > 0) {
                JsonObject tickerData = ticker.get(0).getAsJsonObject();
                String lastPrice = tickerData.get("last").getAsString();
                return new BigDecimal(lastPrice);
            }
            return null;
        } catch (Exception e) {
            log.error("Ошибка при получении цены для {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    @Override
    public BigDecimal calculateFees(BigDecimal amount, BigDecimal leverage) {
        // OKX комиссия для фьючерсов: 0.02% maker, 0.05% taker
        // Используем taker комиссию для рыночных ордеров
        BigDecimal feeRate = BigDecimal.valueOf(0.0005); // 0.05%
        return amount.multiply(leverage).multiply(feeRate);
    }

    @Override
    public TradingProviderType getProviderType() {
        return TradingProviderType.REAL_OKX;
    }

    @Override
    public boolean isConnected() {
        try {
            // Проверяем наличие API ключей
            if (apiKey == null || apiKey.isEmpty() ||
                    apiSecret == null || apiSecret.isEmpty() ||
                    passphrase == null || passphrase.isEmpty()) {
                log.warn("OKX API ключи не настроены");
                return false;
            }

            // Проверяем подключение запросом к API
            return checkApiConnection();
        } catch (Exception e) {
            log.error("Ошибка при проверке подключения к OKX: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<TradeResult> getTradeHistory(int limit) {
        return tradeHistory.stream()
                .sorted((a, b) -> b.getExecutionTime().compareTo(a.getExecutionTime()))
                .limit(limit)
                .toList();
    }

    // Приватные методы для работы с OKX API

    private String placeOrder(String symbol, String side, String posSide, String size,
                              String price, String leverage) {
        try {
            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_ORDER_ENDPOINT;

            JsonObject orderData = new JsonObject();
            orderData.addProperty("instId", symbol);
            orderData.addProperty("tdMode", "isolated"); // Изолированная маржа
            orderData.addProperty("side", side);
            orderData.addProperty("posSide", posSide);
            orderData.addProperty("ordType", "market"); // Рыночный ордер
            orderData.addProperty("sz", size);
            orderData.addProperty("lever", leverage);

            RequestBody body = RequestBody.create(
                    orderData.toString(),
                    MediaType.get("application/json")
            );

            String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            String signature = generateSignature("POST", endpoint, orderData.toString(), timestamp);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .post(body)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if ("0".equals(jsonResponse.get("code").getAsString())) {
                    JsonArray data = jsonResponse.getAsJsonArray("data");
                    if (data.size() > 0) {
                        return data.get(0).getAsJsonObject().get("ordId").getAsString();
                    }
                }

                log.error("Ошибка при создании ордера: {}", responseBody);
                return null;
            }
        } catch (Exception e) {
            log.error("Ошибка при создании ордера: {}", e.getMessage());
            return null;
        }
    }

    private String placeCloseOrder(String symbol, String side, String size, String price) {
        try {
            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_ORDER_ENDPOINT;

            JsonObject orderData = new JsonObject();
            orderData.addProperty("instId", symbol);
            orderData.addProperty("tdMode", "isolated");
            orderData.addProperty("side", side);
            orderData.addProperty("posSide", "net"); // Закрытие позиции
            orderData.addProperty("ordType", "market");
            orderData.addProperty("sz", size);

            RequestBody body = RequestBody.create(
                    orderData.toString(),
                    MediaType.get("application/json")
            );

            String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            String signature = generateSignature("POST", endpoint, orderData.toString(), timestamp);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .post(body)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if ("0".equals(jsonResponse.get("code").getAsString())) {
                    JsonArray data = jsonResponse.getAsJsonArray("data");
                    if (data.size() > 0) {
                        return data.get(0).getAsJsonObject().get("ordId").getAsString();
                    }
                }

                log.error("Ошибка при закрытии ордера: {}", responseBody);
                return null;
            }
        } catch (Exception e) {
            log.error("Ошибка при закрытии ордера: {}", e.getMessage());
            return null;
        }
    }

    private void cancelOrder(String orderId, String symbol) {
        try {
            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = "/api/v5/trade/cancel-order";

            JsonObject cancelData = new JsonObject();
            cancelData.addProperty("instId", symbol);
            cancelData.addProperty("ordId", orderId);

            RequestBody body = RequestBody.create(
                    cancelData.toString(),
                    MediaType.get("application/json")
            );

            String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            String signature = generateSignature("POST", endpoint, cancelData.toString(), timestamp);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .post(body)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                log.info("Отмена ордера {}: {}", orderId, responseBody);
            }
        } catch (Exception e) {
            log.error("Ошибка при отмене ордера {}: {}", orderId, e.getMessage());
        }
    }

    private boolean checkApiConnection() {
        try {
            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = ACCOUNT_BALANCE_ENDPOINT;

            String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            String signature = generateSignature("GET", endpoint, "", timestamp);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                log.info("OKX API запрос: {} {}", baseUrl + endpoint, timestamp);
                log.info("OKX API ответ: {}", responseBody);
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                return "0".equals(jsonResponse.get("code").getAsString());
            }
        } catch (Exception e) {
            log.error("Ошибка при проверке подключения к OKX API: {}", e.getMessage());
            return false;
        }
    }

    private void updatePortfolioFromOkx() {
        try {
            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = ACCOUNT_BALANCE_ENDPOINT;

            String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            String signature = generateSignature("GET", endpoint, "", timestamp);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if ("0".equals(jsonResponse.get("code").getAsString())) {
                    JsonArray data = jsonResponse.getAsJsonArray("data");
                    if (data.size() > 0) {
                        JsonObject account = data.get(0).getAsJsonObject();
                        JsonArray details = account.getAsJsonArray("details");

                        for (JsonElement detail : details) {
                            JsonObject currency = detail.getAsJsonObject();
                            String ccy = currency.get("ccy").getAsString();

                            if ("USDT".equals(ccy)) {
                                String availEqStr = currency.get("availEq").getAsString();
                                String eqStr = currency.get("eq").getAsString();

                                BigDecimal availableBalance = new BigDecimal(availEqStr);
                                BigDecimal totalBalance = new BigDecimal(eqStr);

                                // Для обновления баланса нужно будет использовать другой подход
                                // Пока просто обновляем значения портфолио
                                okxPortfolioManager.updatePortfolioValue();
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при обновлении портфолио с OKX: {}", e.getMessage());
        }
    }

    private void syncPositionsWithOkx() {
        try {
            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_POSITIONS_ENDPOINT;

            String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            String signature = generateSignature("GET", endpoint, "", timestamp);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if ("0".equals(jsonResponse.get("code").getAsString())) {
                    JsonArray data = jsonResponse.getAsJsonArray("data");

                    // Обновляем информацию о позициях
                    for (JsonElement positionElement : data) {
                        JsonObject positionData = positionElement.getAsJsonObject();
                        // Здесь можно добавить логику синхронизации позиций
                        // с данными от OKX
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при синхронизации позиций с OKX: {}", e.getMessage());
        }
    }

    /**
     * Корректировка размера позиции согласно lot size OKX
     */
    private BigDecimal adjustPositionSizeToLotSize(String symbol, BigDecimal positionSize) {
        try {
            // Получаем информацию о торговом инструменте
            InstrumentInfo instrumentInfo = getInstrumentInfo(symbol);
            if (instrumentInfo == null) {
                log.warn("⚠️ Не удалось получить информацию о торговом инструменте {}", symbol);
                return positionSize;
            }

            BigDecimal lotSize = instrumentInfo.getLotSize();
            BigDecimal minSize = instrumentInfo.getMinSize();

            log.debug("🔍 Инструмент {}: lot size = {}, min size = {}, исходный размер = {}",
                    symbol, lotSize, minSize, positionSize);

            // Проверяем минимальный размер
            if (positionSize.compareTo(minSize) < 0) {
                log.warn("⚠️ Размер позиции {} меньше минимального {}, устанавливаем минимальный",
                        positionSize, minSize);
                positionSize = minSize;
            }

            // Корректируем размер до кратного lot size
            BigDecimal adjustedSize = positionSize.divide(lotSize, 0, RoundingMode.DOWN)
                    .multiply(lotSize);

            // Если после корректировки размер стал меньше минимального, увеличиваем на один lot
            if (adjustedSize.compareTo(minSize) < 0) {
                adjustedSize = minSize;
            }

            log.info("📏 Скорректированный размер позиции для {}: {} -> {}",
                    symbol, positionSize, adjustedSize);

            return adjustedSize;

        } catch (Exception e) {
            log.error("❌ Ошибка при корректировке размера позиции для {}: {}", symbol, e.getMessage());
            return positionSize; // Возвращаем исходный размер при ошибке
        }
    }

    /**
     * Получение информации о торговом инструменте
     */
    private InstrumentInfo getInstrumentInfo(String symbol) {
        try {
            // Проверяем кэш
            InstrumentInfo cachedInfo = instrumentInfoCache.get(symbol);
            if (cachedInfo != null) {
                log.debug("🔍 Использую кэшированную информацию о торговом инструменте {}", symbol);
                return cachedInfo;
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = PUBLIC_INSTRUMENTS_ENDPOINT + "?instType=SWAP&instId=" + symbol; //https://www.okx.com/api/v5/public/instruments?instType=SWAP&instId=XRP-USDT-SWAP

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("❌ Ошибка HTTP при получении информации о торговом инструменте: {}", response.code());
                    return null;
                }

                String responseBody = response.body().string();
                log.debug("🔍 Информация о торговом инструменте {}: {}", symbol, responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка OKX API при получении информации о торговом инструменте: {}",
                            jsonResponse.get("msg").getAsString());
                    return null;
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.isEmpty()) {
                    log.warn("⚠️ Торговый инструмент {} не найден", symbol);
                    return null;
                }

                JsonObject instrument = data.get(0).getAsJsonObject();

                String lotSizeStr = instrument.get("lotSz").getAsString();
                String minSizeStr = instrument.get("minSz").getAsString();

                InstrumentInfo instrumentInfo = new InstrumentInfo(
                        symbol,
                        new BigDecimal(lotSizeStr),
                        new BigDecimal(minSizeStr)
                );

                // Кэшируем информацию
                instrumentInfoCache.put(symbol, instrumentInfo);
                log.debug("🔍 Кэшировал информацию о торговом инструменте {}: lot size = {}, min size = {}",
                        symbol, instrumentInfo.getLotSize(), instrumentInfo.getMinSize());

                return instrumentInfo;

            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении информации о торговом инструменте {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Информация о торговом инструменте
     */
    private static class InstrumentInfo {
        private final String symbol;
        private final BigDecimal lotSize;
        private final BigDecimal minSize;

        public InstrumentInfo(String symbol, BigDecimal lotSize, BigDecimal minSize) {
            this.symbol = symbol;
            this.lotSize = lotSize;
            this.minSize = minSize;
        }

        public String getSymbol() {
            return symbol;
        }

        public BigDecimal getLotSize() {
            return lotSize;
        }

        public BigDecimal getMinSize() {
            return minSize;
        }
    }

    private String generateSignature(String method, String endpoint, String body, String timestamp) {
        try {
            String message = timestamp + method + endpoint + body;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Ошибка при генерации подписи: {}", e.getMessage());
            return "";
        }
    }
}