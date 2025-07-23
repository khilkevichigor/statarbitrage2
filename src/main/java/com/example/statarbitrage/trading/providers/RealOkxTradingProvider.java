package com.example.statarbitrage.trading.providers;

import com.example.statarbitrage.client_okx.OkxClient;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.*;
import com.example.statarbitrage.trading.services.GeolocationService;
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
import java.util.*;
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
    private final GeolocationService geolocationService;

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
    private static final String ACCOUNT_CONFIG_ENDPOINT = "/api/v5/account/config";
    private static final String SET_LEVERAGE_ENDPOINT = "/api/v5/account/set-leverage";

    public RealOkxTradingProvider(OkxPortfolioManager okxPortfolioManager, OkxClient okxClient, GeolocationService geolocationService) {
        this.okxPortfolioManager = okxPortfolioManager;
        this.okxClient = okxClient;
        this.geolocationService = geolocationService;
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
            log.error("❌ Ошибка при проверке баланса: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public TradeResult openLongPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        try {
            if (!preTradeChecks(symbol, amount)) {
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, "Ошибка предотлетной проверки");
            }

            BigDecimal positionSize = calculateAndAdjustPositionSize(symbol, amount, leverage);
            if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, "Размер позиции слишком мал");
            }

            // Пересчитываем итоговую долларовую сумму после корректировки lot size
            BigDecimal currentPrice = getCurrentPrice(symbol);
            BigDecimal adjustedAmount = positionSize.multiply(currentPrice).divide(leverage, 2, RoundingMode.HALF_UP);
            log.info("📊 {} LONG: Исходная сумма: ${}, Скорректированная: ${}, Размер: {} единиц",
                    symbol, amount, adjustedAmount, positionSize);

            // Новая проверка размера ордера
            String validationError = validateOrderSize(symbol, adjustedAmount, positionSize, currentPrice);
            if (validationError != null) {
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, validationError);
            }

            if (!setLeverage(symbol, leverage)) {
                log.warn("⚠️ Не удалось установить плечо {}, продолжаем с текущим плечом", leverage);
            }

            TradeResult orderResult = placeOrder(symbol, "buy", "long", positionSize, leverage);
            if (!orderResult.isSuccess()) {
                return orderResult;
            }

            Position position = createPositionFromTradeResult(orderResult, PositionType.LONG, amount, leverage);
            positions.put(position.getPositionId(), position);
            okxPortfolioManager.onPositionOpened(position);

            tradeHistory.add(orderResult);
            log.info("✅ Открыта LONG позиция на OKX: {} | Размер: {} | Цена: {} | OrderID: {}",
                    symbol, position.getSize(), position.getEntryPrice(), position.getExternalOrderId());

            return orderResult;

        } catch (Exception e) {
            log.error("❌ Ошибка при открытии LONG позиции {}: {}", symbol, e.getMessage());
            return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, e.getMessage());
        }
    }

    @Override
    public TradeResult openShortPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        try {
            if (!preTradeChecks(symbol, amount)) {
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, "Ошибка предотлетной проверки");
            }

            BigDecimal positionSize = calculateAndAdjustPositionSize(symbol, amount, leverage);
            if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, "Размер позиции слишком мал");
            }

            // Пересчитываем итоговую долларовую сумму после корректировки lot size
            BigDecimal currentPrice = getCurrentPrice(symbol);
            BigDecimal adjustedAmount = positionSize.multiply(currentPrice).divide(leverage, 2, RoundingMode.HALF_UP);
            log.info("📊 {} SHORT: Исходная сумма: ${}, Скорректированная: ${}, Размер: {} единиц",
                    symbol, amount, adjustedAmount, positionSize);

            // Новая проверка размера ордера
            String validationError = validateOrderSize(symbol, adjustedAmount, positionSize, currentPrice);
            if (validationError != null) {
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, validationError);
            }

            if (!setLeverage(symbol, leverage)) {
                log.warn("⚠️ Не удалось установить плечо {}, продолжаем с текущим плечом", leverage);
            }

            TradeResult orderResult = placeOrder(symbol, "sell", "short", positionSize, leverage);
            if (!orderResult.isSuccess()) {
                return orderResult;
            }

            Position position = createPositionFromTradeResult(orderResult, PositionType.SHORT, amount, leverage);
            positions.put(position.getPositionId(), position);
            okxPortfolioManager.onPositionOpened(position);

            tradeHistory.add(orderResult);
            log.info("✅ Открыта SHORT позиция на OKX: {} | Размер: {} | Цена: {} | OrderID: {}",
                    symbol, position.getSize(), position.getEntryPrice(), position.getExternalOrderId());

            return orderResult;

        } catch (Exception e) {
            log.error("❌ Ошибка при открытии SHORT позиции {}: {}", symbol, e.getMessage());
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

            // 1. Отправляем ордер на закрытие и получаем результат с реальной ценой и комиссией
            TradeResult closeOrderResult = placeCloseOrder(position);
            if (!closeOrderResult.isSuccess()) {
                return closeOrderResult; // Возвращаем ошибку, если не удалось закрыть ордер
            }

            // 2. Рассчитываем и сохраняем реализованный PnL, используя данные из результата ордера
            position.calculateAndSetRealizedPnL(closeOrderResult.getExecutionPrice(), closeOrderResult.getFees());

            // 3. Обновляем статус позиции
            position.setStatus(PositionStatus.CLOSED);
            position.setLastUpdated(LocalDateTime.now());

            // 4. Освобождаем средства и уведомляем портфолио о закрытии
            okxPortfolioManager.releaseReservedBalance(position.getAllocatedAmount());
            BigDecimal totalFees = position.getOpeningFees().add(position.getClosingFees());
            okxPortfolioManager.onPositionClosed(position, position.getRealizedPnL(), totalFees);

            // 5. Создаем итоговый результат операции
            TradeResult finalResult = TradeResult.success(positionId, TradeOperationType.CLOSE_POSITION,
                    position.getSymbol(), closeOrderResult.getExecutedSize(), closeOrderResult.getExecutionPrice(), closeOrderResult.getFees(), closeOrderResult.getExternalOrderId());
            finalResult.setPnl(position.getRealizedPnL());
            finalResult.setExternalOrderId(closeOrderResult.getExternalOrderId());

            tradeHistory.add(finalResult);

            log.info("⚫ Закрыта позиция на OKX: {} {} | Цена: {} | PnL: {} | OrderID: {}",
                    position.getSymbol(), position.getDirectionString(),
                    finalResult.getExecutionPrice(), finalResult.getPnl(), finalResult.getExternalOrderId());

            return finalResult;

        } catch (Exception e) {
            log.error("❌ Ошибка при закрытии позиции {}: {}", positionId, e.getMessage());
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
            log.error("❌ Ошибка при получении активных позиций: {}", e.getMessage());
            return new ArrayList<>(positions.values());
        }
    }

    @Override
    public Position getPosition(String positionId) {
        return positions.get(positionId);
    }

