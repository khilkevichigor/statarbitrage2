package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.model.Position;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Менеджер портфолио для OKX API
 * Получает реальные данные с биржи OKX
 */
@Slf4j
@Service
public class OkxPortfolioManager {

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

    // Константы OKX API
    private static final String PROD_BASE_URL = "https://www.okx.com";
    private static final String SANDBOX_BASE_URL = "https://www.okx.com";
    private static final String ACCOUNT_BALANCE_ENDPOINT = "/api/v5/account/balance";
    private static final String TRADE_POSITIONS_ENDPOINT = "/api/v5/account/positions";

    // Кэш портфолио
    private Portfolio cachedPortfolio;
    private long lastUpdateTime = 0;
    private static final long CACHE_DURATION_MS = 10000; // 10 секунд

    private final Object portfolioLock = new Object();

    public void initializePortfolio(BigDecimal initialBalance) {
        // Для OKX не нужна инициализация - данные приходят с биржи
        log.info("🔄 OKX PortfolioManager: Инициализация не требуется, данные получаются с биржи");
    }

    public Portfolio getCurrentPortfolio() {
        synchronized (portfolioLock) {
            long currentTime = System.currentTimeMillis();

            // Проверяем кэш
            if (cachedPortfolio != null && (currentTime - lastUpdateTime) < CACHE_DURATION_MS) {
                return cachedPortfolio;
            }

            // Обновляем данные с OKX
            Portfolio portfolio = fetchPortfolioFromOkx();
            if (portfolio != null) {
                cachedPortfolio = portfolio;
                lastUpdateTime = currentTime;
                return portfolio;
            }

            // Если не удалось получить данные, возвращаем пустое портфолио
            return createEmptyPortfolio();
        }
    }

    public boolean reserveBalance(BigDecimal amount) {
        // Для OKX резервирование происходит автоматически при создании ордеров
        return hasAvailableBalance(amount);
    }

    public void releaseReservedBalance(BigDecimal amount) {
        // Для OKX освобождение происходит автоматически при закрытии ордеров
        log.debug("💸 OKX: Освобождение резерва {} (автоматически)", amount);
    }

    public void onPositionOpened(Position position) {
        // Для OKX данные о позициях обновляются автоматически
        log.info("📈 OKX: Открыта позиция {}", position.getSymbol());
        invalidateCache();
    }

    public void onPositionClosed(Position position, BigDecimal pnl, BigDecimal fees) {
        // Для OKX данные о позициях обновляются автоматически
        log.info("📉 OKX: Закрыта позиция {} | PnL: {}", position.getSymbol(), pnl);
        invalidateCache();
    }

