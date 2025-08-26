package com.example.candles.controller;

import com.example.candles.service.CandlesService;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.models.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/candles")
@RequiredArgsConstructor
public class CandlesController {

    private final CandlesService candlesService;

    @PostMapping("/applicable-map")
    public Map<String, List<Candle>> getApplicableCandlesMap(@RequestBody CandlesRequest request) {
        if (request.isUsePairData()) {
            return candlesService.getApplicableCandlesMap(request.getTradingPair(), request.getSettings());
        } else {
            return candlesService.getApplicableCandlesMap(request.getSettings(), request.getTradingTickers());
        }
    }
}