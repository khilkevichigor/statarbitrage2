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
import lombok.ToString;
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
        log.info("==> openLongPosition: НАЧАЛО для {} | Сумма: ${} | Плечо: {}", symbol, amount, leverage);
        try {
            if (!preTradeChecks(symbol, amount)) {
                log.error("Предотлетная проверка не пройдена.");
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, "Ошибка предотлетной проверки");
            }

            BigDecimal positionSize = calculateAndAdjustPositionSize(symbol, amount, leverage);
            log.info("Рассчитан и скорректирован размер позиции: {}", positionSize);
            if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("Размер позиции после корректировки равен нулю или меньше.");
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, "Размер позиции слишком мал");
            }

            BigDecimal currentPrice = getCurrentPrice(symbol);
            if (currentPrice == null) {
                log.error("Не удалось получить текущую цену для {}.", symbol);
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, "Не удалось получить цену");
            }
            // ИСПРАВЛЕНИЕ: Для фьючерсов adjustedAmount = размер * цена (без деления на leverage)
            // Плечо влияет только на требуемую маржу, а не на размер ордера
            BigDecimal adjustedAmount = positionSize.multiply(currentPrice);
            log.info("📊 {} LONG: Исходная сумма: ${}, Скорректированная: ${}, Размер: {} единиц, Текущая цена: {}",
                    symbol, amount, adjustedAmount, positionSize, currentPrice);

            String validationError = validateOrderSize(symbol, adjustedAmount, positionSize, currentPrice);
            if (validationError != null) {
                log.error("Ошибка валидации размера ордера: {}", validationError);
                return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, validationError);
            }

            if (!setLeverage(symbol, leverage)) {
                log.warn("⚠️ Не удалось установить плечо {}, продолжаем с текущим плечом", leverage);
            }

            TradeResult orderResult = placeOrder(symbol, "buy", "long", adjustedAmount, leverage);
            if (!orderResult.isSuccess()) {
                log.error("Ошибка размещения ордера: {}", orderResult.getErrorMessage());
                return orderResult;
            }

            Position position = createPositionFromTradeResult(orderResult, PositionType.LONG, amount, leverage);
            positions.put(position.getPositionId(), position);
            okxPortfolioManager.onPositionOpened(position);
            log.info("Позиция создана и сохранена. ID: {}", position.getPositionId());

            tradeHistory.add(orderResult);
            log.info("✅ Открыта LONG позиция на OKX: {} | Размер: {} | Цена: {} | OrderID: {}",
                    symbol, position.getSize(), position.getEntryPrice(), position.getExternalOrderId());

            // НОВАЯ ФУНКЦИЯ: Логируем реальные данные о позиции с OKX
            logRealPositionData(symbol, "OPEN_LONG");

            // ИСПРАВЛЕНИЕ: Заменяем positionId в результате на внутренний ID для корректного поиска позиций
            orderResult.setPositionId(position.getPositionId());

            log.info("<== openLongPosition: КОНЕЦ (Успех) для {}", symbol);
            return orderResult;

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при открытии LONG позиции {}: {}", symbol, e.getMessage(), e);
            return TradeResult.failure(TradeOperationType.OPEN_LONG, symbol, e.getMessage());
        }
    }

    @Override
    public TradeResult openShortPosition(String symbol, BigDecimal amount, BigDecimal leverage) {
        log.info("==> openShortPosition: НАЧАЛО для {} | Сумма: ${} | Плечо: {}", symbol, amount, leverage);
        try {
            if (!preTradeChecks(symbol, amount)) {
                log.error("Предотлетная проверка не пройдена.");
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, "Ошибка предотлетной проверки");
            }

            BigDecimal positionSize = calculateAndAdjustPositionSize(symbol, amount, leverage);
            log.info("Рассчитан и скорректирован размер позиции: {}", positionSize);
            if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("Размер позиции после корректировки равен нулю или меньше.");
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, "Размер позиции слишком мал");
            }

            BigDecimal currentPrice = getCurrentPrice(symbol);
            if (currentPrice == null) {
                log.error("Не удалось получить текущую цену для {}.", symbol);
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, "Не удалось получить цену");
            }
            // ИСПРАВЛЕНИЕ: Для фьючерсов adjustedAmount = размер * цена (без деления на leverage)
            // Плечо влияет только на требуемую маржу, а не на размер ордера
            BigDecimal adjustedAmount = positionSize.multiply(currentPrice);
            log.info("📊 {} SHORT: Исходная сумма: ${}, Скорректированная: ${}, Размер: {} единиц, Текущая цена: {}",
                    symbol, amount, adjustedAmount, positionSize, currentPrice);

            String validationError = validateOrderSize(symbol, adjustedAmount, positionSize, currentPrice);
            if (validationError != null) {
                log.error("Ошибка валидации размера ордера: {}", validationError);
                return TradeResult.failure(TradeOperationType.OPEN_SHORT, symbol, validationError);
            }

            if (!setLeverage(symbol, leverage)) {
                log.warn("⚠️ Не удалось установить плечо {}, продолжаем с текущим плечом", leverage);
            }

            TradeResult orderResult = placeOrder(symbol, "sell", "short", adjustedAmount, leverage);
            if (!orderResult.isSuccess()) {
                log.error("Ошибка размещения ордера: {}", orderResult.getErrorMessage());
                return orderResult;
            }

            Position position = createPositionFromTradeResult(orderResult, PositionType.SHORT, amount, leverage);
            positions.put(position.getPositionId(), position);
            okxPortfolioManager.onPositionOpened(position);
            log.info("Позиция создана и сохранена. ID: {}", position.getPositionId());

            tradeHistory.add(orderResult);
            log.info("✅ Открыта SHORT позиция на OKX: {} | Размер: {} | Цена: {} | OrderID: {}",
                    symbol, position.getSize(), position.getEntryPrice(), position.getExternalOrderId());

            // НОВАЯ ФУНКЦИЯ: Логируем реальные данные о позиции с OKX
            logRealPositionData(symbol, "OPEN_SHORT");

            // ИСПРАВЛЕНИЕ: Заменяем positionId в результате на внутренний ID для корректного поиска позиций
            orderResult.setPositionId(position.getPositionId());

            log.info("<== openShortPosition: КОНЕЦ (Успех) для {}", symbol);
            return orderResult;

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при открытии SHORT позиции {}: {}", symbol, e.getMessage(), e);
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
            log.info("🔄 Обновление позиций: синхронизация с реальными данными OKX");
            
            // Синхронизируем позиции с OKX для получения реальных PnL
            syncPositionsWithOkx();

            // Дополнительно обновляем цены через ticker API (для случаев когда позиции не найдены в OKX)
            for (Position position : positions.values()) {
                try {
                    if (position.getStatus() == PositionStatus.OPEN) {
                        BigDecimal currentPrice = getCurrentPrice(position.getSymbol());
                        if (currentPrice != null) {
                            position.setCurrentPrice(currentPrice);
                            // НЕ пересчитываем PnL, если он уже был обновлен через syncPositionsWithOkx
                            if (position.getUnrealizedPnL() == null || position.getUnrealizedPnL().compareTo(BigDecimal.ZERO) == 0) {
                                position.calculateUnrealizedPnL();
                            }
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
        log.info("==> placeOrder: НАЧАЛО для {} | side: {} | posSide: {} | size: {} | leverage: {}", symbol, side, posSide, size, leverage);
        TradeOperationType tradeOperationType = posSide.equals("long") ? TradeOperationType.OPEN_LONG : TradeOperationType.OPEN_SHORT;
        try {
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Размещение ордера заблокировано из-за геолокации!");
                return TradeResult.failure(tradeOperationType, symbol, "Геолокация не разрешена");
            }
            log.info("Проверка геолокации пройдена.");

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_ORDER_ENDPOINT;
            String correctPosSide = determinePosSide(posSide);
            log.info("Определен correctPosSide: {}", correctPosSide);

            // Получаем информацию об инструменте для правильного расчета размера
            InstrumentInfo instrumentInfo = getInstrumentInfo(symbol);
            if (instrumentInfo == null) {
                log.error("❌ Не удалось получить информацию об инструменте {}", symbol);
                return TradeResult.failure(tradeOperationType, symbol, "Не удалось получить информацию об инструменте");
            }

            log.info("📋 Информация об инструменте {}: {}", symbol, instrumentInfo);

            // Извлекаем параметры lot size
            BigDecimal lotSize = instrumentInfo.getLotSize();
            BigDecimal minSize = instrumentInfo.getMinSize();

            log.info("📋 Lot Size: {}, Min Size: {}", lotSize, minSize);

            // Получаем текущую цену для конвертации
            BigDecimal currentPrice = getCurrentPrice(symbol);
            if (currentPrice == null) {
                log.error("❌ Не удалось получить цену для {}", symbol);
                return TradeResult.failure(tradeOperationType, symbol, "Не удалось получить цену");
            }

            // Конвертируем размер из USDT в базовые единицы
            BigDecimal sizeInBaseUnits = size.divide(currentPrice, 8, RoundingMode.DOWN);
            log.info("💰 Конвертация размера: {}$ / {} = {} базовых единиц", size, currentPrice, sizeInBaseUnits);

            // Округляем до lot size
            BigDecimal adjustedSize = sizeInBaseUnits.divide(lotSize, 0, RoundingMode.DOWN).multiply(lotSize);
            if (adjustedSize.compareTo(minSize) < 0) {
                adjustedSize = minSize;
            }

            log.info("📏 Скорректированный размер: {} -> {} базовых единиц", sizeInBaseUnits, adjustedSize);

            // Рассчитываем итоговую условную стоимость и маржу
            BigDecimal notionalValue = adjustedSize.multiply(currentPrice);
            BigDecimal requiredMargin = notionalValue.divide(leverage, 2, RoundingMode.HALF_UP);
            log.info("🔍 Условная стоимость: {} USD, требуемая маржа: {} USDT (с плечом {}x)",
                    notionalValue, requiredMargin, leverage);

            JsonObject orderData = new JsonObject();
            orderData.addProperty("instId", symbol);
            orderData.addProperty("tdMode", "isolated");
            orderData.addProperty("side", side);
            orderData.addProperty("posSide", correctPosSide);
            orderData.addProperty("ordType", "market");
            orderData.addProperty("sz", adjustedSize.toPlainString());
            // Для SWAP инструментов не указываем szCcy - размер в базовых единицах
            orderData.addProperty("lever", leverage.toPlainString());

            log.info("📋 Тело запроса для создания ордера OKX: {}", orderData.toString());

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

            log.info("Отправка запроса на создание ордера...");
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                log.info("Получен ответ от OKX API: HTTP {} | {}", response.code(), responseBody);
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка при создании ордера: {}", responseBody);
                    return TradeResult.failure(tradeOperationType, symbol, jsonResponse.get("msg").getAsString());
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() > 0) {
                    String orderId = data.get(0).getAsJsonObject().get("ordId").getAsString();
                    log.info("Ордер успешно размещен. OrderID: {}. Получаем детали ордера...", orderId);
                    return getOrderDetails(orderId, symbol, tradeOperationType);
                }
                log.error("Не удалось получить ID ордера из ответа API.");
                return TradeResult.failure(tradeOperationType, symbol, "Не удалось получить ID ордера");
            }
        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при создании ордера: {}", e.getMessage(), e);
            return TradeResult.failure(tradeOperationType, symbol, e.getMessage());
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

        // Получаем информацию об инструменте для ctVal
        InstrumentInfo instrumentInfo = getInstrumentInfo(symbol);
        if (instrumentInfo == null) {
            log.error("❌ Не удалось получить информацию об инструменте {}", symbol);
            return BigDecimal.ZERO;
        }

        BigDecimal ctVal = instrumentInfo.getCtVal();
        BigDecimal minSize = instrumentInfo.getMinSize();
        log.info("📋 Информация для расчета {}: ctVal={}, цена={}, minSize={}", symbol, ctVal, currentPrice, minSize);

        // ПРОВЕРЯЕМ: сколько будет стоить минимальный лот
        BigDecimal minLotCost = minSize.multiply(ctVal).multiply(currentPrice).divide(leverage, 2, RoundingMode.HALF_UP);
        log.info("💰 Стоимость минимального лота: {} контрактов × {} ctVal × {} цена ÷ {} плечо = {} USDT",
                minSize, ctVal, currentPrice, leverage, minLotCost);

        if (minLotCost.compareTo(amount) > 0) {
            log.error("❌ БЛОКИРОВКА: Минимальный лот стоит {} USDT, а пользователь хочет потратить только {} USDT",
                    minLotCost, amount);
            return BigDecimal.ZERO; // Блокируем открытие позиции
        }

        // Рассчитываем максимальное количество контрактов в рамках бюджета
        BigDecimal maxContracts = amount.multiply(leverage).divide(ctVal.multiply(currentPrice), 8, RoundingMode.DOWN);
        log.info("🔢 Максимально доступно контрактов в рамках бюджета {} USDT: {}", amount, maxContracts);

        // Корректируем до кратного lotSize, но не превышая бюджет
        return adjustPositionSizeToLotSizeWithBudgetLimit(symbol, maxContracts, amount, leverage);
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
        log.info("==> getOrderDetails: НАЧАЛО для orderId={} | symbol={} | operation={}", orderId, symbol, tradeOperationType);

        try {
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Получение деталей ордера {} заблокировано из-за геолокации!", orderId);
                return TradeResult.failure(tradeOperationType, symbol, "Геолокация не разрешена");
            }
            log.info("Проверка геолокации пройдена.");

            // Пауза, чтобы рыночный ордер успел исполниться и появиться в истории
            final int sleepMillis = 2000;
            log.info("Ожидаем {} мс, чтобы ордер {} исполнился...", sleepMillis, orderId);
            Thread.sleep(sleepMillis);

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = "/api/v5/trade/order?instId=" + symbol + "&ordId=" + orderId;
            log.info("Формируем запрос к OKX API: GET {}", baseUrl + endpoint);

            String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            String signature = generateSignature("GET", endpoint, "", timestamp);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .build();

            log.info("Отправка запроса на получение деталей ордера {}...", orderId);
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                log.info("Получен ответ от OKX API для ордера {}: HTTP {} | {}", orderId, response.code(), responseBody);
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    String errorMsg = jsonResponse.get("msg").getAsString();
                    log.error("❌ Ошибка при получении деталей ордера {}: {} (Код: {})", orderId, errorMsg, jsonResponse.get("code").getAsString());
                    return TradeResult.failure(tradeOperationType, symbol, "Не удалось получить детали ордера: " + errorMsg);
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() > 0) {
                    JsonObject orderInfo = data.get(0).getAsJsonObject();
                    log.info("Полная информация по ордеру {}: {}", orderId, orderInfo);

                    BigDecimal avgPx = new BigDecimal(orderInfo.get("avgPx").getAsString());
                    BigDecimal fee = new BigDecimal(orderInfo.get("fee").getAsString()).abs();
                    BigDecimal size = new BigDecimal(orderInfo.get("accFillSz").getAsString());

                    log.info("✅ Детали ордера {} успешно извлечены: symbol={} | size={} | avgPx={} | fee={}", orderId, symbol, size, avgPx, fee);

                    // TODO: сделать сверку каким объемом хотели открыть и каким открыли по факту! Если не бьется то возвращать TradeResult.failure по которому потом закроем все что открылось

                    TradeResult result = TradeResult.success(null, tradeOperationType, symbol, size, avgPx, fee, orderId); //todo null тк positionId еще нету
                    log.info("<== getOrderDetails: КОНЕЦ (Успех) для orderId={}. Результат: {}", orderId, result);
                    return result;
                }
                log.error("❌ Детали ордера {} не найдены в ответе API (массив 'data' пуст).", orderId);
                return TradeResult.failure(tradeOperationType, symbol, "Детали ордера не найдены");
            }
        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при получении деталей ордера {}: {}", orderId, e.getMessage(), e);
            return TradeResult.failure(tradeOperationType, symbol, e.getMessage());
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
            // ЗАЩИТА: Проверяем геолокацию перед вызовом OKX API
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Синхронизация позиций заблокирована из-за геолокации!");
                return;
            }

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
                log.debug("🔄 Синхронизация позиций с OKX: {}", responseBody);
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if ("0".equals(jsonResponse.get("code").getAsString())) {
                    JsonArray data = jsonResponse.getAsJsonArray("data");
                    log.info("📊 Получено {} позиций с OKX для синхронизации", data.size());

                    // Обновляем информацию о позициях с реальными данными OKX
                    for (JsonElement positionElement : data) {
                        JsonObject okxPosition = positionElement.getAsJsonObject();
                        updatePositionFromOkxData(okxPosition);
                    }
                } else {
                    log.error("❌ Ошибка OKX API при синхронизации позиций: {}", jsonResponse.get("msg").getAsString());
                }
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при синхронизации позиций с OKX: {}", e.getMessage());
        }
    }

    /**
     * Обновляет внутреннюю позицию данными с OKX
     */
    private void updatePositionFromOkxData(JsonObject okxPosition) {
        try {
            String instId = getJsonStringValue(okxPosition, "instId");
            String upl = getJsonStringValue(okxPosition, "upl"); // Реальный нереализованный PnL с OKX
            String markPx = getJsonStringValue(okxPosition, "markPx"); // Текущая марк-цена
            String pos = getJsonStringValue(okxPosition, "pos"); // Размер позиции
            String avgPx = getJsonStringValue(okxPosition, "avgPx"); // Средняя цена входа
            String lever = getJsonStringValue(okxPosition, "lever"); // Плечо
            String margin = getJsonStringValue(okxPosition, "margin"); // Используемая маржа

            if ("N/A".equals(instId) || "N/A".equals(upl)) {
                log.debug("⚠️ Пропускаем позицию с неполными данными: {}", instId);
                return;
            }

            // Ищем соответствующую внутреннюю позицию по символу
            Position internalPosition = findPositionBySymbol(instId);
            if (internalPosition != null) {
                // Обновляем позицию реальными данными с OKX
                if (!"N/A".equals(markPx)) {
                    internalPosition.setCurrentPrice(new BigDecimal(markPx));
                }
                if (!"N/A".equals(upl)) {
                    internalPosition.setUnrealizedPnL(new BigDecimal(upl));
                }
                if (!"N/A".equals(avgPx)) {
                    internalPosition.setEntryPrice(new BigDecimal(avgPx));
                }
                if (!"N/A".equals(pos)) {
                    internalPosition.setSize(new BigDecimal(pos).abs()); // abs() для учета знака
                }

                internalPosition.setLastUpdated(LocalDateTime.now());

                log.info("🔄 Обновлена позиция {} с реальными данными OKX: PnL={} USDT, цена={}, размер={}", 
                        instId, upl, markPx, pos);
            } else {
                log.debug("⚠️ Внутренняя позиция для {} не найдена, пропускаем", instId);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении позиции из данных OKX: {}", e.getMessage());
        }
    }

    /**
     * Находит внутреннюю позицию по символу инструмента
     */
    private Position findPositionBySymbol(String symbol) {
        return positions.values().stream()
                .filter(pos -> symbol.equals(pos.getSymbol()))
                .filter(pos -> pos.getStatus() == PositionStatus.OPEN)
                .findFirst()
                .orElse(null);
    }


    /**
     * Корректировка размера позиции согласно lot size с учетом бюджетных ограничений
     */
    private BigDecimal adjustPositionSizeToLotSizeWithBudgetLimit(String symbol, BigDecimal maxContracts, BigDecimal userBudget, BigDecimal leverage) {
        try {
            InstrumentInfo instrumentInfo = getInstrumentInfo(symbol);
            if (instrumentInfo == null) {
                log.warn("⚠️ Не удалось получить информацию о торговом инструменте {}", symbol);
                return BigDecimal.ZERO;
            }

            BigDecimal lotSize = instrumentInfo.getLotSize();
            BigDecimal minSize = instrumentInfo.getMinSize();
            BigDecimal ctVal = instrumentInfo.getCtVal();
            BigDecimal currentPrice = getCurrentPrice(symbol);

            log.info("📋 Корректировка с бюджетным ограничением для {}: maxContracts={}, lotSize={}, minSize={}, ctVal={}",
                    symbol, maxContracts, lotSize, minSize, ctVal);

            // Корректируем до кратного lotSize, но не превышая maxContracts
            BigDecimal adjustedSize = maxContracts.divide(lotSize, 0, RoundingMode.DOWN).multiply(lotSize);

            // Проверяем минимальный размер
            if (adjustedSize.compareTo(minSize) < 0) {
                adjustedSize = minSize;

                // Проверяем не превышает ли минимальный лот бюджет пользователя
                BigDecimal minLotCost = adjustedSize.multiply(ctVal).multiply(currentPrice).divide(leverage, 2, RoundingMode.HALF_UP);
                if (minLotCost.compareTo(userBudget) > 0) {
                    log.error("❌ ОКОНЧАТЕЛЬНАЯ БЛОКИРОВКА: Минимальный лот {} стоит {} USDT, превышает бюджет {} USDT",
                            adjustedSize, minLotCost, userBudget);
                    return BigDecimal.ZERO;
                }
            }

            // Финальная проверка бюджета
            BigDecimal finalCost = adjustedSize.multiply(ctVal).multiply(currentPrice).divide(leverage, 2, RoundingMode.HALF_UP);
            log.info("💰 Финальная стоимость позиции: {} контрактов × {} ctVal × {} цена ÷ {} плечо = {} USDT (бюджет: {} USDT)",
                    adjustedSize, ctVal, currentPrice, leverage, finalCost, userBudget);

            if (finalCost.compareTo(userBudget) > 0) {
                log.error("❌ БЛОКИРОВКА: Финальная стоимость {} USDT превышает бюджет {} USDT", finalCost, userBudget);
                return BigDecimal.ZERO;
            }

            log.info("✅ Размер позиции {} контрактов одобрен (стоимость {} USDT в рамках бюджета {} USDT)",
                    adjustedSize, finalCost, userBudget);
            return adjustedSize;

        } catch (Exception e) {
            log.error("❌ Ошибка при корректировке размера позиции с бюджетным ограничением для {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
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
                log.info("🔍 Информация о торговом инструменте {}: {}", symbol, responseBody);

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
                String ctValStr = instrument.has("ctVal") ? instrument.get("ctVal").getAsString() : "1"; // Значение контракта, для спот = 1

                InstrumentInfo instrumentInfo = new InstrumentInfo(
                        symbol,
                        new BigDecimal(lotSizeStr),
                        new BigDecimal(minSizeStr),
                        new BigDecimal(minCcyAmtStr),
                        new BigDecimal(minNotionalStr),
                        new BigDecimal(ctValStr)
                );

                // Кэшируем информацию
                instrumentInfoCache.put(symbol, instrumentInfo);
                log.debug("🔍 Кэшировал информацию о торговом инструменте {}: lot size = {}, min size = {}, minCcyAmt = {}, minNotional = {}, ctVal = {}",
                        symbol, instrumentInfo.getLotSize(), instrumentInfo.getMinSize(), instrumentInfo.getMinCcyAmt(), instrumentInfo.getMinNotional(), instrumentInfo.getCtVal());

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
    @ToString
    private static class InstrumentInfo {
        private final String symbol;
        private final BigDecimal lotSize;
        private final BigDecimal minSize;
        private final BigDecimal minCcyAmt; // Минимальная сумма в валюте котировки
        private final BigDecimal minNotional; // Минимальная условная стоимость
        private final BigDecimal ctVal; // Размер контракта

        public InstrumentInfo(String symbol, BigDecimal lotSize, BigDecimal minSize, BigDecimal minCcyAmt, BigDecimal minNotional, BigDecimal ctVal) {
            this.symbol = symbol;
            this.lotSize = lotSize;
            this.minSize = minSize;
            this.minCcyAmt = minCcyAmt;
            this.minNotional = minNotional;
            this.ctVal = ctVal;
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

        public BigDecimal getCtVal() {
            return ctVal;
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
     * Получение реальной информации о позиции с OKX API по символу
     */
    private JsonObject getRealPositionFromOkx(String symbol) {
        log.info("==> getRealPositionFromOkx: Запрос реальной позиции для {}", symbol);
        try {
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Запрос позиции заблокирован из-за геолокации!");
                return null;
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_POSITIONS_ENDPOINT + "?instId=" + symbol;

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
                    log.error("❌ HTTP ошибка при запросе позиции {}: {}", symbol, response.code());
                    return null;
                }

                String responseBody = response.body().string();
                log.debug("📋 Ответ OKX API для позиции {}: {}", symbol, responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка OKX API при запросе позиции {}: {}", symbol, jsonResponse.get("msg").getAsString());
                    return null;
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.isEmpty()) {
                    log.info("📋 Позиция для {} не найдена (массив data пуст)", symbol);
                    return null;
                }

                // Возвращаем первую найденную позицию для данного символа
                JsonObject positionData = data.get(0).getAsJsonObject();
                log.info("✅ Получена реальная позиция для {}: {}", symbol, positionData);
                return positionData;

            }
        } catch (Exception e) {
            log.error("❌ Ошибка при запросе реальной позиции для {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Логирование реальных данных о позиции с OKX
     */
    private void logRealPositionData(String symbol, String operationType) {
        log.info("==> logRealPositionData: Логирование реальной позиции {} после {}", symbol, operationType);
        try {
            // Небольшая пауза, чтобы позиция появилась в системе OKX
            Thread.sleep(1000);

            JsonObject positionData = getRealPositionFromOkx(symbol);
            if (positionData == null) {
                log.warn("⚠️ Не удалось получить реальные данные о позиции для {}", symbol);
                return;
            }

            // Извлекаем ключевые данные о позиции с проверкой на null
            String instId = getJsonStringValue(positionData, "instId");
            String posSide = getJsonStringValue(positionData, "posSide");
            String pos = getJsonStringValue(positionData, "pos"); // Размер позиции в базовых единицах
            String posSize = getJsonStringValue(positionData, "posSize"); // Размер позиции (положительное число)
            String avgPx = getJsonStringValue(positionData, "avgPx"); // Средняя цена входа
            String markPx = getJsonStringValue(positionData, "markPx"); // Текущая марк-цена
            String notionalUsd = getJsonStringValue(positionData, "notionalUsd"); // Условная стоимость в USD
            String margin = getJsonStringValue(positionData, "margin"); // Используемая маржа
            String imr = getJsonStringValue(positionData, "imr"); // Начальная маржа
            String mmr = getJsonStringValue(positionData, "mmr"); // Поддерживающая маржа
            String upl = getJsonStringValue(positionData, "upl"); // Нереализованный PnL
            String uplRatio = getJsonStringValue(positionData, "uplRatio"); // Коэффициент PnL
            String lever = getJsonStringValue(positionData, "lever"); // Текущее плечо

            log.info("🔍 === РЕАЛЬНЫЕ ДАННЫЕ ПОЗИЦИИ OKX ===");
            log.info("🔍 Инструмент: {}", instId);
            log.info("🔍 Сторона позиции: {}", posSide);
            log.info("🔍 Размер позиции (базовые единицы): {} {}", pos, getBaseCurrency(symbol));
            log.info("🔍 Размер позиции (абсолютный): {} {}", posSize, getBaseCurrency(symbol));
            log.info("🔍 Средняя цена входа: {} USDT", avgPx);
            log.info("🔍 Текущая марк-цена: {} USDT", markPx);
            log.info("🔍 Условная стоимость: {} USD", notionalUsd);
            log.info("🔍 Используемая маржа: {} USDT", margin);
            log.info("🔍 Начальная маржа: {} USDT", imr);
            log.info("🔍 Поддерживающая маржа: {} USDT", mmr);
            log.info("🔍 Нереализованный PnL: {} USDT", upl);
            log.info("🔍 Коэффициент PnL: {}%", uplRatio);
            log.info("🔍 Плечо: {}x", lever);
            log.info("🔍 === КОНЕЦ РЕАЛЬНЫХ ДАННЫХ ===");

        } catch (Exception e) {
            log.error("❌ Ошибка при логировании реальных данных позиции для {}: {}", symbol, e.getMessage());
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
        log.info("==> validateOrderSize: НАЧАЛО для {} | adjustedAmount: {} | positionSize: {} | currentPrice: {}", symbol, adjustedAmount, positionSize, currentPrice);
        try {
            InstrumentInfo instrumentInfo = getInstrumentInfo(symbol);
            if (instrumentInfo == null) {
                log.error("Не удалось получить информацию о торговом инструменте для {}.", symbol);
                return "Не удалось получить информацию о торговом инструменте для проверки размера ордера.";
            }
            log.info("Получена информация об инструменте: {}", instrumentInfo);

            BigDecimal minCcyAmt = instrumentInfo.getMinCcyAmt();
            BigDecimal minNotional = instrumentInfo.getMinNotional();
            log.info("Минимальные требования: minCcyAmt={}, minNotional={}", minCcyAmt, minNotional); //todo здесь нули!

            // Проверка по minCcyAmt (минимальная сумма в валюте котировки, т.е. USDT)
            if (adjustedAmount.compareTo(minCcyAmt) < 0) {
                String errorMsg = String.format("Сумма маржи %.2f USDT меньше минимальной %.2f USDT.", adjustedAmount, minCcyAmt);
                log.warn("⚠️ {} для {}", errorMsg, symbol);
                return errorMsg;
            }

            // Проверка по minNotional (минимальная условная стоимость сделки)
            BigDecimal notionalValue = positionSize.multiply(currentPrice);
            log.info("Рассчитанная условная стоимость сделки: {}", notionalValue);
            if (notionalValue.compareTo(minNotional) < 0) {
                String errorMsg = String.format("Условная стоимость сделки %.2f USDT меньше минимальной %.2f USDT.", notionalValue, minNotional);
                log.warn("⚠️ {} для {}", errorMsg, symbol);
                return errorMsg;
            }

            log.info("<== validateOrderSize: КОНЕЦ (Успех) для {}", symbol);
            return null; // Валидация прошла успешно
        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при валидации размера ордера для {}: {}", symbol, e.getMessage(), e);
            return "Ошибка при валидации размера ордера: " + e.getMessage();
        }
    }

    /**
     * Безопасное извлечение строкового значения из JsonObject с защитой от null
     */
    private String getJsonStringValue(JsonObject jsonObject, String fieldName) {
        try {
            JsonElement element = jsonObject.get(fieldName);
            if (element != null && !element.isJsonNull()) {
                return element.getAsString();
            }
        } catch (Exception e) {
            log.debug("⚠️ Ошибка при извлечении поля '{}': {}", fieldName, e.getMessage());
        }
        return "N/A";
    }
}