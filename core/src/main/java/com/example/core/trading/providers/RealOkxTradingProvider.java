package com.example.core.trading.providers;

import com.example.core.client.OkxFeignClient;
import com.example.core.repositories.PositionRepository;
import com.example.core.trading.interfaces.TradingProvider;
import com.example.core.trading.interfaces.TradingProviderType;
import com.example.core.trading.services.GeolocationService;
import com.example.core.trading.services.OkxPortfolioManager;
import com.example.shared.dto.Portfolio;
import com.example.shared.dto.TradeResult;
import com.example.shared.dto.okx.OkxPositionHistoryData;
import com.example.shared.dto.okx.OkxTickerDto;
import com.example.shared.enums.PositionStatus;
import com.example.shared.enums.PositionType;
import com.example.shared.enums.TradeOperationType;
import com.example.shared.models.Position;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.example.shared.utils.BigDecimalUtil.safeScale;

/**
 * Реальная торговля через OKX API
 * Выполняет настоящие торговые операции через OKX биржу
 * ВСЕ МЕТОДЫ ПОЛНОСТЬЮ СИНХРОННЫЕ!
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealOkxTradingProvider implements TradingProvider {
    private final OkxPortfolioManager okxPortfolioManager;
    private final GeolocationService geolocationService;
    private final PositionRepository positionRepository;
    private final OkxFeignClient okxFeignClient;

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


    // Кэш информации о торговых инструментах
    private final ConcurrentHashMap<String, InstrumentInfo> instrumentInfoCache = new ConcurrentHashMap<>();

    // Константы OKX API
    private static final String PROD_BASE_URL = "https://www.okx.com";
    private static final String SANDBOX_BASE_URL = "https://www.okx.com";
    private static final String TRADE_ORDER_ENDPOINT = "/api/v5/trade/order";
    private static final String TRADE_ORDERS_HISTORY_ENDPOINT = "/api/v5/trade/orders-history-archive";
    private static final String TRADE_POSITIONS_ENDPOINT = "/api/v5/account/positions";
    private static final String ACCOUNT_BALANCE_ENDPOINT = "/api/v5/account/balance";
    private static final String MARKET_TICKER_ENDPOINT = "/api/v5/market/ticker";
    private static final String PUBLIC_INSTRUMENTS_ENDPOINT = "/api/v5/public/instruments";
    private static final String ACCOUNT_CONFIG_ENDPOINT = "/api/v5/account/config";
    private static final String SET_LEVERAGE_ENDPOINT = "/api/v5/account/set-leverage";
    private static final String POSITIONS_HISTORY_ENDPOINT = "/api/v5/account/positions-history";

    @Override
    public Portfolio getPortfolio() {
        try {
            return okxPortfolioManager.getCurrentPortfolio();
        } catch (Exception e) {
            log.error("Ошибка при получении портфолио", e);
            return new Portfolio(); // или null, или дефолт, в зависимости от логики
        }
    }

    @Override
    public boolean hasAvailableBalance(BigDecimal amount) {
        try {
            return okxPortfolioManager.hasAvailableBalance(amount);
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке доступного баланса для суммы {}", amount, e);
            return false;
        }
    }

    @Override
    public TradeResult openLongPosition(Long tradingPairId, String symbol, BigDecimal amount, BigDecimal leverage) {
        return openPosition(tradingPairId, symbol, amount, leverage,
                TradeOperationType.OPEN_LONG,
                PositionType.LONG,
                "buy",
                "long");
    }

    private TradeResult logAndFail(String logMessage, TradeOperationType type, String symbol, String errorMessage) {
        log.error(logMessage);
        return TradeResult.failure(type, symbol, errorMessage);
    }

    private TradeResult logAndReturnError(String logMessage, TradeResult result) {
        log.error(logMessage);
        return result;
    }

    @Override
    public TradeResult openShortPosition(Long tradingPairId, String symbol, BigDecimal amount, BigDecimal leverage) {
        return openPosition(tradingPairId, symbol, amount, leverage,
                TradeOperationType.OPEN_SHORT,
                PositionType.SHORT,
                "sell",
                "short");
    }

    private TradeResult openPosition(
            Long tradingPairId,
            String symbol,
            BigDecimal amount,
            BigDecimal leverage,
            TradeOperationType operationType,
            PositionType positionType,
            String orderSide,
            String positionSide
    ) {
        log.info("==> {}: НАЧАЛО для {} | Сумма: ${} | Плечо: {} | tradingPairId: {}", operationType.name(), symbol, amount, leverage, tradingPairId);

        try {
            // 🔍 Предторговая проверка
            if (!preTradeChecks(amount)) {
                return logAndFail("Предторговая проверка не пройдена.", operationType, symbol, "Ошибка предторговой проверки");
            }

            // 📐 Расчёт размера позиции
            BigDecimal positionSize = calculateAndAdjustPositionSize(symbol, amount, leverage);
            if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
                return logAndFail("Размер позиции после корректировки равен нулю или меньше.",
                        operationType, symbol, "Размер позиции слишком мал");
            }
            log.info("Рассчитан и скорректирован размер позиции: {}", positionSize);

            // 💰 Получение цены
            BigDecimal currentPrice = getCurrentPrice(symbol);
            if (currentPrice == null) {
                return logAndFail("Не удалось получить текущую цену", operationType, symbol, "Не удалось получить цену");
            }

            // 💹 Скорректированная сумма = размер * цена
            BigDecimal adjustedAmount = positionSize.multiply(currentPrice);
            log.info("📊 {} {}: Исходная сумма: ${}, Скорректированная: ${}, Размер: {} единиц, Текущая цена: {}",
                    symbol, positionSide.toUpperCase(), amount, adjustedAmount, positionSize, currentPrice);

            // ✅ Валидация размера ордера
            String validationError = validateOrderSize(symbol, adjustedAmount, positionSize, currentPrice);
            if (validationError != null) {
                return logAndFail("Ошибка валидации размера ордера: " + validationError, operationType, symbol, validationError);
            }

            // ⚙️ Установка плеча
            if (!setLeverage(symbol, leverage)) {
                log.warn("⚠️ Не удалось установить плечо {}, продолжаем с текущим плечом", leverage);
            }

            // 📦 Размещение ордера
            TradeResult orderResult = placeOrder(symbol, orderSide, positionSide, adjustedAmount, leverage);
            if (!orderResult.isSuccess()) {
                return logAndReturnError("Ошибка размещения ордера: " + orderResult.getErrorMessage(), orderResult);
            }

            // 📍 Получение реального positionId из OKX
            String realPositionId = null;
            try {
                // Небольшая задержка чтобы позиция успела появиться в системе OKX
                Thread.sleep(1000);

                JsonObject realPosition = getRealPositionFromOkx(symbol, orderResult);
                if (realPosition != null && realPosition.has("posId")) {
                    realPositionId = realPosition.get("posId").getAsString();
                    log.info("✅ Для {} получен реальный positionId от OKX: {}", symbol, realPositionId);
                } else {
                    log.warn("⚠️ Не удалось получить реальный positionId от OKX для {}, будет использован временный", symbol);
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при получении positionId от OKX: {}", e.getMessage(), e);
            }

            // 🧩 Создание позиции с реальным positionId
            Position position = createPositionFromTradeResult(tradingPairId, orderResult, positionType, amount, leverage, realPositionId);
            log.debug("Сохраняем Position (openPosition() - position) {}", position);
            position = positionRepository.save(position);
            okxPortfolioManager.onPositionOpened(position);
            log.info("Позиция создана и сохранена {}", position);

            log.info("✅ Открыта {} позиция на OKX: {} | Размер: {} | Цена: {} | OrderID: {} | TradingPairId: {} | PositionId: {}",
                    position.getType(),
                    position.getSymbol(),
                    position.getSize(),
                    position.getEntryPrice(),
                    position.getExternalOrderId(),
                    position.getTradingPairId(),
                    position.getPositionId());

            // 🧾 Логгирование данных позиции
            logRealPositionData(symbol, operationType.name(), orderResult);

            // 🆔 Подмена ID и добавление позиции в результат
            orderResult.setPositionId(position.getPositionId());
            orderResult.setPosition(position);

            log.info("<== {}: КОНЕЦ (Успех) для {}", operationType.name(), symbol);
            return orderResult;

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при открытии позиции {}: {}", symbol, e.getMessage(), e);
            return TradeResult.failure(operationType, symbol, e.getMessage());
        }
    }

    @Override
    public TradeResult closePosition(String positionId) {
        try {
            Optional<Position> positionOpt = positionRepository.findByPositionId(positionId);
            if (positionOpt.isEmpty()) {
                return failWithLog("Позиция не найдена: " + positionId, TradeOperationType.CLOSE_POSITION, "UNKNOWN");
            }
            Position position = positionOpt.get();

            if (position.getStatus() != PositionStatus.OPEN) {
                return failWithLog("Позиция не открыта: " + position.getStatus(), TradeOperationType.CLOSE_POSITION, position.getSymbol());
            }

            // 1. Отправляем ордер на закрытие и получаем результат
            TradeResult closeOrderResult = placeCloseOrder(position); //todo грубо тут проверять на null и все - этого достаточно! не берем детали ордера из closeOrderResult тк корректнее делать это из истории ордеров
            if (!closeOrderResult.isSuccess()) {
                return closeOrderResult;
            }

            // 2. Ждем, чтобы позиция появилась в истории OKX, затем получаем реальный P&L
            log.info("💤 Ожидаем 3 секунды, чтобы позиция {} появилась в истории OKX", position.getSymbol());
            try {
                Thread.sleep(3000); // Ждем 3 секунды
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ Прерван ожидание появления позиции в истории OKX");
            }

            OkxPositionHistoryData okxPositionHistoryData = getRealizedPnLFromOkx(position.getSymbol(), position.getPositionId());
            if (okxPositionHistoryData != null) {
                log.info("📊 Используем реальный P&L от OKX: realizedPnl={}, fee={}, fundingFee={}",
                        okxPositionHistoryData.getRealizedPnl(), okxPositionHistoryData.getFee(), okxPositionHistoryData.getFundingFee());

                // Используем точные данные от OKX
                position.setRealizedPnLUSDT(okxPositionHistoryData.getRealizedPnl()); //чистоган в USDT
                position.setRealizedPnLPercent(okxPositionHistoryData.getPnlRatio()); //процент (хз чистого или грязного pnl, может пофиг ваще)
                position.setClosingFees(okxPositionHistoryData.getFee().abs().subtract(position.getOpeningFees().abs())); //сами считаем openClose-open=close
                position.setOpenCloseFees(okxPositionHistoryData.getFee().abs()); // комиссия открытие+закрытие
                position.setFundingFees(okxPositionHistoryData.getFundingFee().abs()); //по доке - аккумулированный фандинг
                position.setClosingPrice(closeOrderResult.getExecutionPrice()); //из ордера
                position.setOpenCloseFundingFees(position.getOpenCloseFees().abs().add(position.getFundingFees().abs()));

                // Рассчитываем процентный P&L
//                if (position.getAllocatedAmount() != null && position.getAllocatedAmount().compareTo(BigDecimal.ZERO) > 0) { //todo уже не надо тк вся инфа есть с окх
//                    BigDecimal pnlPercent = okxPositionHistoryData.getRealizedPnl()
//                            .divide(position.getAllocatedAmount(), 4, RoundingMode.HALF_UP)
//                            .multiply(BigDecimal.valueOf(100));
//                    position.setRealizedPnLPercent(pnlPercent);
//                }

                // Сбрасываем нереализованный P&L
//                position.setUnrealizedPnLUSDT(BigDecimal.ZERO); //todo может оставлять и сделать софтделит для позиций???
//                position.setUnrealizedPnLPercent(BigDecimal.ZERO);

                log.info("✅ P&L обновлен реальными данными OKX: realizedPnL={} USDT ({}%)",
                        position.getRealizedPnLUSDT(), position.getRealizedPnLPercent());

            } else {
                log.warn("⚠️ Не удалось получить реальные данные P&L от OKX");
            }

            // 3. Обновляем статус и время последнего обновления
            position.setStatus(PositionStatus.CLOSED);
            position.setLastUpdated(LocalDateTime.now());

            // 4. Освобождаем средства и уведомляем портфолио
            okxPortfolioManager.releaseReservedBalance(position.getAllocatedAmount());

            okxPortfolioManager.onPositionClosed(position, position.getRealizedPnLUSDT()); //todo нахуа это все

            log.info("Комиссии после закрытия: openingFees={}, closingFees={}, fundingFee={}, total={}",
                    position.getOpeningFees(), position.getClosingFees(), position.getFundingFees(), position.getOpenCloseFundingFees());

            // 5. Формируем итоговый результат
            TradeResult finalResult = TradeResult.success(
                    positionId,
                    TradeOperationType.CLOSE_POSITION,
                    position.getSymbol(),
                    position.getRealizedPnLUSDT(),
                    position.getRealizedPnLPercent(),
                    position.getAllocatedAmount(),
                    position.getClosingPrice(),
                    position.getOpenCloseFundingFees(),
                    closeOrderResult.getExternalOrderId(),
                    position
            );

            log.info("⚫ Закрыта позиция на OKX: {} {} | Цена: {} | PnL: {} USDT ({} %) | OrderID: {}",
                    finalResult.getSymbol(),
                    position.getDirectionString(),
                    finalResult.getExecutionPrice(),
                    finalResult.getPnlUSDT(),
                    finalResult.getPnlPercent(),
                    finalResult.getExternalOrderId()
            );

            // Сохраняем обновленную позицию в БД
            log.debug("Сохраняем Position (closePosition() - position) {}", position);
            positionRepository.save(position);
            log.info("💾 Позиция {} сохранена в БД после закрытия", positionId);

            return finalResult;

        } catch (Exception e) {
            log.error("❌ Ошибка при закрытии позиции {}: ", positionId, e);
            return TradeResult.failure(TradeOperationType.CLOSE_POSITION, "UNKNOWN", e.getMessage());
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private TradeResult failWithLog(String message, TradeOperationType type, String symbol) {
        log.error(message);
        return TradeResult.failure(type, symbol, message);
    }

    @Override
    public Position getPosition(String positionId) {
        return positionRepository.findByPositionId(positionId).orElse(null);
    }

    @Override
    public void updatePositionPrices(List<String> tickers) {
        updatePositionsInternal(tickers);
    }

    private void updatePositionsInternal(List<String> tickers) {
        try {
            if (tickers == null) {
                log.debug("🔄 Обновление всех позиций: синхронизация с OKX");
                syncPositionsWithOkx();
            } else {
                log.debug("🔄 Обновление позиций для тикеров: {} (с синхронизацией OKX)", tickers);
                syncPositionsWithOkxForTickers(tickers);
            }

            okxPortfolioManager.updatePortfolioValue();
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении цен позиций: {}", e.getMessage(), e);
        }
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        try {
            // Получаем тикер через OkxFeignClient как DTO
            OkxTickerDto ticker = okxFeignClient.getTicker(symbol);
            if (ticker != null && ticker.getLast() != null) {
                return ticker.getLast();
            } else {
                log.warn("Получен пустой тикер для символа {}", symbol);
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении цены для {}: ", symbol, e);
            return null;
        }
    }

    @Override
    public BigDecimal calculateFees(BigDecimal amount, BigDecimal leverage) {
        // OKX комиссия для фьючерсов:
        // maker: 0.02% (0.0002), taker: 0.05% (0.0005)
        // Для рыночных ордеров используем taker комиссию
        final BigDecimal takerFeeRate = new BigDecimal("0.0005"); // 0.05%

        // Комиссия считается от позиции с учетом плеча
        return amount.multiply(leverage).multiply(takerFeeRate);
    }


    @Override
    public TradingProviderType getProviderType() {
        return TradingProviderType.REAL_OKX;
    }

    @Override
    public boolean isConnected() {
        try {
            if (isAnyApiKeyMissing()) {
                log.warn("⚠️ OKX API ключи не настроены");
                return false;
            }
            return checkApiConnection();
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке подключения к OKX: ", e);
            return false;
        }
    }

    private boolean isAnyApiKeyMissing() {
        return isNullOrEmpty(apiKey) || isNullOrEmpty(apiSecret) || isNullOrEmpty(passphrase);
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    @Override
    public List<TradeResult> getTradeHistory(int limit) {
        // TODO: Реализовать через БД или убрать метод
        return List.of();
    }

    @Override
    public void loadPositions(List<Position> positionsToLoad) {
        // Позиции уже в БД, ничего делать не нужно
        log.debug("Позиции уже хранятся в БД, loadPositions пропущен");
    }


    // Приватные методы для работы с OKX API

    private TradeResult placeOrder(String symbol, String side, String posSide, BigDecimal size, BigDecimal leverage) { //todo возвращать OrderResult
        log.info("==> placeOrder: НАЧАЛО для {} | side: {} | posSide: {} | size: {} | leverage: {}", symbol, side, posSide, size, leverage);
        TradeOperationType tradeOperationType = posSide.equalsIgnoreCase("long") ? TradeOperationType.OPEN_LONG : TradeOperationType.OPEN_SHORT;

        try {
            // Проверка геолокации
            if (!geolocationService.isGeolocationAllowed()) {
                log.info("❌ БЛОКИРОВКА: Размещение ордера заблокировано из-за геолокации!");
                return TradeResult.failure(tradeOperationType, symbol, "Геолокация не разрешена");
            }
            log.info("Проверка геолокации пройдена.");

            // Базовый URL и endpoint
            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_ORDER_ENDPOINT;

            // Корректируем posSide
            String correctPosSide = determinePosSide(posSide);
            log.info("Определен correctPosSide: {}", correctPosSide);

            // Получаем информацию об инструменте
            InstrumentInfo instrumentInfo = getInstrumentInfo(symbol);
            if (instrumentInfo == null) {
                log.error("❌ Не удалось получить информацию об инструменте {}", symbol);
                return TradeResult.failure(tradeOperationType, symbol, "Не удалось получить информацию об инструменте");
            }
            log.info("📋 Информация об инструменте {}: {}", symbol, instrumentInfo);

            // Параметры lot size и min size
            BigDecimal lotSize = instrumentInfo.getLotSize();
            BigDecimal minSize = instrumentInfo.getMinSize();
            log.info("📋 Lot Size: {}, Min Size: {}", lotSize, minSize);

            // Получаем текущую цену
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

            // Рассчитываем условную стоимость и требуемую маржу
            BigDecimal notionalValue = adjustedSize.multiply(currentPrice);
            BigDecimal requiredMargin = notionalValue.divide(leverage, 2, RoundingMode.HALF_UP);
            log.debug("🔍 Условная стоимость: {} USD, требуемая маржа: {} USDT (с плечом {}x)", notionalValue, requiredMargin, leverage);

            // Формируем тело запроса
            JsonObject orderData = new JsonObject();
            orderData.addProperty("instId", symbol);
            orderData.addProperty("tdMode", "isolated");
            orderData.addProperty("side", side);
            orderData.addProperty("posSide", correctPosSide);
            orderData.addProperty("ordType", "market");
            orderData.addProperty("sz", adjustedSize.toPlainString());
            orderData.addProperty("lever", leverage.toPlainString());

            log.info("📋 Тело запроса для создания ордера OKX: {}", orderData.toString());

            // Формируем HTTP запрос
            RequestBody body = RequestBody.create(orderData.toString(), MediaType.get("application/json"));
            String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
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
                log.debug("Получен ответ от OKX API: HTTP {} | {}", response.code(), responseBody);
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка при создании ордера: {}", responseBody);
                    return TradeResult.failure(tradeOperationType, symbol, jsonResponse.get("msg").getAsString());
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() > 0) {
                    String orderId = data.get(0).getAsJsonObject().get("ordId").getAsString();
                    log.info("Ордер успешно размещен. OrderID: {}. Получаем детали ордера...", orderId);
                    return getOrderDetails(orderId, symbol, tradeOperationType); //todo создать OrderResult
                }
                log.error("Не удалось получить ID ордера из ответа API.");
                return TradeResult.failure(tradeOperationType, symbol, "Не удалось получить ID ордера");
            }
        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при создании ордера: ", e);
            return TradeResult.failure(tradeOperationType, symbol, e.getMessage());
        }
    }


    private boolean preTradeChecks(BigDecimal amount) {
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
            log.error("❌ Не удалось получить текущую цену для {}", symbol);
            return BigDecimal.ZERO;
        }

        InstrumentInfo instrumentInfo = getInstrumentInfo(symbol);
        if (instrumentInfo == null) {
            log.error("❌ Не удалось получить информацию об инструменте {}", symbol);
            return BigDecimal.ZERO;
        }

        BigDecimal ctVal = instrumentInfo.getCtVal();
        BigDecimal minSize = instrumentInfo.getMinSize();
        log.debug("📋 Информация для расчета {}: ctVal={}, цена={}, minSize={}", symbol, ctVal, currentPrice, minSize);

        // Стоимость минимального лота с учетом плеча
        BigDecimal minLotCost = minSize.multiply(ctVal).multiply(currentPrice)
                .divide(leverage, 2, RoundingMode.HALF_UP);
        log.debug("💰 Стоимость минимального лота: {} контрактов × {} ctVal × {} цена ÷ {} плечо = {} USDT",
                minSize, ctVal, currentPrice, leverage, minLotCost);

        if (minLotCost.compareTo(amount) > 0) {
            log.error("❌ БЛОКИРОВКА: Минимальный лот стоит {} USDT, а доступно только {} USDT",
                    minLotCost, amount);
            return BigDecimal.ZERO; // Недостаточно средств для минимального лота
        }

        // Максимальное количество контрактов с учетом бюджета и плеча
        BigDecimal maxContracts = amount.multiply(leverage)
                .divide(ctVal.multiply(currentPrice), 8, RoundingMode.DOWN);
        log.debug("🔢 Максимально доступно контрактов в рамках бюджета {} USDT: {}", amount, maxContracts);

        // Корректируем размер позиции под лот и бюджет
        return adjustPositionSizeToLotSizeWithBudgetLimit(symbol, maxContracts, amount, leverage);
    }

    private Position createPositionFromTradeResult(Long tradingPairId, TradeResult tradeResult, PositionType type, BigDecimal amount, BigDecimal leverage, String realPositionId) {
        //todo здесь создавать position не по OrderResult а брать инфу из открытых позиций okx/api/positions!


        // positionId - используем реальный ID от OKX если получен, иначе fallback логика
        String okxPositionId = realPositionId;

        if (okxPositionId == null || okxPositionId.isEmpty() || okxPositionId.equals("N/A")) {
            // Пытаемся получить ID из TradeResult (может быть в detalях ордера)
            okxPositionId = tradeResult.getPositionId();

            // Последний fallback: временный ID
            if (okxPositionId == null || okxPositionId.isEmpty() || okxPositionId.equals("N/A")) {
                okxPositionId = "temp_" + UUID.randomUUID().toString().substring(0, 8);
                log.info("⚠️ OKX не вернул positionId для {}, используем временный: {}.",
                        tradeResult.getSymbol(), okxPositionId);
            }
        }

        //todo здесь потенциальная проблема создания дублей position после усреднения - когда делаем усреднение здесь надо проверять есть ли уже позиция с этим positionId - если есть - обновлять, если нет - создавать новую запись!
        Position position;
        Optional<Position> existingPosition = positionRepository.findByPositionId(realPositionId);
        if (existingPosition.isPresent()) {
            // Обновляем существующую
            position = existingPosition.get();
            // обновляем поля...
            log.info("Обновляем существующую позицию symbol: {}, type: {}, positionId: {}", position.getSymbol(), position.getType(), realPositionId);
            position.setPositionId(okxPositionId);
            position.setSymbol(tradeResult.getSymbol());
            position.setType(type);
            position.setSize(tradeResult.getExecutedSize());
            position.setEntryPrice(tradeResult.getExecutionPrice());
            position.setCurrentPrice(tradeResult.getExecutionPrice()); // Изначально currentPrice = entryPrice
            position.setLeverage(leverage);
            position.setAllocatedAmount(amount);
            position.setOpeningFees(tradeResult.getFees().abs());
            position.setStatus(PositionStatus.OPEN);
            position.setOpenTime(LocalDateTime.now());
            position.setLastUpdated(LocalDateTime.now());
            position.setExternalOrderId(tradeResult.getExternalOrderId());
            position.setTradingPairId(tradingPairId);
        } else {
            // Создаем новую
            position = new Position();
            log.info("Создаем новую позицию symbol: {}, type: {}, positionId: {}", position.getSymbol(), position.getType(), realPositionId);
            position.setPositionId(okxPositionId);
            position.setSymbol(tradeResult.getSymbol());
            position.setType(type);
            position.setSize(tradeResult.getExecutedSize());
            position.setEntryPrice(tradeResult.getExecutionPrice());
            position.setCurrentPrice(tradeResult.getExecutionPrice()); // Изначально currentPrice = entryPrice
            position.setLeverage(leverage);
            position.setAllocatedAmount(amount);
            position.setOpeningFees(tradeResult.getFees().abs());
            position.setStatus(PositionStatus.OPEN);
            position.setOpenTime(LocalDateTime.now());
            position.setLastUpdated(LocalDateTime.now());
            position.setExternalOrderId(tradeResult.getExternalOrderId());
            position.setTradingPairId(tradingPairId);
        }

        return position;

//        return Position.builder()
//                .positionId(okxPositionId)  // Реальный ID от OKX или временный
//                .symbol(tradeResult.getSymbol())
//                .type(type)
//                .size(tradeResult.getExecutedSize())      // Используем реально исполненный размер
//                .entryPrice(tradeResult.getExecutionPrice())
//                .currentPrice(tradeResult.getExecutionPrice()) // Изначально currentPrice = entryPrice
//                .leverage(leverage)
//                .allocatedAmount(amount)
//                .openingFees(tradeResult.getFees().abs())
//                .status(PositionStatus.OPEN)
//                .openTime(LocalDateTime.now())
//                .lastUpdated(LocalDateTime.now())
//                .externalOrderId(tradeResult.getExternalOrderId())
//                .tradingPairId(tradingPairId)
//                .build();
    }


    private TradeResult placeCloseOrder(Position position) {
        try {
            // Проверяем геолокацию
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Закрытие ордера заблокировано из-за геолокации!");
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(), "Геолокация не разрешена");
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_ORDER_ENDPOINT;

            // Определяем сторону закрытия ордера
            String side = position.getType() == PositionType.LONG ? "sell" : "buy";

            // Определяем корректное значение posSide в зависимости от режима хеджирования
            String correctPosSide = isHedgeMode()
                    ? (side.equals("buy") ? "short" : "long")
                    : "net";

            // Рассчитываем общую стоимость позиции (размер * текущая цена)
            BigDecimal totalOrderValue = position.getSize().multiply(position.getCurrentPrice());

            // Формируем тело запроса для закрытия позиции
            JsonObject orderData = new JsonObject();
            orderData.addProperty("instId", position.getSymbol());
            orderData.addProperty("tdMode", "isolated");
            orderData.addProperty("side", side);
            orderData.addProperty("posSide", correctPosSide);
            orderData.addProperty("ordType", "market");
            orderData.addProperty("sz", position.getSize().toPlainString());   // Количество базового актива
            orderData.addProperty("szCcy", getBaseCurrency(position.getSymbol())); // Указываем, что sz в базовой валюте

            RequestBody body = RequestBody.create(orderData.toString(), MediaType.get("application/json"));
            String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
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
                log.info("Ответ OKX на закрытие ордера: HTTP {} | {}", response.code(), responseBody);
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка при создании ордера на закрытие: {}", responseBody);
                    return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(), jsonResponse.get("msg").getAsString());
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() > 0) {
                    String orderId = data.get(0).getAsJsonObject().get("ordId").getAsString();
                    log.info("Ордер на закрытие успешно размещен. OrderID: {}. Получаем детали ордера...", orderId);
                    TradeResult orderDetails = getOrderDetails(orderId, position.getSymbol(), TradeOperationType.CLOSE_POSITION);
                    log.info("Получены детали ордера на закрытие: symbol={} | orderId={} | pnlUSDT={} | size={} | avgPx={} | fee={}",
                            orderDetails.getSymbol(),
                            orderDetails.getExternalOrderId(),
                            orderDetails.getPnlUSDT(),
                            orderDetails.getSize(),
                            orderDetails.getExecutionPrice(),
                            orderDetails.getFees());

                    return orderDetails;
                }

                log.error("Не удалось получить ID ордера из ответа API при закрытии позиции.");
                return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(), "Не удалось получить ID ордера");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при закрытии ордера: ", e);
            return TradeResult.failure(TradeOperationType.CLOSE_POSITION, position.getSymbol(), e.getMessage());
        }
    }

    private TradeResult getOrderDetails(String orderId, String symbol, TradeOperationType tradeOperationType) {
        log.debug("==> getOrderDetails: НАЧАЛО для orderId={} | symbol={} | operation={}", orderId, symbol, tradeOperationType);

        try {
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Получение деталей ордера {} заблокировано из-за геолокации!", orderId);
                return TradeResult.failure(tradeOperationType, symbol, "Геолокация не разрешена");
            }
            log.debug("Проверка геолокации пройдена.");

            final int sleepMillis = 2000;
            log.debug("Ожидаем {} мс, чтобы ордер {} успел исполниться...", sleepMillis, orderId);
            Thread.sleep(sleepMillis);

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = "/api/v5/trade/order?instId=" + symbol + "&ordId=" + orderId;
            log.debug("Формируем запрос к OKX API: GET {}", baseUrl + endpoint);

            String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
            String signature = generateSignature("GET", endpoint, "", timestamp);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .addHeader("OK-ACCESS-KEY", apiKey)
                    .addHeader("OK-ACCESS-SIGN", signature)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                    .build();

            log.debug("Отправка запроса на получение деталей ордера {}...", orderId);
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                log.debug("Ответ от OKX API для ордера {}: HTTP {} | {}", orderId, response.code(), responseBody);
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    String errorMsg = jsonResponse.get("msg").getAsString();
                    log.error("❌ Ошибка при получении деталей ордера {}: {} (Код: {})", orderId, errorMsg, jsonResponse.get("code").getAsString());
                    return TradeResult.failure(tradeOperationType, symbol, "Не удалось получить детали ордера: " + errorMsg);
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() > 0) {
                    JsonObject orderInfo = data.get(0).getAsJsonObject();
                    log.info("🔍 ПОЛНАЯ ИНФОРМАЦИЯ ПО ОРДЕРУ {}: {}", orderId, orderInfo);

                    // Детализированное логирование всех полей ответа с описанием (на основе реального ответа OKX)
                    log.info("📊 === ДЕТАЛИ ОРДЕРА {} ===", orderId);
                    log.info("🔹 instId              : {} (ID торгового инструмента)", getJsonStringValue(orderInfo, "instId")); //ETHW-USDT-SWAP
                    log.info("🔹 instType            : {} (Тип инструмента)", getJsonStringValue(orderInfo, "instType")); //SWAP
                    log.info("🔹 ordId               : {} (ID ордера)", getJsonStringValue(orderInfo, "ordId")); //2807201839622594560
                    log.info("🔹 clOrdId             : {} (Пользовательский ID ордера)", getJsonStringValue(orderInfo, "clOrdId"));
                    log.info("🔹 tag                 : {} (Тег ордера)", getJsonStringValue(orderInfo, "tag"));
                    log.info("🔹 px                  : {} (Цена ордера)", getJsonStringValue(orderInfo, "px"));
                    log.info("🔹 pxUsd               : {} (Цена ордера в USD)", getJsonStringValue(orderInfo, "pxUsd"));
                    log.info("🔹 pxVol               : {} (Цена для волатильности)", getJsonStringValue(orderInfo, "pxVol"));
                    log.info("🔹 pxType              : {} (Тип цены)", getJsonStringValue(orderInfo, "pxType"));
                    log.info("🔹 sz                  : {} (Размер ордера)", getJsonStringValue(orderInfo, "sz")); //194
                    log.info("🔹 pnl                 : {} USDT (Прибыль/убыток по позиции)", getJsonStringValue(orderInfo, "pnl")); //-0.2328
                    log.info("🔹 ordType             : {} (Тип ордера: market/limit)", getJsonStringValue(orderInfo, "ordType")); //market
                    log.info("🔹 side                : {} (Сторона: buy/sell)", getJsonStringValue(orderInfo, "side")); //buy
                    log.info("🔹 posSide             : {} (Сторона позиции: long/short/net)", getJsonStringValue(orderInfo, "posSide")); //net
                    log.info("🔹 tdMode              : {} (Торговый режим: isolated/cross)", getJsonStringValue(orderInfo, "tdMode")); //isolated
                    log.info("🔹 accFillSz           : {} (Накопленный исполненный размер)", getJsonStringValue(orderInfo, "accFillSz")); //194
                    log.info("🔹 avgPx               : {} (Средняя цена исполнения)", getJsonStringValue(orderInfo, "avgPx")); //1.617
                    log.info("🔹 fillPx              : {} (Последняя цена исполнения)", getJsonStringValue(orderInfo, "fillPx")); //1.617
                    log.info("🔹 fillSz              : {} (Последний размер исполнения)", getJsonStringValue(orderInfo, "fillSz")); //194
                    log.info("🔹 fillTime            : {} (Время последнего исполнения)", getJsonStringValue(orderInfo, "fillTime")); //1756163534232
                    log.info("🔹 tradeId             : {} (ID последней сделки)", getJsonStringValue(orderInfo, "tradeId")); //109251869
                    log.info("🔹 state               : {} (Статус ордера)", getJsonStringValue(orderInfo, "state")); //filled
                    log.info("🔹 lever               : {} (Плечо)", getJsonStringValue(orderInfo, "lever")); //2
                    log.info("🔹 fee                 : {} (Комиссия)", getJsonStringValue(orderInfo, "fee")); //-0.0156849
                    log.info("🔹 feeCcy              : {} (Валюта комиссии)", getJsonStringValue(orderInfo, "feeCcy")); //USDT
                    log.info("🔹 rebate              : {} (Рибейт)", getJsonStringValue(orderInfo, "rebate")); //0
                    log.info("🔹 rebateCcy           : {} (Валюта рибейта)", getJsonStringValue(orderInfo, "rebateCcy")); //USDT
                    log.info("🔹 ccy                 : {} (Валюта маржи)", getJsonStringValue(orderInfo, "ccy"));
                    log.info("🔹 category            : {} (Категория ордера)", getJsonStringValue(orderInfo, "category")); //normal
                    log.info("🔹 cTime               : {} (Время создания)", getJsonStringValue(orderInfo, "cTime")); //1756163534232
                    log.info("🔹 uTime               : {} (Время обновления)", getJsonStringValue(orderInfo, "uTime")); //1756163534233
                    log.info("🔹 reduceOnly          : {} (Только закрытие)", getJsonStringValue(orderInfo, "reduceOnly")); //false
                    log.info("🔹 quickMgnType        : {} (Тип быстрой маржи)", getJsonStringValue(orderInfo, "quickMgnType"));
                    log.info("🔹 algoId              : {} (ID алгоритма)", getJsonStringValue(orderInfo, "algoId"));
                    log.info("🔹 algoClOrdId         : {} (Алгоритмический пользовательский ID)", getJsonStringValue(orderInfo, "algoClOrdId"));
                    log.info("🔹 attachAlgoClOrdId   : {} (Прикрепленный алго ID)", getJsonStringValue(orderInfo, "attachAlgoClOrdId"));
                    log.info("🔹 tpTriggerPx         : {} (Цена срабатывания тейк-профита)", getJsonStringValue(orderInfo, "tpTriggerPx"));
                    log.info("🔹 tpTriggerPxType     : {} (Тип цены тейк-профита)", getJsonStringValue(orderInfo, "tpTriggerPxType"));
                    log.info("🔹 tpOrdPx             : {} (Цена ордера тейк-профита)", getJsonStringValue(orderInfo, "tpOrdPx"));
                    log.info("🔹 slTriggerPx         : {} (Цена срабатывания стоп-лосса)", getJsonStringValue(orderInfo, "slTriggerPx"));
                    log.info("🔹 slTriggerPxType     : {} (Тип цены стоп-лосса)", getJsonStringValue(orderInfo, "slTriggerPxType"));
                    log.info("🔹 slOrdPx             : {} (Цена ордера стоп-лосса)", getJsonStringValue(orderInfo, "slOrdPx"));
                    log.info("🔹 source              : {} (Источник)", getJsonStringValue(orderInfo, "source"));
                    log.info("🔹 cancelSource        : {} (Источник отмены)", getJsonStringValue(orderInfo, "cancelSource"));
                    log.info("🔹 cancelSourceReason  : {} (Причина отмены)", getJsonStringValue(orderInfo, "cancelSourceReason"));
                    log.info("🔹 isTpLimit           : {} (Является ли тейк-профит лимитным)", getJsonStringValue(orderInfo, "isTpLimit")); //false
                    log.info("🔹 stpId               : {} (ID самоторговли)", getJsonStringValue(orderInfo, "stpId"));
                    log.info("🔹 stpMode             : {} (Режим самоторговли)", getJsonStringValue(orderInfo, "stpMode")); //cancel_maker
                    log.info("🔹 tgtCcy              : {} (Целевая валюта)", getJsonStringValue(orderInfo, "tgtCcy"));
                    log.info("🔹 tradeQuoteCcy       : {} (Валюта котировки торговли)", getJsonStringValue(orderInfo, "tradeQuoteCcy"));
                    log.info("📊 === КОНЕЦ ДЕТАЛЕЙ ОРДЕРА ===");

                    /*
                    avgPx
                    Средняя цена заполнения. Если не заполнено ни одной, возвращается "".
                     */
                    BigDecimal avgPx = new BigDecimal(orderInfo.get("avgPx").getAsString());

                    /*
                    fee
                    Комиссия и возврат
                    Для спот и маржи - это накопленная комиссия, взимаемая платформой. Она всегда отрицательна, например, -0,01.
                    Для фьючерсов с экспирацией, бессрочных фьючерсов и опционов это накопленная комиссия и рибейт
                     */
                    BigDecimal fee = new BigDecimal(orderInfo.get("fee").getAsString()).abs();

                    /*
                    accFillSz
                    Накопленное количество заполнения
                    Единица измерения - base_ccy для SPOT и MARGIN, например, для BTC-USDT единица измерения - BTC; для рыночных ордеров единица измерения - base_ccy, если tgtCcy - base_ccy или quote_ccy;
                    Единица измерения - контракт для FUTURES/SWAP/OPTION.
                     */
                    BigDecimal size = new BigDecimal(orderInfo.get("accFillSz").getAsString());

                    /*
                    pnl
                    Прибыль и убыток, применяется к ордерам, которые имеют сделку и нацелены на закрытие позиции.
                    В других условиях всегда равен 0.
                     */
                    BigDecimal pnlUSDT = new BigDecimal(orderInfo.get("pnl").getAsString());

                    log.debug("✅ Детали ордера успешно извлечены: orderId={} | symbol={} | pnlUSDT={} | size={} | avgPx={} | fee={}",
                            orderId, symbol, pnlUSDT, size, avgPx, fee);

                    // TODO: сверить запрошенный и исполненный объем, при несовпадении вернуть failure

                    //todo по моему не правильно данные ордера пихать в TradeResult!!! нужно брать факт из истории если это было закрытие сделки
                    TradeResult result = TradeResult.success(null, tradeOperationType, symbol, pnlUSDT, null, size, avgPx, fee, orderId, null);
                    log.debug("<== getOrderDetails: КОНЕЦ (Успех) для orderId={}. Результат: {}", orderId, result);
                    return result;
                }

                log.error("❌ Детали ордера {} не найдены в ответе API (пустой массив 'data').", orderId);
                return TradeResult.failure(tradeOperationType, symbol, "Детали ордера не найдены");
            }
        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при получении деталей ордера {}: {}", orderId, e.getMessage(), e);
            return TradeResult.failure(tradeOperationType, symbol, e.getMessage());
        }
    }

    private boolean checkApiConnection() {
        try {
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Проверка API соединения заблокирована из-за геолокации!");
                return false;
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = ACCOUNT_BALANCE_ENDPOINT;

            String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
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
                log.debug("OKX API запрос: {} {}", baseUrl + endpoint, timestamp);
                log.debug("OKX API ответ: {}", responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                return "0".equals(jsonResponse.get("code").getAsString());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке подключения к OKX API: {}", e.getMessage(), e);
            return false;
        }
    }

    private void syncPositionsWithOkx() {
        syncPositionsWithOkxInternal(null);
    }

    /**
     * Синхронизация позиций с OKX, если передан список тикеров — фильтруем по ним,
     * иначе обновляем все позиции.
     */
    private void syncPositionsWithOkxForTickers(List<String> tickers) {
        syncPositionsWithOkxInternal(tickers);
    }

    private void syncPositionsWithOkxInternal(List<String> tickers) {
        try {
            if (!geolocationService.isGeolocationAllowed()) {
                log.error("❌ БЛОКИРОВКА: Синхронизация позиций заблокирована из-за геолокации!");
                return;
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = TRADE_POSITIONS_ENDPOINT;

            String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
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
                if (tickers == null) {
                    log.debug("🔄 Синхронизация позиций с OKX: {}", responseBody);
                } else {
                    log.debug("🔄 Синхронизация позиций с OKX для тикеров {}: {}", tickers, responseBody);
                }

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    String msg = jsonResponse.get("msg").getAsString();
                    if (tickers == null) {
                        log.error("❌ Ошибка OKX API при синхронизации позиций: {}", msg);
                    } else {
                        log.error("❌ Ошибка OKX API при синхронизации позиций для тикеров {}: {}", tickers, msg);
                    }
                    return;
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (tickers == null) {
                    log.info("📊 Получено {} позиций с OKX для синхронизации", data.size());
                    for (JsonElement positionElement : data) {
                        updatePositionFromOkxData(positionElement.getAsJsonObject());
                    }
                } else {
                    log.debug("📊 Получено {} позиций с OKX, фильтруем по тикерам {}", data.size(), tickers);
                    for (JsonElement positionElement : data) {
                        JsonObject okxPosition = positionElement.getAsJsonObject();
                        String instId = getJsonStringValue(okxPosition, "instId");
                        if (tickers.contains(instId)) {
                            log.debug("🎯 Обновляем позицию для тикера {}", instId);
                            updatePositionFromOkxData(okxPosition);
                        } else {
                            log.debug("⏭️ Пропускаем позицию для тикера {} (не в списке для обновления)", instId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (tickers == null) {
                log.error("❌ Ошибка при синхронизации позиций с OKX: {}", e.getMessage(), e);
            } else {
                log.error("❌ Ошибка при синхронизации позиций с OKX для тикеров {}: {}", tickers, e.getMessage(), e);
            }
        }
    }

    private void updatePositionFromOkxData(JsonObject okxPosition) {
        try {
            // Извлекаем поля как строки
            String instId = getJsonStringValue(okxPosition, "instId");
            String instType = getJsonStringValue(okxPosition, "instType");
            String mgnMode = getJsonStringValue(okxPosition, "mgnMode");
            String posId = getJsonStringValue(okxPosition, "posId");
            String posSide = getJsonStringValue(okxPosition, "posSide");
            String pos = getJsonStringValue(okxPosition, "pos");
            String posCcy = getJsonStringValue(okxPosition, "posCcy");
            String avgPx = getJsonStringValue(okxPosition, "avgPx");
            String markPx = getJsonStringValue(okxPosition, "markPx");
            String upl = getJsonStringValue(okxPosition, "upl");
            String uplRatio = getJsonStringValue(okxPosition, "uplRatio");
            String realizedPnlUSDT = getJsonStringValue(okxPosition, "realizedPnl");
            String lever = getJsonStringValue(okxPosition, "lever");
            String margin = getJsonStringValue(okxPosition, "margin");
            String imr = getJsonStringValue(okxPosition, "imr");
            String mmr = getJsonStringValue(okxPosition, "mmr");
            String notionalUsd = getJsonStringValue(okxPosition, "notionalUsd");
            String interest = getJsonStringValue(okxPosition, "interest");
            String tradeId = getJsonStringValue(okxPosition, "tradeId");
            String cTime = getJsonStringValue(okxPosition, "cTime");
            String uTime = getJsonStringValue(okxPosition, "uTime");
            String ccy = getJsonStringValue(okxPosition, "ccy");
            String bePx = getJsonStringValue(okxPosition, "bePx");
            String fee = getJsonStringValue(okxPosition, "fee");
            String fundingFee = getJsonStringValue(okxPosition, "fundingFee");

            if ("N/A".equals(instId)) {
                log.debug("⚠️ Пропускаем позицию с пустым instId");
                return;
            }

            // ЛОГИРОВАНИЕ ВСЕХ ПОЛЕЙ С РУССКИМИ ПОДПИСЯМИ И ПОДОЗРИТЕЛЬНЫМИ ЗНАЧЕНИЯМИ
            log.debug("📊 === ПОЛНАЯ ИНФОРМАЦИЯ О ПОЗИЦИИ OKX ===");
            log.debug("🔹 instId         : {} (ID инструмента)", instId);
            log.debug("🔹 instType       : {} (тип инструмента)", instType);
            log.debug("🔹 mgnMode        : {} (режим маржи)", mgnMode);
            log.debug("🔹 posId          : {} (ID позиции)", posId);
            log.debug("🔹 posSide        : {} (направление позиции: long/short)", posSide);
            log.debug("🔹 pos            : {} {} (размер позиции)", pos, posCcy);
            log.debug("🔹 posCcy         : {} (валюта позиции)", posCcy);
            log.debug("🔹 ccy            : {} (базовая валюта)", ccy);
            log.debug("🔹 avgPx          : {} USDT (средняя цена входа)", avgPx);
            log.debug("🔹 markPx         : {} USDT (маркировочная цена)", markPx);
            log.debug("🔹 upl            : {} USDT (нереализованный PnL)", upl);
            log.debug("🔹 uplRatio       : {} % (PnL в процентах)", uplRatio);
            log.debug("🔹 realizedPnl    : {} USDT (реализованный PnL)", realizedPnlUSDT);
            log.debug("🔹 bePx           : {} USDT (цена безубыточности)", bePx);
            log.debug("🔹 lever          : {}x (плечо)", lever);
            log.debug("🔹 margin         : {} USDT (используемая маржа)", margin);
            log.debug("🔹 imr            : {} USDT (начальная маржа)", imr);
            log.debug("🔹 mmr            : {} USDT (поддерживающая маржа)", mmr);
            log.debug("🔹 notionalUsd    : {} USD (условная стоимость)", notionalUsd);
            log.debug("🔹 interest       : {} (проценты)", interest);
            log.debug("🔹 tradeId        : {} (ID сделки)", tradeId);
            log.debug("🔹 cTime          : {} (время открытия позиции)", cTime);
            log.debug("🔹 uTime          : {} (время последнего обновления)", uTime);
            log.debug("🔹 fee            : {} USDT (все комиссии по позиции)", fee);
            log.debug("🔹 fundingFee     : {} USDT (фандинг комиссия)", fundingFee);
            log.debug("📊 === КОНЕЦ ИНФОРМАЦИИ О ПОЗИЦИИ ===");

            BigDecimal scaledAvgPx = safeScale(avgPx, 8);
            BigDecimal scaledMarkPx = safeScale(markPx, 8);
            BigDecimal scaledUpl = safeScale(upl, 8);
            BigDecimal scaledUplRatio = safeScale(uplRatio, 8);
            BigDecimal scaledMargin = safeScale(margin, 8);

            BigDecimal scaledRealizedPnl = safeScale(realizedPnlUSDT, 8);
            BigDecimal scaledFee = safeScale(fee, 8);
            BigDecimal scaledFundingFee = safeScale(fundingFee, 8);
            // Ищем и обновляем внутреннюю позицию
            Position internalPosition = findPositionBySymbol(instId);
            if (internalPosition != null) {
                if (scaledMarkPx != null) {
                    internalPosition.setCurrentPrice(scaledMarkPx);
                }
                if (scaledUpl != null) {
                    internalPosition.setUnrealizedPnLUSDT(scaledUpl);
                }
                if (scaledUplRatio != null) {
                    internalPosition.setUnrealizedPnLPercent(scaledUplRatio.multiply(BigDecimal.valueOf(100))); // 0.02 -> 2
                }
                if (scaledRealizedPnl != null) {
                    internalPosition.setRealizedPnLUSDT(scaledRealizedPnl);
                }
                if (scaledAvgPx != null) {
                    internalPosition.setEntryPrice(scaledAvgPx);
                }
                if (!"N/A".equals(pos)) {
                    internalPosition.setSize(new BigDecimal(pos).abs());
                }
                if (scaledFee != null) {
                    internalPosition.setOpeningFees(scaledFee.abs());
                }
                if (scaledFundingFee != null) {
                    internalPosition.setFundingFees(scaledFundingFee.abs());
                }
                if (scaledMargin != null) {
                    internalPosition.setAllocatedAmount(scaledMargin);
                }

                internalPosition.setLastUpdated(LocalDateTime.now());

                // Сохраняем обновленную позицию в БД
                log.debug("Сохраняем Position (updatePositionFromOkxData() - internalPosition) {}", internalPosition);
                positionRepository.save(internalPosition);

                log.debug("✅ Обновлена позиция {}: posId={}, нереализованный PnL={} USDT ({} %), реализованный PnL={} USDT, цена={}, размер={}, маржа={}, комиссия={}, комиссия за фандинг={}",
                        instId,
                        posId,
                        internalPosition.getUnrealizedPnLUSDT(),
                        internalPosition.getUnrealizedPnLPercent(),
                        internalPosition.getRealizedPnLUSDT(),
                        internalPosition.getCurrentPrice(),
                        internalPosition.getSize(),
                        internalPosition.getAllocatedAmount(),
                        internalPosition.getOpeningFees(),
                        internalPosition.getFundingFees()
                );
            } else {
                log.debug("⚠️ Внутренняя позиция для {} не найдена, пропускаем", instId);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении позиции из данных OKX: {}", e.getMessage(), e);
        }
    }

    /**
     * Находит внутреннюю позицию по символу инструмента
     */
    private Position findPositionBySymbol(String symbol) {
        log.debug("🔍 findPositionBySymbol: Поиск открытой позиции для символа '{}' в БД", symbol);

        List<Position> openPositions = positionRepository.findAllByStatus(PositionStatus.OPEN);

        Position found = openPositions.stream()
                .filter(pos -> symbol.equals(pos.getSymbol()))
                .findFirst()
                .orElse(null);

        log.debug("🔍 findPositionBySymbol: Найденная позиция для '{}': {}", symbol, found != null ? found.getPositionId() : "НЕ НАЙДЕНА");

        return found;
    }

    /**
     * Корректировка размера позиции согласно lot size с учетом бюджетных ограничений.
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

            log.debug("📋 Корректировка позиции для {}: maxContracts={}, lotSize={}, minSize={}, ctVal={}",
                    symbol, maxContracts, lotSize, minSize, ctVal);

            // Рассчитываем максимально допустимый размер позиции, кратный lotSize
            BigDecimal adjustedSize = maxContracts.divide(lotSize, 0, RoundingMode.DOWN).multiply(lotSize);

            // Гарантируем, что размер не меньше минимального
            if (adjustedSize.compareTo(minSize) < 0) {
                adjustedSize = minSize;

                BigDecimal minLotCost = calculateCost(adjustedSize, ctVal, currentPrice, leverage);
                if (minLotCost.compareTo(userBudget) > 0) {
                    log.error("❌ Минимальный лот {} стоит {} USDT, превышает бюджет {} USDT",
                            adjustedSize, minLotCost, userBudget);
                    return BigDecimal.ZERO;
                }
            }

            BigDecimal finalCost = calculateCost(adjustedSize, ctVal, currentPrice, leverage);
            if (finalCost.compareTo(userBudget) > 0) {
                log.error("❌ Финальная стоимость {} USDT превышает бюджет {} USDT", finalCost, userBudget);
                return BigDecimal.ZERO;
            }

            log.debug("✅ Одобрен размер позиции {} контрактов (стоимость: {} USDT ≤ бюджет: {} USDT)",
                    adjustedSize, finalCost, userBudget);
            return adjustedSize;

        } catch (Exception e) {
            log.error("❌ Ошибка при корректировке размера позиции для {}: {}", symbol, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Вычисляет стоимость позиции с учетом ctVal, цены и плеча.
     */
    private BigDecimal calculateCost(BigDecimal size, BigDecimal ctVal, BigDecimal price, BigDecimal leverage) {
        return size.multiply(ctVal).multiply(price).divide(leverage, 2, RoundingMode.HALF_UP);
    }

    /**
     * Получение информации о торговом инструменте
     */
    private InstrumentInfo getInstrumentInfo(String symbol) {
        try {
            // Проверка кэша
            InstrumentInfo cachedInfo = instrumentInfoCache.get(symbol);
            if (cachedInfo != null) {
                log.debug("🔍 Использую кэшированную информацию о торговом инструменте {}", symbol);
                return cachedInfo;
            }

            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = PUBLIC_INSTRUMENTS_ENDPOINT + "?instType=SWAP&instId=" + symbol;
            String url = baseUrl + endpoint;

            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("❌ HTTP ошибка при запросе инструмента {}: код {}", symbol, response.code());
                    return null;
                }

                String responseBody = response.body().string();
                log.debug("🔍 Ответ на запрос инструмента {}: {}", symbol, responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка OKX API при запросе {}: {}", symbol, jsonResponse.get("msg").getAsString());
                    return null;
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.isEmpty()) {
                    log.warn("⚠️ Инструмент {} не найден в данных OKX", symbol);
                    return null;
                }

                JsonObject instrument = data.get(0).getAsJsonObject();
                InstrumentInfo info = new InstrumentInfo(
                        symbol,
                        parseBigDecimalSafe(instrument, "lotSz", "1"),
                        parseBigDecimalSafe(instrument, "minSz", "0"),
                        parseBigDecimalSafe(instrument, "minCcyAmt", "0"),
                        parseBigDecimalSafe(instrument, "minNotional", "0"),
                        parseBigDecimalSafe(instrument, "ctVal", "1")
                );

                instrumentInfoCache.put(symbol, info);
                log.debug("🔍 Кэширован {}: lotSize={}, minSize={}, minCcyAmt={}, minNotional={}, ctVal={}",
                        symbol, info.getLotSize(), info.getMinSize(), info.getMinCcyAmt(), info.getMinNotional(), info.getCtVal());

                return info;
            }

        } catch (Exception e) {
            log.error("❌ Исключение при получении информации о {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    private BigDecimal parseBigDecimalSafe(JsonObject json, String key, String defaultValue) {
        try {
            return json.has(key) ? new BigDecimal(json.get(key).getAsString()) : new BigDecimal(defaultValue);
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при разборе поля {}: {}", key, e.getMessage());
            return new BigDecimal(defaultValue);
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
        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Установка плеча заблокирована из-за геолокации!");
            return false;
        }

        String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
        String endpoint = SET_LEVERAGE_ENDPOINT;
        String fullUrl = baseUrl + endpoint;

        JsonObject payload = new JsonObject();
        payload.addProperty("instId", symbol);
        payload.addProperty("lever", leverage.toString());
        payload.addProperty("mgnMode", "isolated");

        String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
        String signature = generateSignature("POST", endpoint, payload.toString(), timestamp);

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(fullUrl)
                .post(body)
                .addHeader("OK-ACCESS-KEY", apiKey)
                .addHeader("OK-ACCESS-SIGN", signature)
                .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                .addHeader("Content-Type", "application/json")
                .build();

        log.debug("🔧 Установка плеча: symbol={}, leverage={}, URL={}", symbol, leverage, fullUrl);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.debug("🔧 Ответ от OKX ({}): {}", response.code(), responseBody);

            if (!response.isSuccessful()) {
                log.error("❌ HTTP ошибка: {}", response.code());
                return false;
            }

            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            if ("0".equals(jsonResponse.get("code").getAsString())) {
                log.debug("✅ Плечо {} успешно установлено для {}", leverage, symbol);
                return true;
            } else {
                log.error("❌ Ошибка OKX при установке плеча: {}", jsonResponse.get("msg").getAsString());
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при установке плеча для {}: {}", symbol, e.getMessage(), e);
            return false;
        }
    }


    /**
     * Возвращает корректное значение posSide в зависимости от режима аккаунта (Net или Hedge).
     *
     * @param intendedPosSide Желаемое направление позиции ("long" или "short") — используется только в режиме Hedge.
     * @return "net" для Net-режима, иначе возвращает переданный posSide.
     */
    private String determinePosSide(String intendedPosSide) {
        return isHedgeMode() ? intendedPosSide : "net";
    }

    /**
     * Проверка, активирован ли Hedge-режим (long_short_mode) для аккаунта на OKX.
     *
     * @return true, если включён Hedge-режим; false — если Net-режим или произошла ошибка.
     */
    private boolean isHedgeMode() {
        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Проверка режима позиций заблокирована из-за геолокации!");
            return false;
        }

        try {
            JsonObject jsonResponse = executeSignedGet(ACCOUNT_CONFIG_ENDPOINT);
            if (jsonResponse == null || !"0".equals(jsonResponse.get("code").getAsString())) {
                log.error("❌ Ошибка OKX API при получении конфигурации: {}",
                        jsonResponse != null ? jsonResponse.get("msg").getAsString() : "пустой ответ");
                return false;
            }

            JsonArray data = jsonResponse.getAsJsonArray("data");
            if (data.isEmpty()) {
                log.warn("⚠️ Данные конфигурации аккаунта пусты");
                return false;
            }

            String posMode = data.get(0).getAsJsonObject().get("posMode").getAsString();
            boolean isHedge = "long_short_mode".equals(posMode);
            log.debug("🔍 Режим позиций OKX: {} ({})", posMode, isHedge ? "Hedge" : "Net");
            return isHedge;

        } catch (Exception e) {
            log.error("❌ Ошибка при определении режима позиций: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Выполняет GET-запрос с подписями OKX к приватному API.
     */
    private JsonObject executeSignedGet(String endpoint) throws IOException {
        String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
        String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
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
            log.debug("📡 Ответ от OKX API [{}]: {}", endpoint, responseBody);
            return JsonParser.parseString(responseBody).getAsJsonObject();
        }
    }


    /**
     * Получение реальной информации о позиции с OKX API по символу
     */
    private JsonObject getRealPositionFromOkx(String symbol, TradeResult orderResult) {
        log.debug("==> getRealPositionFromOkx: Запрос реальной позиции для {}", symbol);

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Запрос позиции заблокирован из-за геолокации!");
            return null;
        }

        try {
            String endpoint = TRADE_POSITIONS_ENDPOINT + "?instId=" + symbol;
            JsonObject jsonResponse = executeSignedGet(endpoint);

            if (jsonResponse == null || !"0".equals(jsonResponse.get("code").getAsString())) {
                log.error("❌ Ошибка OKX API при запросе позиции {}: {}",
                        symbol, jsonResponse != null ? jsonResponse.get("msg").getAsString() : "пустой ответ");
                return null;
            }

            JsonArray data = jsonResponse.getAsJsonArray("data");
            if (data.isEmpty()) {
                log.info("📋 Позиция для {} не найдена (массив data пуст)", symbol);
                return null;
            }

            // Находим позицию по tradeId из нашего ордера (самый точный способ)
            JsonObject targetPosition = null;
            String orderTradeId = null;
            
            // Пытаемся получить tradeId из ордера
            if (orderResult != null && orderResult.getExternalOrderId() != null) {
                try {
                    JsonObject orderDetails = getOrderJson(orderResult.getExternalOrderId());
                    if (orderDetails != null && orderDetails.has("tradeId")) {
                        orderTradeId = orderDetails.get("tradeId").getAsString();
                        log.debug("📋 Получен tradeId из ордера {}: {}", orderResult.getExternalOrderId(), orderTradeId);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Не удалось получить tradeId из ордера {}: {}", orderResult.getExternalOrderId(), e.getMessage());
                }
            }
            
            // Ищем позицию по tradeId
            if (orderTradeId != null) {
                for (int i = 0; i < data.size(); i++) {
                    JsonObject position = data.get(i).getAsJsonObject();
                    if (position.has("tradeId")) {
                        String positionTradeId = position.get("tradeId").getAsString();
                        if (orderTradeId.equals(positionTradeId)) {
                            targetPosition = position;
                            log.debug("✅ Найдена позиция по tradeId {}: {}", orderTradeId, position.get("posId").getAsString());
                            break;
                        }
                    }
                }
            }
            
            // Fallback: ищем самую свежую позицию по времени
            if (targetPosition == null) {
                log.warn("⚠️ Позиция по tradeId не найдена, ищем по времени для {}", symbol);
                long latestTime = 0;
                
                for (int i = 0; i < data.size(); i++) {
                    JsonObject position = data.get(i).getAsJsonObject();
                    if (position.has("uTime")) {
                        long positionTime = position.get("uTime").getAsLong();
                        if (positionTime > latestTime) {
                            latestTime = positionTime;
                            targetPosition = position;
                        }
                    }
                }
            }
            
            if (targetPosition == null) {
                log.warn("⚠️ Не найдена подходящая позиция для {}, используем первую", symbol);
                targetPosition = data.get(0).getAsJsonObject();
            }
            
            log.debug("✅ Получена реальная позиция для {}: {}", symbol, targetPosition);
            return targetPosition;

        } catch (Exception e) {
            log.error("❌ Ошибка при запросе реальной позиции для {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Получение JSON деталей ордера
     */
    private JsonObject getOrderJson(String orderId) {
        log.debug("==> getOrderJson: Получение JSON деталей ордера {}", orderId);
        
        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Получение деталей ордера {} заблокировано из-за геолокации!", orderId);
            return null;
        }
        
        try {
            String endpoint = "/api/v5/trade/orders-history-archive?ordId=" + orderId;
            JsonObject response = executeSignedGet(endpoint);
            
            if (response == null || !"0".equals(response.get("code").getAsString())) {
                log.error("❌ Ошибка OKX API при запросе ордера {}: {}", orderId, 
                         response != null ? response.get("msg").getAsString() : "пустой ответ");
                return null;
            }
            
            JsonArray data = response.getAsJsonArray("data");
            if (data.isEmpty()) {
                log.warn("⚠️ Ордер {} не найден в истории", orderId);
                return null;
            }
            
            JsonObject orderData = data.get(0).getAsJsonObject();
            log.debug("✅ Получены JSON детали ордера {}: {}", orderId, orderData);
            return orderData;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при получении JSON деталей ордера {}: {}", orderId, e.getMessage(), e);
            return null;
        }
    }


    /**
     * Логирование реальных данных о позиции с OKX
     */
    private void logRealPositionData(String symbol, String operationType, TradeResult orderResult) {
        log.info("==> logRealPositionData: Логирование реальной позиции {} после {}", symbol, operationType);
        try {
            Thread.sleep(1000); // Пауза для появления позиции в OKX

            JsonObject data = getRealPositionFromOkx(symbol, orderResult);
            if (data == null) {
                log.warn("⚠️ Не удалось получить реальные данные о позиции для {}", symbol);
                return;
            }

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("Инструмент", getJsonStringValue(data, "instId"));
            fields.put("Сторона позиции", getJsonStringValue(data, "posSide"));
            fields.put("Размер позиции (базовые единицы)", getJsonStringValue(data, "pos") + " " + getBaseCurrency(symbol));
            fields.put("Размер позиции (абсолютный)", getJsonStringValue(data, "posSize") + " " + getBaseCurrency(symbol));
            fields.put("Средняя цена входа", getJsonStringValue(data, "avgPx") + " USDT");
            fields.put("Текущая марк-цена", getJsonStringValue(data, "markPx") + " USDT");
            fields.put("Условная стоимость", getJsonStringValue(data, "notionalUsd") + " USD");
            fields.put("Используемая маржа", getJsonStringValue(data, "margin") + " USDT");
            fields.put("Начальная маржа", getJsonStringValue(data, "imr") + " USDT");
            fields.put("Поддерживающая маржа", getJsonStringValue(data, "mmr") + " USDT");
            fields.put("Нереализованный PnL", getJsonStringValue(data, "upl") + " USDT");
            fields.put("Коэффициент PnL", getJsonStringValue(data, "uplRatio") + " %");
            fields.put("Плечо", getJsonStringValue(data, "lever") + "x");

            log.info("🔍 === РЕАЛЬНЫЕ ДАННЫЕ ПОЗИЦИИ OKX ===");
            fields.forEach((label, value) -> log.info("🔍 {}: {}", label, value));
            log.info("🔍 === КОНЕЦ РЕАЛЬНЫХ ДАННЫХ ===");

        } catch (Exception e) {
            log.error("❌ Ошибка при логировании реальных данных позиции для {}: {}", symbol, e.getMessage(), e);
        }
    }

    /**
     * Генерация HMAC SHA256 подписи для OKX API
     */
    private String generateSignature(String method, String endpoint, String body, String timestamp) {
        String message = timestamp + method + endpoint + body;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hash);

            log.debug("🔏 Генерация подписи: method={}, endpoint={}, timestamp={}, bodyLength={}, signaturePrefix={}",
                    method, endpoint, timestamp, body.length(), signature.substring(0, Math.min(8, signature.length())) + "...");

            return signature;
        } catch (Exception e) {
            log.error("❌ Ошибка при генерации подписи для сообщения '{}': {}", message, e.getMessage(), e);
            return "";
        }
    }

    /**
     * Извлекает базовую валюту из символа торгового инструмента (например, "BTC" из "BTC-USDT-SWAP").
     *
     * @param symbol Символ торгового инструмента (например, "BTC-USDT-SWAP").
     * @return Базовая валюта или пустая строка, если символ некорректен.
     */
    private String getBaseCurrency(String symbol) {
        if (symbol == null || symbol.isBlank() || !symbol.contains("-")) {
            log.warn("⚠️ Некорректный символ для извлечения базовой валюты: '{}'", symbol);
            return "";
        }

        String[] parts = symbol.split("-");
        if (parts.length < 2 || parts[0].isBlank()) {
            log.warn("⚠️ Не удалось определить базовую валюту из символа: '{}'", symbol);
            return "";
        }

        return parts[0];
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
        log.debug("==> validateOrderSize: НАЧАЛО | symbol: {}, adjustedAmount: {}, positionSize: {}, currentPrice: {}", symbol, adjustedAmount, positionSize, currentPrice);
        try {
            InstrumentInfo info = getInstrumentInfo(symbol);
            if (info == null) {
                String msg = "Не удалось получить информацию о торговом инструменте для проверки размера ордера.";
                log.error("❌ {}", msg);
                return msg;
            }

            BigDecimal minCcyAmt = Optional.ofNullable(info.getMinCcyAmt()).orElse(BigDecimal.ZERO);
            BigDecimal minNotional = Optional.ofNullable(info.getMinNotional()).orElse(BigDecimal.ZERO);

            log.debug("Минимальные значения: minCcyAmt = {}, minNotional = {}", minCcyAmt, minNotional);

            if (adjustedAmount.compareTo(minCcyAmt) < 0) {
                String error = String.format("Сумма маржи %.2f USDT меньше минимальной %.2f USDT.", adjustedAmount, minCcyAmt);
                log.warn("⚠️ {}", error);
                return error;
            }

            BigDecimal notionalValue = positionSize.multiply(currentPrice);
            log.debug("Условная стоимость сделки: {}", notionalValue);

            if (notionalValue.compareTo(minNotional) < 0) {
                String error = String.format("Условная стоимость сделки %.2f USDT меньше минимальной %.2f USDT.", notionalValue, minNotional);
                log.warn("⚠️ {}", error);
                return error;
            }

            log.debug("<== validateOrderSize: КОНЕЦ (успешно) для {}", symbol);
            return null;

        } catch (Exception e) {
            String errorMsg = "Ошибка при валидации размера ордера: " + e.getMessage();
            log.error("❌ {} для {}", errorMsg, symbol, e);
            return errorMsg;
        }
    }

    /**
     * Получение истории закрытых позиций от OKX API
     */
    public List<OkxPositionHistoryData> getPositionsHistory(String symbol) {
        log.info("==> getPositionsHistory: Запрос истории позиций для {}", symbol);

        if (!geolocationService.isGeolocationAllowed()) {
            log.error("❌ БЛОКИРОВКА: Получение истории позиций заблокировано из-за геолокации!");
            return Collections.emptyList();
        }

        try {
            String baseUrl = isSandbox ? SANDBOX_BASE_URL : PROD_BASE_URL;
            String endpoint = POSITIONS_HISTORY_ENDPOINT + "?instType=SWAP&instId=" + symbol;

            String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
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
                log.info("Ответ OKX на запрос истории позиций {}: HTTP {} | {}", symbol, response.code(), responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    log.error("❌ Ошибка OKX API при запросе истории позиций {}: {}", symbol, jsonResponse.get("msg").getAsString());
                    return Collections.emptyList();
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                List<OkxPositionHistoryData> historyList = new ArrayList<>();

                for (JsonElement element : data) {
                    JsonObject positionJson = element.getAsJsonObject();
                    OkxPositionHistoryData historyData = parsePositionHistory(positionJson);
                    if (historyData != null) {
                        historyList.add(historyData);
                    }
                }

                log.info("✅ Получено {} записей истории позиций для {}", historyList.size(), symbol);
                return historyList;

            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении истории позиций для {}: {}", symbol, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Парсинг данных истории позиции из JSON OKX
     */
    private OkxPositionHistoryData parsePositionHistory(JsonObject positionJson) {
        try {
            // Извлекаем поля как строки
            String instType = getJsonStringValue(positionJson, "instType");
            String instId = getJsonStringValue(positionJson, "instId");
            String posId = getJsonStringValue(positionJson, "posId");
            String posType = getJsonStringValue(positionJson, "posType");
            String openSize = getJsonStringValue(positionJson, "openSize");
            String closeSize = getJsonStringValue(positionJson, "closeSize");
            String avgOpenPrice = getJsonStringValue(positionJson, "avgOpenPrice");
            String avgClosePrice = getJsonStringValue(positionJson, "avgClosePrice");
            String realizedPnl = getJsonStringValue(positionJson, "realizedPnl");
            String pnl = getJsonStringValue(positionJson, "pnl");
            String pnlRatio = getJsonStringValue(positionJson, "pnlRatio");
            // OKX API возвращает cTime (creation time) и uTime (update time)
            String cTime = getJsonStringValue(positionJson, "cTime");
            String uTime = getJsonStringValue(positionJson, "uTime");
            String ccy = getJsonStringValue(positionJson, "ccy");
            String lever = getJsonStringValue(positionJson, "lever");
            String margin = getJsonStringValue(positionJson, "margin");
            String fee = getJsonStringValue(positionJson, "fee");
            String fundingFee = getJsonStringValue(positionJson, "fundingFee");

            // ЛОГИРОВАНИЕ ВСЕХ ПОЛЕЙ С РУССКИМИ ПОДПИСЯМИ
            log.debug("📊 === ПОЛНАЯ ИНФОРМАЦИЯ ОБ ИСТОРИИ ПОЗИЦИИ OKX ===");
            log.debug("🔹 instType       : {} (тип инструмента)", instType);
            log.debug("🔹 instId         : {} (ID инструмента)", instId);
            log.debug("🔹 posId          : {} (ID позиции)", posId);
            log.debug("🔹 posType        : {} (тип позиции: long/short)", posType);
            log.debug("🔹 openSize       : {} (размер при открытии)", openSize);
            log.debug("🔹 closeSize      : {} (размер при закрытии)", closeSize);
            log.debug("🔹 avgOpenPrice   : {} (средняя цена открытия)", avgOpenPrice);
            log.debug("🔹 avgClosePrice  : {} (средняя цена закрытия)", avgClosePrice);
            log.debug("🔹 realizedPnl    : {} (реализованный PnL)", realizedPnl);
            log.debug("🔹 pnl            : {} (общий PnL)", pnl);
            log.debug("🔹 pnlRatio       : {} (PnL в процентах)", pnlRatio);
            log.debug("🔹 cTime          : {} (время создания)", cTime);
            log.debug("🔹 uTime          : {} (время обновления)", uTime);
            log.debug("🔹 ccy            : {} (валюта)", ccy);
            log.debug("🔹 lever          : {} (плечо)", lever);
            log.debug("🔹 margin         : {} (маржа)", margin);
            log.debug("🔹 fee            : {} (комиссия)", fee);
            log.debug("🔹 fundingFee     : {} (фандинг комиссия)", fundingFee);
            log.debug("📊 === КОНЕЦ ИНФОРМАЦИИ ОБ ИСТОРИИ ПОЗИЦИИ ===");

            OkxPositionHistoryData historyData = new OkxPositionHistoryData();
            historyData.setInstrumentType(instType);
            historyData.setInstrumentId(instId);
            historyData.setPositionId(posId);
            historyData.setPositionType(posType);

            historyData.setOpenSize(safeParseDecimal(openSize));
            historyData.setCloseSize(safeParseDecimal(closeSize));
            historyData.setAverageOpenPrice(safeParseDecimal(avgOpenPrice));
            historyData.setAverageClosePrice(safeParseDecimal(avgClosePrice));
            historyData.setRealizedPnl(safeParseDecimal(realizedPnl));
            historyData.setPnl(safeParseDecimal(pnl));
            historyData.setPnlRatio(safeParseDecimal(pnlRatio));

            historyData.setOpenTime(cTime);  // Используем cTime для времени открытия
            historyData.setCloseTime(uTime); // Используем uTime для времени обновления/закрытия
            historyData.setCurrency(ccy);
            historyData.setLeverage(safeParseDecimal(lever));
            historyData.setMargin(safeParseDecimal(margin));
            historyData.setFee(safeParseDecimal(fee));
            historyData.setFundingFee(safeParseDecimal(fundingFee));

            return historyData;
        } catch (Exception e) {
            log.error("❌ Ошибка при парсинге истории позиции: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Безопасный парсинг BigDecimal из строки
     */
    private BigDecimal safeParseDecimal(String value) {
        if (value == null || value.isEmpty() || "N/A".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            log.debug("⚠️ Ошибка при парсинге числа '{}': {}", value, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Получение реального P&L от OKX API для закрытой позиции
     */
    public OkxPositionHistoryData getRealizedPnLFromOkx(String symbol, String positionId) {
        log.debug("==> getRealizedPnLFromOkx: Получение реального P&L для {} (позиция: {})", symbol, positionId);

        // Делаем несколько попыток найти позицию в истории с интервалами
        int maxAttempts = 3;
        int attemptDelayMs = 2000; // 2 секунды между попытками

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.info("🔄 Попытка {}/{} поиска позиции {} в истории OKX", attempt, maxAttempts, symbol);

            List<OkxPositionHistoryData> history = getPositionsHistory(symbol);

            // Ищем позицию по точному OKX positionId, если нет - берем последнюю по времени (fallback)
            Optional<OkxPositionHistoryData> targetPosition = Optional.empty();

            if (positionId != null && !positionId.isEmpty() && !positionId.equals("N/A")) {
                // Точный поиск по OKX positionId
                targetPosition = history.stream()
                        .filter(h -> positionId.equals(h.getPositionId()))
                        .filter(h -> h.getCloseTime() != null && !h.getCloseTime().equals("N/A"))
                        .findFirst();

                if (targetPosition.isPresent()) {
                    log.info("🎯 Найдена позиция по точному OKX positionId: {}", positionId);
                } else {
                    log.debug("🔍 Позиция с точным OKX positionId {} не найдена, используем fallback поиск", positionId);
                }
            }

            // Fallback: берем последнюю закрытую позицию по времени
            Optional<OkxPositionHistoryData> latestClosedPosition = targetPosition.isPresent() ?
                    targetPosition :
                    history.stream()
                            .filter(h -> h.getCloseTime() != null && !h.getCloseTime().equals("N/A"))
                            .max(Comparator.comparing(OkxPositionHistoryData::getCloseTime));

            if (latestClosedPosition.isPresent()) {
                OkxPositionHistoryData positionData = latestClosedPosition.get();

                // ЛОГИРОВАНИЕ ВСЕХ ПОЛЕЙ НАЙДЕННОЙ ПОЗИЦИИ
                log.info("✅ === НАЙДЕНА ПОСЛЕДНЯЯ ЗАКРЫТАЯ ПОЗИЦИЯ {} НА ПОПЫТКЕ {} ===", symbol, attempt);
                log.info("🔹 instrumentType     : {} (тип инструмента)", positionData.getInstrumentType()); //SWAP
                log.info("🔹 instrumentId       : {} (ID инструмента)", positionData.getInstrumentId()); //AI16Z-USDT-SWAP
                log.info("🔹 positionId         : {} (ID позиции)", positionData.getPositionId()); //2774901548676227072
                log.info("🔹 positionType       : {} (тип позиции)", positionData.getPositionType()); //N/A
                log.info("🔹 openSize           : {} (размер при открытии)", positionData.getOpenSize()); //0
                log.info("🔹 closeSize          : {} (размер при закрытии)", positionData.getCloseSize()); //0
                log.info("🔹 averageOpenPrice   : {} (средняя цена открытия)", positionData.getAverageOpenPrice()); //0
                log.info("🔹 averageClosePrice  : {} (средняя цена закрытия)", positionData.getAverageClosePrice()); //0
                log.info("🔹 realizedPnl        : {} (реализованный PnL)", positionData.getRealizedPnl()); //1.05478535
                log.info("🔹 pnl                : {} (общий PnL)", positionData.getPnl()); //1.0841
                log.info("🔹 pnlRatio           : {} (PnL в процентах)", positionData.getPnlRatio()); //0.0733187372708758
                log.info("🔹 openTime           : {} (время открытия)", positionData.getOpenTime()); //1756156538051
                log.info("🔹 closeTime          : {} (время закрытия)", positionData.getCloseTime()); //1756163527776
                log.info("🔹 currency           : {} (валюта)", positionData.getCurrency()); //USDT
                log.info("🔹 leverage           : {} (плечо)", positionData.getLeverage()); //2.0
                log.info("🔹 margin             : {} (маржа)", positionData.getMargin()); //0
                log.info("🔹 fee                : {} (комиссия)", positionData.getFee()); //-0.02931465
                log.info("🔹 fundingFee         : {} (фандинг комиссия)", positionData.getFundingFee()); //0
                log.info("✅ === КОНЕЦ ИНФОРМАЦИИ О НАЙДЕННОЙ ПОЗИЦИИ ===");

                return positionData;
            }

            // Если это не последняя попытка, ждем перед следующей
            if (attempt < maxAttempts) {
                log.info("⏳ Позиция {} не найдена, ждем {} мс перед попыткой {}", symbol, attemptDelayMs, attempt + 1);
                try {
                    Thread.sleep(attemptDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("⚠️ Прерван ожидание между попытками поиска позиции");
                    break;
                }
            }
        }

        log.warn("⚠️ Не найдена закрытая позиция в истории OKX для {} после {} попыток (позиция: {})", symbol, maxAttempts, positionId);
        return null;
    }

    /**
     * Безопасное извлечение строкового значения из JsonObject с защитой от null
     */
    private String getJsonStringValue(JsonObject jsonObject, String fieldName) {
        if (jsonObject == null || fieldName == null) {
            return "N/A";
        }
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

    /**
     * ВРЕМЕННЫЙ ТЕСТОВЫЙ МЕТОД: Получение positionId для FARTCOIN-USDT-SWAP
     * TODO: Удалить после тестирования
     */
    public void testGetFartcoinPositionHistory() {
        String symbol = "FARTCOIN-USDT-SWAP";
        log.info("🧪 ТЕСТ: Запрос истории позиций для {}", symbol);

        List<OkxPositionHistoryData> history = getPositionsHistory(symbol);

        if (history.isEmpty()) {
            log.warn("⚠️ ТЕСТ: Нет истории позиций для {}", symbol);
            return;
        }

        log.info("🧪 ТЕСТ: Найдено {} записей истории для {}", history.size(), symbol);

        for (int i = 0; i < history.size(); i++) {
            OkxPositionHistoryData pos = history.get(i);
            log.info("🧪 ТЕСТ: Позиция #{}: positionId='{}', openTime='{}', closeTime='{}', realizedPnl='{}'",
                    i + 1, pos.getPositionId(), pos.getOpenTime(), pos.getCloseTime(), pos.getRealizedPnl());
        }

        // Показать самую последнюю закрытую позицию
        Optional<OkxPositionHistoryData> latestClosed = history.stream()
                .filter(h -> h.getCloseTime() != null && !h.getCloseTime().equals("N/A"))
                .max(Comparator.comparing(OkxPositionHistoryData::getCloseTime));

        if (latestClosed.isPresent()) {
            OkxPositionHistoryData latest = latestClosed.get();
            log.info("🎯 ТЕСТ: Последняя закрытая позиция - positionId='{}', closeTime='{}'",
                    latest.getPositionId(), latest.getCloseTime());
        } else {
            log.warn("⚠️ ТЕСТ: Нет закрытых позиций в истории");
        }
    }

}