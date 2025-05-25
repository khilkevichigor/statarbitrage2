package com.example.statarbitrage.experemental.chatgpt;

import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class OkxTrader {

    private static final String API_KEY = "YOUR_API_KEY";
    private static final String API_SECRET = "YOUR_API_SECRET";
    private static final String API_PASSPHRASE = "YOUR_API_PASSPHRASE";

    private static final OkHttpClient client = new OkHttpClient();
    private static final String BASE_URL = "https://www.okx.com";

    public enum Direction {
        LONG, SHORT
    }

    public static void main(String[] args) throws Exception {
        openPosition("BTC-USDT-SWAP", 60000.0, 0.01, Direction.LONG);
    }

    public static void openPosition(String instId, double price, double size, Direction direction) throws Exception {
        // Шаг 1: Рыночный ордер
        String side = (direction == Direction.LONG) ? "buy" : "sell";

        String marketOrderJson = String.format("""
                {
                    "instId": "%s",
                    "tdMode": "isolated",
                    "side": "%s",
                    "ordType": "market",
                    "sz": "%.4f"
                }
                """, instId, side, size);

        sendPost("/api/v5/trade/order", marketOrderJson);

        // Шаг 2: Установка TP/SL
        double tpPrice = (direction == Direction.LONG) ? price * 1.03 : price * 0.97;
        double slPrice = (direction == Direction.LONG) ? price * 0.99 : price * 1.01;

        String algoOrderJson = String.format("""
                {
                    "instId": "%s",
                    "tdMode": "isolated",
                    "ordType": "oco",
                    "side": "%s",
                    "sz": "%.4f",
                    "tpTriggerPx": "%.2f",
                    "tpOrdPx": "-1",
                    "slTriggerPx": "%.2f",
                    "slOrdPx": "-1"
                }
                """, instId, side.equals("buy") ? "sell" : "buy", size, tpPrice, slPrice);

        sendPost("/api/v5/trade/order-algo", algoOrderJson);
    }

    private static void sendPost(String path, String jsonBody) throws Exception {
        String url = BASE_URL + path;
        String timestamp = Instant.now().toString();
        String sign = sign(timestamp, "POST", path, jsonBody);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("OK-ACCESS-KEY", API_KEY)
                .addHeader("OK-ACCESS-SIGN", sign)
                .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                .addHeader("OK-ACCESS-PASSPHRASE", API_PASSPHRASE)
                .addHeader("OK-ACCESS-KEY-VERSION", "2")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String result = response.body().string();
            System.out.println("Response: " + result);
        }
    }

    private static String sign(String timestamp, String method, String requestPath, String body) throws Exception {
        String preHash = timestamp + method.toUpperCase() + requestPath + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(API_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(preHash.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