//    @Override
//    public boolean isPositionOpen(PairData pairData) {
//        return false;
//    }

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
                    log.warn("⚠️ Не удалось обновить цену для позиции {}: {}",
                            position.getPositionId(), e.getMessage());
                }
            }

            // Обновляем портфолио
            okxPortfolioManager.updatePortfolioValue();

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении цен позиций: {}", e.getMessage());
        }
    }

    @Override
    public void updatePositionPrices(List<String> tickers) {
        try {
            for (Position position : positions.values()) {
                try {
                    if (tickers.contains(position.getSymbol())) {
                        BigDecimal currentPrice = getCurrentPrice(position.getSymbol());
                        if (currentPrice != null) {
                            position.setCurrentPrice(currentPrice);
                            position.calculateUnrealizedPnL();
                            position.setLastUpdated(LocalDateTime.now());
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Не удалось обновить цену для позиции {}: {}",
                            position.getPositionId(), e.getMessage());
                }
            }

            // Обновляем портфолио
            okxPortfolioManager.updatePortfolioValue();

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении цен позиций: {}", e.getMessage());
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
            log.error("❌ Ошибка при получении цены для {}: {}", symbol, e.getMessage());
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
                log.warn("⚠️ OKX API ключи не настроены");
                return false;
            }

            // Проверяем подключение запросом к API
            return checkApiConnection();
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке подключения к OKX: {}", e.getMessage());
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

    private TradeResult placeOrder(String symbol, String side, String posSide, BigDecimal size, BigDecimal leverage) {
        try {
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Размещение ордера заблокировано из-за геолокации!");
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, "Геолокация не разрешена");
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_ORDER_ENDPOINT;
            String correctPosSide = determinePosSide(posSide);

            JsonObject orderData = new JsonObject();
            orderData.addProperty("instId", symbol);
            orderData.addProperty("tdMode", "isolated");
            orderData.addProperty("side", side);
            orderData.addProperty("posSide", correctPosSide);
            orderData.addProperty("ordType", "market");
            orderData.addProperty("sz", size.toPlainString()); // Теперь это количество базового актива
            orderData.addProperty("szCcy", getBaseCurrency(symbol)); // Указываем, что sz в базовой валюте
            orderData.addProperty("lever", leverage.toPlainString());

            log.info("📋 Создание ордера OKX: symbol={}, side={}, posSide={}, szCcy={}, sz={}, leverage={}",
                    symbol, side, correctPosSide, getBaseCurrency(symbol), size, leverage);

            RequestBody body = RequestBody.create(orderData.toString(), MediaType.get("application/json"));
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

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка при создании ордера: {}", responseBody);
                    return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, jsonResponse.get("msg").getAsString());
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() > 0) {
                    String orderId = data.get(0).getAsJsonObject().get("ordId").getAsString();
                    return getOrderDetails(orderId, symbol, Objects.equals(posSide, "long") ? TradeOperationType.OPEN_LONG : TradeOperationType.OPEN_SHORT);
                }
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, "Не удалось получить ID ордера");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при создании ордера: {}", e.getMessage());
            return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, e.getMessage());
        }
    }

    private boolean preTradeChecks(String symbol, BigDecimal amount) {
        if (!isConnected()) {
            log.error("❌ Нет подключения к OKX API");
            return false;
        }
        if (!hasAvailableBalance(amount)) {
            log.error("❌ Недостаточно средств для открытия позиции");
            return false;
        }
        return true;
    }

    private BigDecimal calculateAndAdjustPositionSize(String symbol, BigDecimal amount, BigDecimal leverage) {
        BigDecimal currentPrice = getCurrentPrice(symbol);
        if (currentPrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal positionSize = amount.multiply(leverage).divide(currentPrice, 8, RoundingMode.HALF_UP);
        return adjustPositionSizeToLotSize(symbol, positionSize);
    }

    private Position createPositionFromTradeResult(TradeResult tradeResult, PositionType type, BigDecimal amount, BigDecimal leverage) {
        String positionId = UUID.randomUUID().toString();
        return Position.builder()
                .positionId(positionId)
                .symbol(tradeResult.getSymbol())
                .type(type)
                .size(tradeResult.getExecutedSize()) // Используем исполненный размер
                .entryPrice(tradeResult.getExecutionPrice())
                .currentPrice(tradeResult.getExecutionPrice())
                .leverage(leverage)
                .allocatedAmount(amount)
                .openingFees(tradeResult.getFees())
                .status(PositionStatus.OPEN)
                .openTime(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .externalOrderId(tradeResult.getExternalOrderId())
                .build();
    }

    private TradeResult placeCloseOrder(Position position) {
        try {
            // ЗАЩИТА: Проверяем геолокацию перед вызовом OKX API
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Закрытие ордера заблокировано из-за геолокации!");
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(), "Геолокация не разрешена");
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_ORDER_ENDPOINT;

            String side = position.getType() == PositionType.LONG ? "sell" : "buy";
            String correctPosSide = isHedgeMode() ? (side.equals("buy") ? "short" : "long") : "net";

            // Рассчитываем общую стоимость ордера для закрытия
            BigDecimal totalOrderValue = position.getSize().multiply(position.getCurrentPrice());

            JsonObject orderData = new JsonObject();
            orderData.addProperty("instId", position.getSymbol());
            orderData.addProperty("tdMode", "isolated");
            orderData.addProperty("side", side);
            orderData.addProperty("posSide", correctPosSide);
            orderData.addProperty("ordType", "market");
            orderData.addProperty("sz", position.getSize().toPlainString()); // Теперь это количество базового актива
            orderData.addProperty("szCcy", getBaseCurrency(position.getSymbol())); // Указываем, что sz в базовой валюте

            RequestBody body = RequestBody.create(orderData.toString(), MediaType.get("application/json"));
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

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка при создании ордера на закрытие: {}", responseBody);
                    return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(), jsonResponse.get("msg").getAsString());
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() > 0) {
                    String orderId = data.get(0).getAsJsonObject().get("ordId").getAsString();
                    // После успешного размещения ордера, запрашиваем его детали для получения цены и комиссии
                    return getOrderDetails(orderId, position.getSymbol(), TradeOperationType.CLOSE_POSITION);
                }
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(), "Не удалось получить ID ордера");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при закрытии ордера: {}", e.getMessage());
            return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(), e.getMessage());
        }
    }

    private TradeResult getOrderDetails(String orderId, String symbol, TradeOperationType tradeOperationType) {
        if (tradeOperationType == TradeOperationType.OPEN_LONG) {
            log.info("Получаем информацию по только что открытой LONG позиции symbol={} orderId={}", symbol, orderId);
        } else if (tradeOperationType == TradeOperationType.OPEN_SHORT) {
            log.info("Получаем информацию по только что открытой SHORT позиции symbol={} orderId={}", symbol, orderId);
        } else if (tradeOperationType == TradeOperationType.CLOSE_POSITION) {
            log.info("Получаем информацию по только что закрытой позиции symbol={} orderId={}", symbol, orderId);
        } else {
            log.error("❌ Неизвестная операция для получения деталей с окх для symbol={} orderId={}", symbol, orderId);
        }

        try {
            // ЗАЩИТА: Проверяем геолокацию перед вызовом OKX API
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Получение деталей ордера заблокировано из-за геолокации!");
                return TradeResult.failure(tradeOperationType, symbol, "Геолокация не разрешена");
            }

            // Пауза, чтобы ордер успел исполниться
            Thread.sleep(2000); // 2 секунды

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = "/api/v5/trade/order?instId=" + symbol + "&ordId=" + orderId;

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

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка при получении деталей ордера {}: {}", orderId, responseBody);
                    return TradeResult.failure(TradeOperationType.CLOSE_POSITION, symbol, "Не удалось получить детали ордера");
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() > 0) {
                    JsonObject orderInfo = data.get(0).getAsJsonObject();
                    BigDecimal avgPx = new BigDecimal(orderInfo.get("avgPx").getAsString());
                    BigDecimal fee = new BigDecimal(orderInfo.get("fee").getAsString()).abs();
                    BigDecimal size = new BigDecimal(orderInfo.get("accFillSz").getAsString());

                    log.info("Информация по symbol={}: size={} | avgPx={} | fee={} | orderId={}", symbol, size, avgPx, fee, orderId);

                    return TradeResult.success(orderId, TradeOperationType.CLOSE_POSITION, symbol, size, avgPx, fee, orderId);
                }
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, symbol, "Детали ордера не найдены");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении деталей ордера {}: {}", orderId, e.getMessage());
            return TradeResult.failure(TradeOperationType.CLOSE_POSITION, symbol, e.getMessage());
        }
    }

    private void cancelOrder(String orderId, String symbol) {
        try {
            // ЗАЩИТА: Проверяем геолокацию перед вызовом OKX API
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Отмена ордера заблокирована из-за геолокации!");
                return;
            }

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
            log.error("❌ Ошибка при отмене ордера {}: {}", orderId, e.getMessage());
        }
    }

    private boolean checkApiConnection() {
        try {
            // ЗАЩИТА: Проверяем геолокацию перед вызовом OKX API
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
                return false;
            }

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
            log.error("❌ Ошибка при проверке подключения к OKX API: {}", e.getMessage());
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
            log.error("❌ Ошибка при обновлении портфолио с OKX: {}", e.getMessage());
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
            log.error("❌ Ошибка при синхронизации позиций с OKX: {}", e.getMessage());
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
                String minCcyAmtStr = instrument.has("minCcyAmt") ? instrument.get("minCcyAmt").getAsString() : "0";
                String minNotionalStr = instrument.has("minNotional") ? instrument.get("minNotional").getAsString() : "0";

                InstrumentInfo instrumentInfo = new InstrumentInfo(
                        symbol,
                        new BigDecimal(lotSizeStr),
                        new BigDecimal(minSizeStr),
                        new BigDecimal(minCcyAmtStr),
                        new BigDecimal(minNotionalStr)
                );

                // Кэшируем информацию
                instrumentInfoCache.put(symbol, instrumentInfo);
                log.debug("🔍 Кэшировал информацию о торговом инструменте {}: lot size = {}, min size = {}, minCcyAmt = {}, minNotional = {}",
                        symbol, instrumentInfo.getLotSize(), instrumentInfo.getMinSize(), instrumentInfo.getMinCcyAmt(), instrumentInfo.getMinNotional());

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
        private final BigDecimal minCcyAmt; // Минимальная сумма в валюте котировки
        private final BigDecimal minNotional; // Минимальная условная стоимость

        public InstrumentInfo(String symbol, BigDecimal lotSize, BigDecimal minSize, BigDecimal minCcyAmt, BigDecimal minNotional) {
            this.symbol = symbol;
            this.lotSize = lotSize;
            this.minSize = minSize;
            this.minCcyAmt = minCcyAmt;
            this.minNotional = minNotional;
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

        public BigDecimal getMinCcyAmt() {
            return minCcyAmt;
        }

        public BigDecimal getMinNotional() {
            return minNotional;
        }
    }

    /**
     * Настройка плеча для инструмента на OKX
     */
    private boolean setLeverage(String symbol, BigDecimal leverage) {
        try {
            // ЗАЩИТА: Проверяем геолокацию перед вызовом OKX API
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Установка плеча заблокирована из-за геолокации!");
                return false;
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = SET_LEVERAGE_ENDPOINT;
            String fullUrl = baseUrl + endpoint;

            JsonObject leverageData = new JsonObject();
            leverageData.addProperty("instId", symbol);
            leverageData.addProperty("lever", leverage.toString());
            leverageData.addProperty("mgnMode", "isolated"); // Изолированная маржа

            log.info("🔧 Настройка плеча OKX: symbol={}, leverage={}", symbol, leverage);
            log.info("🔧 URL: {}", fullUrl);
            log.info("🔧 Payload: {}", leverageData.toString());
            log.info("🔧 API Key: {}", apiKey != null ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "null");

            RequestBody body = RequestBody.create(
                    leverageData.toString(),
                    MediaType.get("application/json")
            );

            String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            log.info("🔧 Timestamp: {}", timestamp);

            String signature = generateSignature("POST", endpoint, leverageData.toString(), timestamp);
            log.info("🔧 Signature: {}", signature != null ? signature.substring(0, Math.min(8, signature.length())) + "..." : "null");

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .post(body)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .addHeader("Content-Type", "application/json")
                    .build();

            log.info("🔧 Отправляем запрос на установку плеча...");
            try (Response response = httpClient.newCall(request).execute()) {
                log.info("🔧 Получен ответ: HTTP {}", response.code());

                if (!response.isSuccessful()) {
                    log.error("❌ HTTP ошибка: {}", response.code());
                    return false;
                }

                String responseBody = response.body().string();
                log.info("🔧 OKX установка плеча ответ: {}", responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if ("0".equals(jsonResponse.get("code").getAsString())) {
                    log.info("✅ Плечо {} успешно установлено для {}", leverage, symbol);
                    return true;
                } else {
                    log.error("❌ Ошибка установки плеча: {}", responseBody);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при установке плеча для {}: {}", symbol, e.getMessage());
            log.error("❌ Тип ошибки: {}", e.getClass().getSimpleName());
            log.error("❌ Стек ошибки: ", e);
            return false;
        }
    }

    /**
     * Определяет правильный posSide в зависимости от режима аккаунта
     */
    private String determinePosSide(String intendedPosSide) {
        if (isHedgeMode()) {
            // В Hedge режиме используем переданный posSide (long/short)
            return intendedPosSide;
        } else {
            // В Net режиме всегда используем "net"
            return "net";
        }
    }

    /**
     * Проверка режима позиции (Net или Hedge)
     */
    private boolean isHedgeMode() {
        try {
            // ЗАЩИТА: Проверяем геолокацию перед вызовом OKX API
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Проверка режима позиций заблокирована из-за геолокации!");
                return false;
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = ACCOUNT_CONFIG_ENDPOINT;

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
                if (!response.isSuccessful()) {
                    log.error("❌ Ошибка HTTP при получении конфигурации аккаунта: {}", response.code());
                    return false; // По умолчанию считаем Net режим
                }

                String responseBody = response.body().string();
                log.debug("🔍 Конфигурация аккаунта OKX: {}", responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка OKX API при получении конфигурации: {}", jsonResponse.get("msg").getAsString());
                    return false;
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.isEmpty()) {
                    log.warn("⚠️ Данные конфигурации аккаунта пусты");
                    return false;
                }

                JsonObject accountConfig = data.get(0).getAsJsonObject();
                String posMode = accountConfig.get("posMode").getAsString();

                // posMode: "net_mode" = Net режим, "long_short_mode" = Hedge режим
                boolean isHedge = "long_short_mode".equals(posMode);
                log.info("🔍 Режим позиций OKX: {} ({})", posMode, isHedge ? "Hedge" : "Net");

                return isHedge;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при определении режима позиций: {}", e.getMessage());
            return false; // По умолчанию считаем Net режим
        }
    }

    /**
     * Публичный метод для тестирования геолокации
     * Делегирует вызов GeolocationService
     */
    public String testGeolocation() {
        return geolocationService.forceCheckGeolocation();
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
            log.error("❌ Ошибка при генерации подписи: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Извлекает базовую валюту из символа торгового инструмента (например, "BTC" из "BTC-USDT-SWAP").
     *
     * @param symbol Символ торгового инструмента.
     * @return Базовая валюта.
     */
    private String getBaseCurrency(String symbol) {
        if (symbol == null || !symbol.contains("-")) {
            log.warn("⚠️ Некорректный символ для извлечения базовой валюты: {}", symbol);
            return "";
        }
        return symbol.split("-")[0];
    }

    /**
     * Проверяет, соответствует ли размер ордера минимальным требованиям OKX (minCcyAmt, minNotional).
     *
     * @param symbol         Символ торгового инструмента.
     * @param adjustedAmount Скорректированная сумма в USDT (маржа).
     * @param positionSize   Скорректированный размер позиции в единицах актива.
     * @param currentPrice   Текущая рыночная цена.
     * @return null, если размер ордера валиден, иначе сообщение об ошибке.
     */
    private String validateOrderSize(String symbol, BigDecimal adjustedAmount, BigDecimal positionSize, BigDecimal currentPrice) {
        try {
            InstrumentInfo instrumentInfo = getInstrumentInfo(symbol);
            if (instrumentInfo == null) {
                return "Не удалось получить информацию о торговом инструменте для проверки размера ордера.";
            }

            BigDecimal minCcyAmt = instrumentInfo.getMinCcyAmt();
            BigDecimal minNotional = instrumentInfo.getMinNotional();

            // Проверка по minCcyAmt (минимальная сумма в валюте котировки, т.е. USDT)
            if (adjustedAmount.compareTo(minCcyAmt) < 0) {
                log.warn("⚠️ Сумма маржи {} USDT меньше минимальной {} USDT для {}", adjustedAmount, minCcyAmt, symbol);
                return String.format("Сумма маржи %.2f USDT меньше минимальной %.2f USDT.", adjustedAmount, minCcyAmt);
            }

            // Проверка по minNotional (минимальная условная стоимость сделки)
            BigDecimal notionalValue = positionSize.multiply(currentPrice);
            if (notionalValue.compareTo(minNotional) < 0) {
                log.warn("⚠️ Условная стоимость сделки {} USDT меньше минимальной {} USDT для {}", notionalValue, minNotional, symbol);
                return String.format("Условная стоимость сделки %.2f USDT меньше минимальной %.2f USDT.", notionalValue, minNotional);
            }

            return null; // Валидация прошла успешно
        } catch (Exception e) {
            log.error("❌ Ошибка при валидации размера ордера для {}: {}", symbol, e.getMessage());
            return "Ошибка при валидации размера ордера: " + e.getMessage();
        }
    }
}