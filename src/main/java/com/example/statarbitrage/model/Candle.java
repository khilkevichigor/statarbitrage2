package com.example.statarbitrage.model;

import com.google.gson.JsonArray;

public class Candle {
    private long timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

    public Candle(long timestamp, double open, double high, double low, double close, double volume) {
        this.timestamp = timestamp;
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

        return new Candle(timestamp, open, high, low, close, volume);
    }

    public long getTimestamp() {
        return timestamp;
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
                "time=" + timestamp +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ", volume=" + volume +
                '}';
    }
}
