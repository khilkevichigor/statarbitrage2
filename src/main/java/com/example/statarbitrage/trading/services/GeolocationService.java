package com.example.statarbitrage.trading.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Сервис для проверки геолокации IP-адреса
 * Защищает от случайных вызовов API из США при отключенном VPN
 */
@Slf4j
@Service
public class GeolocationService {

    // HTTP клиент для проверки геолокации (быстрый)
    private static final OkHttpClient geoCheckClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build();

    // Кэш результата проверки геолокации (чтобы не проверять каждый раз)
    private volatile String lastKnownCountry = null;
    private volatile long lastGeoCheckTime = 0;
    private static final long GEO_CHECK_CACHE_DURATION = 5 * 60 * 1000; // 5 минут

    /**
     * Проверка геолокации с кэшированием
     * @return true если разрешено, false если IP из США
     */
    public boolean isGeolocationAllowed() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Проверяем кэш (5 минут)
            if (lastKnownCountry != null && (currentTime - lastGeoCheckTime) < GEO_CHECK_CACHE_DURATION) {
                boolean isUSA = "US".equals(lastKnownCountry);
                if (isUSA) {
                    log.error("🚫 БЛОКИРОВКА: Обнаружено местоположение в США (кэш). IP из страны: {}", lastKnownCountry);
                    return false;
                }
                log.debug("✅ Геолокация проверена (кэш): {} - разрешено", lastKnownCountry);
                return true;
            }
            
            // Выполняем новую проверку
            log.info("🌍 Проверка геолокации...");
            
            // Используем несколько сервисов для надёжности
            String country = checkCountryViaIpApi();
            if (country == null) {
                country = checkCountryViaIpify();
            }
            
            if (country == null) {
                log.warn("⚠️ Не удалось определить страну, разрешаем вызов (может быть проблема с сетью)");
                return true;
            }
            
            // Обновляем кэш
            lastKnownCountry = country;
            lastGeoCheckTime = currentTime;
            
