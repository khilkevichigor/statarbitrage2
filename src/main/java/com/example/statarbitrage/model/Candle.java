package com.example.statarbitrage.model;

import com.google.gson.JsonArray;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Candle {
    private ZonedDateTime time;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

    public Candle(ZonedDateTime time, double open, double high, double low, double close, double volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public static Candle fromJsonArray(JsonArray arr) {
        long timestamp = Long.parseLong(arr.get(0).getAsString());
        double open = Double.parseDouble(arr.get(1).getAsString());
        double high = Double.parseDouble(arr.get(2).getAsString());
        double low = Double.parseDouble(arr.get(3).getAsString());
        double close = Double.parseDouble(arr.get(4).getAsString());
        double volume = Double.parseDouble(arr.get(5).getAsString());

        ZonedDateTime time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault());
        return new Candle(time, open, high, low, close, volume);
    }

    // Getters (можно добавить @Getter от Lombok)
    public ZonedDateTime getTime() {
        return time;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public double getVolume() {
        return volume;
    }

    @Override
    public String toString() {
        return "Candle{" +
                "time=" + time +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ", volume=" + volume +
                '}';
    }
}
