package com.example.statarbitrage.api.threecommas;

import com.example.statarbitrage.model.threecommas.ProfitData;
import com.example.statarbitrage.model.threecommas.StrategyList;
import com.example.statarbitrage.model.threecommas.response.bot.DcaBot;
import com.example.statarbitrage.model.threecommas.response.bot.DcaBotDealsStats;
import com.example.statarbitrage.model.threecommas.response.bot.DcaBotProfit;
import com.example.statarbitrage.model.threecommas.response.bot.DcaBotStats;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeCommasBotsClient {

    private static final String API_KEY_ACCOUNT_1 = "59690761f0314ea1b3e5a034dbe91d29501546e7e99548e2a9e8c773f089c25b";
    private static final String API_SECRET_ACCOUNT_1 = "24c4bbec59bfd6182821493a2ecaea6bba43d13f67d7e6892059c0891048761cb9f6290a7e0d0a0a2c2969ebcd76c93c9152fd519027c12ebf93f4af1315b2a85e37bfe94b79cee71d25046140aad7f188fab5d67183c70520519f6a879b90d81df881d4";
    private static final String BASE_URL = "https://api.3commas.io";
    private static final int LONG_DCA_BOT_ID = 15911089;
    private static final int SHORT_DCA_BOT_ID = 11111111;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient();

    // 📌 Получить всех ботов (можно фильтровать по type=paper или real)
    public void getBotsList() throws Exception {
        log.info("3commas -> getBotsList...");
        String path = "/public/api/ver1/bots";
        String url = BASE_URL + path;

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        // Формирование подписи
        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .addHeader("Timestamp", timestamp)
                .build();

        System.out.println("Debug:");
        System.out.println("Path: " + path);
        System.out.println("Message to sign: " + path);
        System.out.println("Signature: " + signature);

        try (Response response = client.newCall(request).execute()) {
            System.out.println("📋 Bots List (status " + response.code() + "):");
            System.out.println(response.body().string());
        }
    }

    public void getDcaBots() throws Exception {
        log.info("3commas -> getDcaBots...");
        String path = "/public/api/ver1/bots";
        String url = BASE_URL + path;

        // Пустое тело запроса (GET без параметров)
        String payload = "";

        // Подпись
        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path + payload);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            log.info("🤖 DCA Bots (status " + response.code() + "):");

            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            List<DcaBot> bots = MAPPER.readValue(responseBody, new TypeReference<>() {
            });
            for (DcaBot bot : bots) {
                log.info("Bot name: " + bot.getName() + ", ID: " + bot.getId());
            }
        }
    }

    public DcaBot getDcaBot(boolean isLong) throws Exception {
        log.info("3commas -> getDcaBot...");
        long botId = isLong ? LONG_DCA_BOT_ID : SHORT_DCA_BOT_ID;
        String path = "/public/api/ver1/bots/" + botId + "/show";
        String url = BASE_URL + path;

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            log.info("🤖 DCA Bot #" + botId + " (status " + response.code() + "):");
            String responseBody = response.body().string();

            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // Ответ содержит {"bot": {...}} — оборачиваем в вспомогательный класс
            DcaBot bot = MAPPER.readValue(responseBody, DcaBot.class);

            log.info("Название бота: " + bot.getName() + ", Стратегия: " + bot.getStrategy());
            log.info(bot.toString());
            return bot;
        }
    }

    public DcaBot editDcaBot(DcaBot dcaBot) throws Exception {
        log.info("3commas -> editDcaBot...");
        String path = "/public/api/ver1/bots/" + dcaBot.getId() + "/update";
        String url = BASE_URL + path;

        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String payload = MAPPER.writeValueAsString(dcaBot);

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path + payload);

        RequestBody requestBody = RequestBody.create(payload, MediaType.parse("application/json"));


        Request request = new Request.Builder()
                .url(url)
                .patch(requestBody)  // <-- Используем PATCH здесь
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("📤 Edit DCA Bot (status " + response.code() + "):");
            log.info(responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Edit failed: " + responseBody);
            }

            DcaBot updatedBot = MAPPER.readValue(responseBody, DcaBot.class);
            log.info("Обновлённый бот: " + updatedBot.getName());
            return updatedBot;
        }
    }

    public DcaBot disableDcaBot(long botId) throws Exception {
        log.info("3commas -> disableDcaBot...");
        String path = "/public/api/ver1/bots/" + botId + "/disable";
        String url = BASE_URL + path;

        String payload = ""; // тело запроса отсутствует для этого PATCH
        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path + payload);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, MediaType.parse("application/json")))
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("📴 Disable DCA Bot (status " + response.code() + "):");
            log.info(responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Disable failed: " + responseBody);
            }

            // Можно десериализовать результат, если нужно:
            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            DcaBot updatedBot = MAPPER.readValue(responseBody, DcaBot.class);
            log.info("Отключённый бот: " + updatedBot.getName());
            return updatedBot;
        }
    }

    public DcaBot enableDcaBot(long botId) throws Exception {
        log.info("3commas -> enableDcaBot...");
        String path = "/public/api/ver1/bots/" + botId + "/enable";
        String url = BASE_URL + path;

        String payload = "";
        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path + payload);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, MediaType.parse("application/json")))
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("📴 Enable DCA Bot (status " + response.code() + "):");
            log.info(responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Enable failed: " + responseBody);
            }

            // Можно десериализовать результат, если нужно:
            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            DcaBot updatedBot = MAPPER.readValue(responseBody, DcaBot.class);
            log.info("Влючённый бот: " + updatedBot.getName());
            return updatedBot;
        }
    }

    public DcaBotProfit getDcaBotProfitData(long botId) throws Exception {
        log.info("3commas -> getDcaBotProfitData...");
        String path = "/public/api/ver1/bots/" + botId + "/profit_by_day";
        String url = BASE_URL + path;

        String payload = ""; // тело отсутствует
        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path + payload);

        Request request = new Request.Builder()
                .url(url)
                .get() // используем GET
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("💰 Get DCA Bot Profit Data (status " + response.code() + "):");
            log.info(responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Failed to get profit data: " + responseBody);
            }

            DcaBotProfit profitResponse = MAPPER.readValue(responseBody, DcaBotProfit.class);

            for (ProfitData data : profitResponse.getData()) {
                log.info("📅 Дата: " + data.getSDate());
                log.info("💵 USD прибыль: " + data.getProfit().getUsd());
                log.info("₿ BTC прибыль: " + data.getProfit().getBtc());
            }
            return profitResponse;
        }
    }

    public DcaBotStats getDcaBotStats(long botId) throws Exception {
        log.info("3commas -> getDcaBotStats...");
        String path = "/public/api/ver1/bots/" + botId + "/stats"; //todo не тот эндпоинт
        String url = BASE_URL + path;

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("📊 Get DCA Bot Stats (status " + response.code() + "):");
            log.info(responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Failed to get stats: " + responseBody);
            }

            return MAPPER.readValue(responseBody, DcaBotStats.class);
        }
    }

    public DcaBotDealsStats getDcaBotDealsStats(long botId) throws Exception {
        log.info("3commas -> getDcaBotDealsStats...");
        String path = "/public/api/ver1/bots/" + botId + "/deals_stats";
        String url = BASE_URL + path;

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .build();

        try (Response response = client.newCall(request).execute()) {
            log.info("📈 Get DCA Bot Deals Stats (status {}):", response.code());

            if (!response.isSuccessful()) {
                throw new IOException("Failed to get deals stats: " + response.body().string());
            }

            String body = response.body().string();
            DcaBotDealsStats dcaBotDealsStats = MAPPER.readValue(body, DcaBotDealsStats.class);
            System.out.println(body);
            return dcaBotDealsStats;
        }
    }

    public StrategyList getAvailableStrategies() throws Exception {
        log.info("3commas -> getAvailableStrategies...");
        String path = "/public/api/ver1/bots/strategy_list";
        String url = BASE_URL + path;

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();

            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            StrategyList strategies = MAPPER.readValue(responseBody, StrategyList.class);

            log.info("📊 Стратегий получено: " + strategies.getStrategies().size());
            strategies.getStrategies().forEach((key, value) ->
                    log.info("🧠 " + key + ": " + value.getName())
            );

            return strategies;
        }
    }

    public DcaBot closeDcaBotAtMarketPrice(long botId) throws Exception {
        log.info("3commas -> closeDcaBotAtMarketPrice...");
        String path = "/public/api/ver1/bots/" + botId + "/panic_sell_all_deals";
        String url = BASE_URL + path;

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET_ACCOUNT_1, path);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", null)) // POST с пустым телом
                .addHeader("APIKEY", API_KEY_ACCOUNT_1)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            DcaBot dcaBotResponse = MAPPER.readValue(response.body().string(), DcaBot.class);
            log.info(dcaBotResponse.toString());
            return dcaBotResponse;
        }
    }
}
