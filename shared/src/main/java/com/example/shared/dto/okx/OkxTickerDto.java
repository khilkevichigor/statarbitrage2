package com.example.shared.dto.okx;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для тикера OKX API
 * Соответствует ответу /api/v5/market/ticker
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkxTickerDto {

    /**
     * Instrument ID (например, BTC-USDT-SWAP)
     */
    private String instId;

    /**
     * Last traded price
     */
    private BigDecimal last;

    /**
     * Last traded size
     */
    private BigDecimal lastSz;

    /**
     * Best ask price
     */
    private BigDecimal askPx;

    /**
     * Best ask size
     */
    private BigDecimal askSz;

    /**
     * Best bid price
     */
    private BigDecimal bidPx;

    /**
     * Best bid size
     */
    private BigDecimal bidSz;

    /**
     * Open price in the past 24 hours
     */
    private BigDecimal open24h;

    /**
     * Highest price in the past 24 hours
     */
    private BigDecimal high24h;

    /**
     * Lowest price in the past 24 hours
     */
    private BigDecimal low24h;

    /**
     * 24h trading volume
     */
    private BigDecimal vol24h;

    /**
     * 24h trading volume in currency
     */
    private BigDecimal volCcy24h;

    /**
     * Timestamp of ticker data
     */
    private Long ts;

    /**
     * Создает OkxTickerDto из JsonArray ответа OKX API
     */
    public static OkxTickerDto fromJsonArray(JsonArray tickerData) {
        if (tickerData == null || tickerData.size() == 0) {
            return null;
        }

        JsonObject ticker = tickerData.get(0).getAsJsonObject();

        return OkxTickerDto.builder()
                .instId(getStringValue(ticker, "instId"))
                .last(getBigDecimalValue(ticker, "last"))
                .lastSz(getBigDecimalValue(ticker, "lastSz"))
                .askPx(getBigDecimalValue(ticker, "askPx"))
                .askSz(getBigDecimalValue(ticker, "askSz"))
                .bidPx(getBigDecimalValue(ticker, "bidPx"))
                .bidSz(getBigDecimalValue(ticker, "bidSz"))
                .open24h(getBigDecimalValue(ticker, "open24h"))
                .high24h(getBigDecimalValue(ticker, "high24h"))
                .low24h(getBigDecimalValue(ticker, "low24h"))
                .vol24h(getBigDecimalValue(ticker, "vol24h"))
                .volCcy24h(getBigDecimalValue(ticker, "volCcy24h"))
                .ts(getLongValue(ticker, "ts"))
                .build();
    }

    private static String getStringValue(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private static BigDecimal getBigDecimalValue(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            String value = obj.get(key).getAsString();
            if (value != null && !value.trim().isEmpty()) {
                try {
                    return new BigDecimal(value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Long getLongValue(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsLong();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}