    public BigDecimal calculateMaxPositionSize() {
        Portfolio portfolio = getCurrentPortfolio();
        if (portfolio == null) {
            return BigDecimal.ZERO;
        }

        // 10% от доступного баланса на одну позицию
        BigDecimal maxPositionPercent = BigDecimal.valueOf(10);
        return portfolio.getAvailableBalance()
                .multiply(maxPositionPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public boolean hasAvailableBalance(BigDecimal amount) {
        Portfolio portfolio = getCurrentPortfolio();
        if (portfolio == null) {
            return false;
        }
        return portfolio.getAvailableBalance().compareTo(amount) >= 0;
    }

    public BigDecimal getPortfolioReturn() {
        Portfolio portfolio = getCurrentPortfolio();
        if (portfolio == null) {
            return BigDecimal.ZERO;
        }
        return portfolio.getTotalReturn();
    }

    public BigDecimal getMaxDrawdown() {
        Portfolio portfolio = getCurrentPortfolio();
        if (portfolio == null) {
            return BigDecimal.ZERO;
        }
        return portfolio.getMaxDrawdown();
    }

    public void updatePortfolioValue() {
        synchronized (portfolioLock) {
            // Принудительно обновляем данные с OKX
            invalidateCache();
            getCurrentPortfolio();
        }
    }

    public void savePortfolio() {
        // Для OKX не нужно сохранять - данные хранятся на бирже
        log.debug("💾 OKX: Сохранение портфолио не требуется");
    }

    public void loadPortfolio() {
        // Для OKX не нужно загружать - данные получаются с биржи
        log.debug("📊 OKX: Загрузка портфолио не требуется");
    }

    /**
     * Получение портфолио с OKX API
     */
    private Portfolio fetchPortfolioFromOkx() {
        try {
            if (!isApiConfigured()) {
                log.warn("⚠️ OKX API не настроен");
                return null;
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
                if (!response.isSuccessful()) {
                    log.error("❌ OKX API HTTP error: {}", response.code());
                    return null;
                }

                String responseBody = response.body().string();
                log.debug("🔍 OKX API ответ: {}", responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                JsonElement codeElement = jsonResponse.get("code");
                if (codeElement == null) {
                    log.error("❌ OKX API ответ не содержит поле 'code'");
                    return null;
                }

                String code = codeElement.getAsString();
                if (!"0".equals(code)) {
                    JsonElement msgElement = jsonResponse.get("msg");
                    String msg = msgElement != null ? msgElement.getAsString() : "Unknown error";
                    log.error("❌ OKX API error: код={}, сообщение={}", code, msg);
                    return null;
                }

                return parsePortfolioData(jsonResponse);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении портфолио с OKX: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Парсинг данных портфолио из ответа OKX
     */
    private Portfolio parsePortfolioData(JsonObject jsonResponse) {
        try {
            log.debug("🔍 Парсинг данных портфолио OKX");

            JsonArray data = jsonResponse.getAsJsonArray("data");
            if (data == null || data.isEmpty()) {
                log.warn("⚠️ Массив 'data' пуст или отсутствует");
                return createEmptyPortfolio();
            }

            JsonObject account = data.get(0).getAsJsonObject();
            JsonArray details = account.getAsJsonArray("details");

            if (details == null || details.isEmpty()) {
                log.warn("⚠️ Массив 'details' пуст или отсутствует");
                return createEmptyPortfolio();
            }

            log.debug("💰 Найдено {} валют в портфолио", details.size());

            BigDecimal totalBalance = BigDecimal.ZERO;
            BigDecimal availableBalance = BigDecimal.ZERO;
            BigDecimal unrealizedPnL = BigDecimal.ZERO;

            // Ищем USDT баланс
            for (JsonElement detail : details) {
                JsonObject currency = detail.getAsJsonObject();
                log.debug("💰 Обрабатываем валюту: {}", currency.toString());

                JsonElement ccyElement = currency.get("ccy");
                if (ccyElement == null) {
                    log.warn("⚠️ Поле 'ccy' отсутствует в данных валюты");
                    continue;
                }
                String ccy = ccyElement.getAsString();

                if ("USDT".equals(ccy)) {
                    log.debug("💰 Найден USDT баланс");

                    // Проверяем поле eq
                    JsonElement eqElement = currency.get("eq");
                    if (eqElement == null) {
                        log.error("❌ Поле 'eq' отсутствует для USDT");
                        continue;
                    }
                    String eqStr = eqElement.getAsString();
                    log.debug("💰 eq (общий баланс): {}", eqStr);

                    // Проверяем поле availEq
                    JsonElement availEqElement = currency.get("availEq");
                    if (availEqElement == null) {
                        log.error("❌ Поле 'availEq' отсутствует для USDT");
                        continue;
                    }
                    String availEqStr = availEqElement.getAsString();
                    log.debug("💰 availEq (доступный баланс): {}", availEqStr);

                    // Проверяем поле uPnL (может отсутствовать или быть null)
                    JsonElement uPnLElement = currency.get("uPnL");
                    String uPnLStr = "0";
                    if (uPnLElement != null && !uPnLElement.isJsonNull()) {
                        uPnLStr = uPnLElement.getAsString();
                        log.debug("💰 uPnL (нереализованная прибыль): {}", uPnLStr);
                    } else {
                        log.debug("⚠️ Поле 'uPnL' отсутствует или null для USDT, используем 0"); //todo подумать что делать с этим!
                    }

                    totalBalance = new BigDecimal(eqStr);
                    availableBalance = new BigDecimal(availEqStr);
                    unrealizedPnL = new BigDecimal(uPnLStr);
                    break;
                }
            }

            // Получаем количество активных позиций
            int activePositions = getActivePositionsCount();

            // Создаем портфолио
            Portfolio portfolio = Portfolio.builder()
                    .totalBalance(totalBalance)
                    .availableBalance(availableBalance)
                    .reservedBalance(totalBalance.subtract(availableBalance))
                    .initialBalance(totalBalance) // Для OKX не знаем начальный баланс
                    .unrealizedPnL(unrealizedPnL)
                    .realizedPnL(BigDecimal.ZERO) // Нужно получить отдельно
                    .totalFeesAccrued(BigDecimal.ZERO) // Нужно получить отдельно
                    .maxDrawdown(BigDecimal.ZERO) // Нужно рассчитать
                    .highWaterMark(totalBalance)
                    .activePositionsCount(activePositions)
                    .createdAt(LocalDateTime.now())
                    .lastUpdated(LocalDateTime.now())
                    .build();

            log.debug("📊 OKX Портфолио: Общий баланс: {}, Доступно: {}, Нереализованная PnL: {}, Активных позиций: {}",
                    totalBalance, availableBalance, unrealizedPnL, activePositions);

            return portfolio;

        } catch (Exception e) {
            log.error("❌ Ошибка при парсинге данных портфолио OKX: {}", e.getMessage());
            return createEmptyPortfolio();
        }
    }

    /**
     * Получение количества активных позиций
     */
    private int getActivePositionsCount() {
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
                if (!response.isSuccessful()) {
                    return 0;
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!"0".equals(jsonResponse.get("code").getAsString())) {
                    return 0;
                }

                JsonArray data = jsonResponse.getAsJsonArray("data");
                return data.size();
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при получении активных позиций: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Создание пустого портфолио
     */
    private Portfolio createEmptyPortfolio() {
        return Portfolio.builder()
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .initialBalance(BigDecimal.ZERO)
                .unrealizedPnL(BigDecimal.ZERO)
                .realizedPnL(BigDecimal.ZERO)
                .totalFeesAccrued(BigDecimal.ZERO)
                .maxDrawdown(BigDecimal.ZERO)
                .highWaterMark(BigDecimal.ZERO)
                .activePositionsCount(0)
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * Генерация подписи для OKX API
     */
    private String generateSignature(String method, String endpoint, String body, String timestamp) {
        try {
            String message = timestamp + method + endpoint + body;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("❌ Ошибка при генерации подписи OKX: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Проверка настройки API
     */
    private boolean isApiConfigured() {
        return apiKey != null && !apiKey.isEmpty() &&
                apiSecret != null && !apiSecret.isEmpty() &&
                passphrase != null && !passphrase.isEmpty();
    }

    /**
     * Инвалидация кэша
     */
    private void invalidateCache() {
        synchronized (portfolioLock) {
            lastUpdateTime = 0;
            cachedPortfolio = null;
        }
    }
}