package com.example.statarbitrage.api.threecommas;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeCommasClient {

    private static final String API_KEY = "59690761f0314ea1b3e5a034dbe91d29501546e7e99548e2a9e8c773f089c25b";
    private static final String API_SECRET = "24c4bbec59bfd6182821493a2ecaea6bba43d13f67d7e6892059c0891048761cb9f6290a7e0d0a0a2c2969ebcd76c93c9152fd519027c12ebf93f4af1315b2a85e37bfe94b79cee71d25046140aad7f188fab5d67183c70520519f6a879b90d81df881d4";
    private static final String BASE_URL = "https://api.3commas.io";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient();

    public void validateCredentials() throws Exception {
        log.info("3commas -> validateCredentials...");
        String path = "/public/api/ver1/validate";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        // Подпись
        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET, path);

        String url = BASE_URL + path;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("APIKEY", API_KEY)
                .addHeader("Signature", signature)
                .addHeader("Timestamp", timestamp)
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("📋 Validate (status " + response.code() + "):");
            System.out.println("Headers: " + response.headers());
            String responseBody = response.body().string();
            System.out.println("Body: " + responseBody);
        }
    }

    public void getAccounts() throws Exception {
        log.info("3commas -> getAccounts...");
        long timestamp = Instant.now().getEpochSecond();
        String path = "/public/api/ver1/accounts";
        String payload = ""; // GET-запрос — без тела

        String signature = ThreeCommasClientsUtil.hmacSHA256(API_SECRET, path);

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
}