            boolean isUSA = "US".equals(country);
            if (isUSA) {
                log.error("🚫 БЛОКИРОВКА: Обнаружено местоположение в США! IP из страны: {}", country);
                log.error("🚫 ВНИМАНИЕ: VPN может быть отключен! Проверьте соединение!");
                return false;
            } else {
                log.info("✅ Геолокация проверена: {} - разрешено", country);
                return true;
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке геолокации: {}", e.getMessage());
            log.warn("⚠️ Разрешаем вызов из-за ошибки проверки геолокации");
            return true; // Разрешаем при ошибке, чтобы не блокировать торговлю
        }
    }

    /**
     * Получение текущей страны (может быть null)
     */
    public String getCurrentCountry() {
        return lastKnownCountry;
    }

    /**
     * Принудительная проверка геолокации (игнорирует кэш)
     */
    public String forceCheckGeolocation() {
        try {
            log.info("🧪 Принудительная проверка геолокации...");
            
            // Сбрасываем кэш
            lastKnownCountry = null;
            lastGeoCheckTime = 0;
            
            // Выполняем проверку
            String country = checkCountryViaIpApi();
            if (country == null) {
                country = checkCountryViaIpify();
            }
            
            if (country != null) {
                lastKnownCountry = country;
                lastGeoCheckTime = System.currentTimeMillis();
                
                boolean isUSA = "US".equals(country);
                String result = String.format(
                    "Результат геолокации:\n" +
                    "- Страна: %s\n" +
                    "- Разрешено: %s\n" +
                    "- Время проверки: %s",
                    country,
                    isUSA ? "🚫 Нет" : "✅ Да",
                    new java.util.Date().toString()
                );
                
                log.info("🧪 {}", result);
                return result;
            } else {
                String result = "Не удалось определить страну при принудительной проверке";
                log.warn("🧪 {}", result);
                return result;
            }
            
        } catch (Exception e) {
            String errorResult = "Ошибка при принудительной проверке геолокации: " + e.getMessage();
            log.error("🧪 {}", errorResult);
            return errorResult;
        }
    }

    /**
     * Проверка геолокации при запуске приложения
     */
    public void checkGeolocationOnStartup() {
        log.info("🌍 Проверка геолокации при запуске...");
        
        try {
            String country = checkCountryViaIpApi();
            if (country == null) {
                country = checkCountryViaIpify();
            }
            
            if (country == null) {
                log.warn("⚠️ Не удалось определить страну при запуске");
                log.warn("⚠️ Проверьте подключение к интернету");
                return;
            }
            
            // Обновляем кэш
            lastKnownCountry = country;
            lastGeoCheckTime = System.currentTimeMillis();
            
            boolean isUSA = "US".equals(country);
            if (isUSA) {
                log.error("🚫 КРИТИЧЕСКОЕ ПРЕДУПРЕЖДЕНИЕ: Обнаружено местоположение в США!");
                log.error("🚫 Страна: {}", country);
                log.error("🚫 ВСЕ ВЫЗОВЫ OKX API БУДУТ ЗАБЛОКИРОВАНЫ!");
                log.error("🚫 ВКЛЮЧИТЕ VPN ПЕРЕД ТОРГОВЛЕЙ НА OKX!");
                log.error("🚫 ═══════════════════════════════════════════════════════════");
            } else {
                log.info("✅ Геолокация при запуске: {} - безопасно для OKX", country);
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке геолокации: {}", e.getMessage());
        }
    }

    /**
     * Проверка страны через ip-api.com
     */
    private String checkCountryViaIpApi() {
        try {
            Request request = new Request.Builder()
                    .url("http://ip-api.com/json/?fields=countryCode")
                    .get()
                    .build();
            
            try (Response response = geoCheckClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("⚠️ Ошибка HTTP при проверке геолокации через ip-api: {}", response.code());
                    return null;
                }
                
                String responseBody = response.body().string();
                log.debug("🔍 Ответ ip-api: {}", responseBody);
                
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                if (jsonResponse.has("countryCode")) {
                    return jsonResponse.get("countryCode").getAsString();
                }
                
                return null;
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при проверке геолокации через ip-api: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Проверка страны через ipify (резервный сервис)
     */
    private String checkCountryViaIpify() {
        try {
            Request request = new Request.Builder()
                    .url("https://api.ipify.org?format=json")
                    .get()
                    .build();
            
            try (Response response = geoCheckClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("⚠️ Ошибка HTTP при проверке IP через ipify: {}", response.code());
                    return null;
                }
                
                String responseBody = response.body().string();
                log.debug("🔍 Ответ ipify: {}", responseBody);
                
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                if (jsonResponse.has("ip")) {
                    String ip = jsonResponse.get("ip").getAsString();
                    log.debug("🔍 Получен IP: {}", ip);
                    
                    // Проверяем IP через ipgeolocation.io
                    return checkCountryByIp(ip);
                }
                
                return null;
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при проверке IP через ipify: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Проверка страны по IP через ipgeolocation.io
     */
    private String checkCountryByIp(String ip) {
        try {
            Request request = new Request.Builder()
                    .url("https://ipgeolocation.io/ip-location/" + ip)
                    .get()
                    .build();
            
            try (Response response = geoCheckClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("⚠️ Ошибка HTTP при проверке геолокации по IP: {}", response.code());
                    return null;
                }
                
                String responseBody = response.body().string();
                log.debug("🔍 Ответ ipgeolocation: {}", responseBody);
                
                // Простой парсинг HTML для получения кода страны
                if (responseBody.contains("United States")) {
                    return "US";
                }
                
                // Попробуем найти другие признаки
                if (responseBody.contains("country_code2")) {
                    // Извлекаем код страны из HTML
                    String[] parts = responseBody.split("country_code2");
                    if (parts.length > 1) {
                        String afterCode = parts[1];
                        if (afterCode.contains("US")) {
                            return "US";
                        }
                    }
                }
                
                return "OTHER"; // Не США
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при проверке геолокации по IP: {}", e.getMessage());
            return null;
        }
    }
}