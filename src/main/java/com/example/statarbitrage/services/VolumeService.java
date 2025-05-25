package com.example.statarbitrage.services;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.calculators.VolumeCalculator;
import com.google.gson.JsonArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VolumeService {
    @Autowired
    private OkxClient okxClient;

    public Double getVolume24h(String symbol) {
        JsonArray candles = okxClient.getTicker(symbol);
        return VolumeCalculator.calculate24h(candles);
    }
}