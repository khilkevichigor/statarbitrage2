package com.example.statarbitrage.services;

import com.example.statarbitrage.model.threecommas.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeCommasService {

    private static final String API_KEY = "59690761f0314ea1b3e5a034dbe91d29501546e7e99548e2a9e8c773f089c25b";
    private static final String API_SECRET = "24c4bbec59bfd6182821493a2ecaea6bba43d13f67d7e6892059c0891048761cb9f6290a7e0d0a0a2c2969ebcd76c93c9152fd519027c12ebf93f4af1315b2a85e37bfe94b79cee71d25046140aad7f188fab5d67183c70520519f6a879b90d81df881d4";
    private static final String BASE_URL = "https://api.3commas.io";
    private static final long SPOT_ACCOUNT_ID = 32991372;
    private static final long FUTURES_ACCOUNT_ID = 32991373;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    public void test() {
        try {
//            validateCredentials();
//            getBotsList();
//            getTradesHistory();
//            getAccounts();
//            createSimpleMarketTrade();
            createFuturesXrpBuyMarketTrade();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void validateCredentials() throws Exception {
        String path = "/public/api/ver1/validate";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        // Подпись
        String signature = hmacSHA256(API_SECRET, path);

        String url = BASE_URL + path;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .addHeader("Timestamp", timestamp)
                .build();

        System.out.println("🔍 Validate API Key");
        System.out.println("Message to sign: " + path);
        System.out.println("Signature: " + signature);

        try (Response response = client.newCall(request).execute()) {
            System.out.println("📋 Validate (status " + response.code() + "):");
            System.out.println("Headers: " + response.headers());
            String responseBody = response.body().string();
            System.out.println("Body: " + responseBody);
        }
    }

    // 📌 Получить всех ботов (можно фильтровать по type=paper или real)
    public void getBotsList() throws Exception {
        String path = "/public/api/ver1/bots";
        String url = BASE_URL + path;

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        // Формирование подписи
        String signature = hmacSHA256(API_SECRET, path);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY)
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

    /*
    {"trades":[{"uuid":"76a61de9-1cae-4273-923d-1e2a3c48737a","account_id":32991372,"pair":"USDT_XRP","created_at":1738765497,"closed_at":1738765500,"order":{"type":"market","side":"sell","strategy":null,"position_side":"both","reduce_only":false},"units":{"value":"401.560887"},"price":{"value":null},"total":{"value":"1019.563092093"},"conditional":{"enabled":false,"value":null,"price":{"value":null,"type":null}},"trailing":{"enabled":false,"value":null,"percent":null},"timeout":{"enabled":false,"value":null},"leverage":{"type":null},"status":{"value":"finished","error":null},"filled":{"units":"397.545278","total":"1008.5750701795655719","price":"2.537","value":"100.0"},"data":{"cancelable":false}},{"uuid":"4da6df04-73ff-4725-a282-8c6973fb65c6","account_id":32991372,"pair":"USDT_XRP","created_at":1738765553,"closed_at":1738765556,"order":{"type":"limit","side":"sell","strategy":"gtc","position_side":"both","reduce_only":false},"units":{"value":"4.015609"},"price":{"value":"2.5392"},"total":{"value":"10.1964343728"},"conditional":{"enabled":false,"value":null,"price":{"value":null,"type":null}},"trailing":{"enabled":false,"value":null,"percent":null},"timeout":{"enabled":false,"value":null},"leverage":{"type":null},"status":{"value":"finished","error":null},"filled":{"units":"3.975452","total":"10.08639214422528","price":"2.5371","value":"100.0"},"data":{"cancelable":false}},{"uuid":"a7aeda63-ef0b-47b2-8ca5-9d3c0d5b0b44","account_id":32991373,"pair":"USDT_XRP-USDT-SWAP","created_at":1749696595,"closed_at":1749696597,"order":{"type":"market","side":"buy","strategy":null,"position_side":"both","reduce_only":false},"units":{"value":"1.0"},"price":{"value":null},"total":{"value":"225.08"},"conditional":{"enabled":false,"value":null,"price":{"value":null,"type":null}},"trailing":{"enabled":false,"value":null,"percent":null},"timeout":{"enabled":false,"value":null},"leverage":{"type":"cross"},"status":{"value":"finished","error":null},"filled":{"units":"1.0","total":"225.222555","price":"2.2522","value":"100.0"},"data":{"cancelable":false}}]}
     */
    public void getTradesHistory() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String path = "/public/api/ver1/trades/history";
        String payload = ""; // GET-запрос — нет тела

        String signature = hmacSHA256(API_SECRET, path);

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

    public void getAccounts() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String path = "/public/api/ver1/accounts";
        String payload = ""; // GET-запрос — без тела

        String signature = hmacSHA256(API_SECRET, path);

        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .get()
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .addHeader("Timestamp", String.valueOf(timestamp))
                .build();

        System.out.println("🔍 Debug:");
        System.out.println("Path: " + path);
        System.out.println("Payload: " + payload);
        System.out.println("Message to sign: " + path);
        System.out.println("Signature: " + signature);

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            System.out.println("📋 Accounts (status " + response.code() + "):");
            System.out.println(responseBody);
        }
    }

    public void createSimpleMarketTrade() throws Exception {
        String path = "/public/api/ver1/trades";
        String url = BASE_URL + path;

        // Создаём payload
        TradePayload payload = new TradePayload();
        payload.setAccountId(SPOT_ACCOUNT_ID);
        payload.setPair("XRP_USDT");
        payload.setOrder(new Order("market", "buy"));
        payload.setUnits(new Units(2));
        payload.setLeverage(new Leverage(false, "cross"));
        payload.setEnabled(true);
        payload.setConditional(new Flag(false));
        payload.setTrailing(new Flag(false));
        payload.setTimeout(new Flag(false));

        // Сериализуем payload
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String payloadJson = objectMapper.writeValueAsString(payload);

        // Подпись
        String signature = hmacSHA256(API_SECRET, path + payloadJson);

        RequestBody body = RequestBody.create(
                payloadJson, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        System.out.println("🔫 Sending Market Trade...");
        System.out.println("Message to sign: " + path);
        System.out.println("Signature: " + signature);

        //todo Получаю 500 {"error":"unknown_error","error_description":"Неизвестная ошибка#NoMethodError"}
        try (Response response = client.newCall(request).execute()) {
            System.out.println("📋 Trade Creation (status " + response.code() + "):");
            System.out.println(response.body().string());
        }
    }

    //рабочий метод
    public void createFuturesXrpBuyMarketTrade() throws Exception {
        String path = "/public/api/ver1/trades";
        String url = BASE_URL + path;

        String payload = """
                {
                  "account_id": 32991373,
                  "pair": "USDT_XRP-USDT-SWAP",
                  "order": {
                    "type": "market",
                    "side": "buy",
                    "position_side": "both",
                    "reduce_only": false
                  },
                  "units": {
                    "value": 2
                  },
                  "leverage": {
                    "type": "cross"
                  },
                  "conditional": {
                    "enabled": false
                  },
                  "trailing": {
                    "enabled": false
                  },
                  "timeout": {
                    "enabled": false
                  }
                }
                """;

        String signature = hmacSHA256(API_SECRET, path + payload);

        RequestBody body = RequestBody.create(payload, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build();

        System.out.println("📤 Sending futures market buy order for 2 XRP...");
        System.out.println("Signature: " + signature);

        try (Response response = client.newCall(request).execute()) {
            System.out.println("📋 Trade Creation (status " + response.code() + "):");
            System.out.println(response.body().string());
        }
    }

    private static String hmacSHA256(String secret, String message) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
