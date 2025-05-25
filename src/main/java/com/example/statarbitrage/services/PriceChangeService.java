package com.example.statarbitrage.services;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.calculators.PriceChangeCalculator;
import com.google.gson.JsonArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PriceChangeService {
    @Autowired
    private OkxClient okxClient;

    public double get24hPriceChange(String symbol) {
        JsonArray candles = okxClient.getCandles(symbol, "1D", 2);
        return PriceChangeCalculator.calculate(candles);
    }
}