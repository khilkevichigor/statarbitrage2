package com.example.core.common.dto;

import com.google.gson.JsonArray;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class Candle {
    private long timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

    public static Candle fromJsonArray(JsonArray arr) {
        long timestamp = Long.parseLong(arr.get(0).getAsString());
        double open = Double.parseDouble(arr.get(1).getAsString());
        double high = Double.parseDouble(arr.get(2).getAsString());
        double low = Double.parseDouble(arr.get(3).getAsString());
        double close = Double.parseDouble(arr.get(4).getAsString());
        double volume = Double.parseDouble(arr.get(5).getAsString());

        return Candle.builder()
                .timestamp(timestamp)
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(volume)
                .build();
    }

    @Override
    public String toString() {
        return "Candle{" +
                "time=" + timestamp +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ", volume=" + volume +
                '}';
    }
}
