package com.example.statarbitrage.api.threecommas;

import com.example.statarbitrage.model.threecommas.*;
import com.example.statarbitrage.model.threecommas.response.trade.ActiveTradesResponse;
import com.example.statarbitrage.model.threecommas.response.trade.TradeResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeCommasSimpleTradeClient {

    private static final String API_KEY = "59690761f0314ea1b3e5a034dbe91d29501546e7e99548e2a9e8c773f089c25b";
    private static final String API_SECRET = "24c4bbec59bfd6182821493a2ecaea6bba43d13f67d7e6892059c0891048761cb9f6290a7e0d0a0a2c2969ebcd76c93c9152fd519027c12ebf93f4af1315b2a85e37bfe94b79cee71d25046140aad7f188fab5d67183c70520519f6a879b90d81df881d4";
    private static final String BASE_URL = "https://api.3commas.io";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient();

    /*
    {"trades":[{"uuid":"76a61de9-1cae-4273-923d-1e2a3c48737a","account_id":32991372,"pair":"USDT_XRP","created_at":1738765497,"closed_at":1738765500,"order":{"type":"market","side":"sell","strategy":null,"position_side":"both","reduce_only":false},"units":{"value":"401.560887"},"price":{"value":null},"total":{"value":"1019.563092093"},"conditional":{"enabled":false,"value":null,"price":{"value":null,"type":null}},"trailing":{"enabled":false,"value":null,"percent":null},"timeout":{"enabled":false,"value":null},"leverage":{"type":null},"status":{"value":"finished","error":null},"filled":{"units":"397.545278","total":"1008.5750701795655719","price":"2.537","value":"100.0"},"data":{"cancelable":false}},{"uuid":"4da6df04-73ff-4725-a282-8c6973fb65c6","account_id":32991372,"pair":"USDT_XRP","created_at":1738765553,"closed_at":1738765556,"order":{"type":"limit","side":"sell","strategy":"gtc","position_side":"both","reduce_only":false},"units":{"value":"4.015609"},"price":{"value":"2.5392"},"total":{"value":"10.1964343728"},"conditional":{"enabled":false,"value":null,"price":{"value":null,"type":null}},"trailing":{"enabled":false,"value":null,"percent":null},"timeout":{"enabled":false,"value":null},"leverage":{"type":null},"status":{"value":"finished","error":null},"filled":{"units":"3.975452","total":"10.08639214422528","price":"2.5371","value":"100.0"},"data":{"cancelable":false}},{"uuid":"a7aeda63-ef0b-47b2-8ca5-9d3c0d5b0b44","account_id":32991373,"pair":"USDT_XRP-USDT-SWAP","created_at":1749696595,"closed_at":1749696597,"order":{"type":"market","side":"buy","strategy":null,"position_side":"both","reduce_only":false},"units":{"value":"1.0"},"price":{"value":null},"total":{"value":"225.08"},"conditional":{"enabled":false,"value":null,"price":{"value":null,"type":null}},"trailing":{"enabled":false,"value":null,"percent":null},"timeout":{"enabled":false,"value":null},"leverage":{"type":"cross"},"status":{"value":"finished","error":null},"filled":{"units":"1.0","total":"225.222555","price":"2.2522","value":"100.0"},"data":{"cancelable":false}}]}
     */
    public void getTradesHistory() throws Exception {
        log.info("3commas -> getTradesHistory...");
        long timestamp = Instant.now().getEpochSecond();
        String path = "/public/api/ver1/trades/history";
        String payload = ""; // GET-запрос — нет тела

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET, path);

        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .get()
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .addHeader("Timestamp", String.valueOf(timestamp))
                .build();

        System.out.println("Debug:");
        System.out.println("Path: " + path);
        System.out.println("Payload: " + payload);
        System.out.println("Message to sign: " + path);
        System.out.println("Signature: " + signature);

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            System.out.println("📋 Trades History (status " + response.code() + "):");
            System.out.println(responseBody);
        }
    }

    public TradeResponse createTrade(
            long accountId,
            String pair,
            String orderType,     // "market" или "limit"
            String side,          // "buy" или "sell"
            double unitsValue,
            boolean leverageEnabled,
            String leverageType,  // "cross" или "isolated"
            boolean conditional,
            boolean trailing,
            boolean timeout
    ) throws Exception {
        log.info("3commas -> createTrade...");
        String path = "/public/api/ver1/trades";
        String url = BASE_URL + path;

        // Создаём payload
        TradePayload payload = new TradePayload();
        payload.setAccountId(accountId);
        payload.setPair(pair);
        payload.setOrder(new Order(orderType, side));
        payload.setUnits(new Units(unitsValue));
        payload.setLeverage(new Leverage(leverageEnabled, leverageType));
        payload.setEnabled(true);
        payload.setConditional(new Flag(conditional));
        payload.setTrailing(new Flag(trailing));
        payload.setTimeout(new Flag(timeout));

        // Сериализуем payload
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String payloadJson = MAPPER.writeValueAsString(payload);

        // Подпись
        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET, path + payloadJson);

        RequestBody body = RequestBody.create(
                payloadJson, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            log.info("📋 Trade Creation (status " + response.code() + "):");
            String responseBody = response.body().string();
            log.info(responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Cancel failed: " + response);
            }

            // Используй уже считанную строку
            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            TradeResponse tradeResponse = MAPPER.readValue(responseBody, TradeResponse.class);
            log.info("Trade: " + tradeResponse);

            return tradeResponse;
        }
    }

    public ActiveTradesResponse getActiveTrades() throws Exception {
        log.info("3commas -> getActiveTrades...");
        String path = "/public/api/ver1/trades";
        String url = BASE_URL + path;

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET, path);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .build();

        try (Response response = client.newCall(request).execute()) {
            log.info("📋 Get Active Trades (status " + response.code() + "):");
            String responseBody = response.body().string();
            log.info("responseBody: " + responseBody);

            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // Десериализация через обёртку
            ActiveTradesResponse activeTradesResponse = MAPPER.readValue(responseBody, ActiveTradesResponse.class);
            log.info("Active trades count: " + activeTradesResponse.getTrades().size());
            return activeTradesResponse;

        }
    }


    public void cancelTrade(String tradeId) throws Exception {
        log.info("3commas -> cancelTrade...");
        String path = "/public/api/ver1/trades/" + tradeId + "/cancel";
        String url = BASE_URL + path;

        String payload = "";
        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET, path + payload);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, MediaType.parse("application/json")))
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("📴 Cancel Trade (status " + response.code() + "):");
            log.info(responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Cancel failed: " + responseBody);
            }

            // Можно десериализовать результат, если нужно:
            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            TradeResponse tradeResponse = MAPPER.readValue(responseBody, TradeResponse.class);
            log.info("TradeResponse after cancel tradeResponse: " + tradeResponse);
        }
    }


    public TradeResponse getTradeByUuid(String tradeUuid) throws Exception {
        log.info("3commas -> getTradeByUuid...");
        String path = "/public/api/ver1/trades/" + tradeUuid;
        String url = BASE_URL + path;

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET, path);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .build();

        try (Response response = client.newCall(request).execute()) {
            log.info("📋 Get Trade (status " + response.code() + "):");
            String responseBody = response.body().string();
            log.info("responseBody: " + responseBody);

            MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            TradeResponse tradeResponse = MAPPER.readValue(responseBody, TradeResponse.class);

            log.info("TradeResponse опсле маппинга: " + tradeResponse.toString());
            return tradeResponse;
        }
    }
}
