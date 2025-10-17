package com.example.okx.controller;

import com.example.okx.service.OkxClient;
import com.example.shared.dto.Candle;
import com.example.shared.dto.okx.OkxTickerDto;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/okx")
@RequiredArgsConstructor
public class OkxController {

    private final OkxClient okxClient;

    /**
     * Получить все SWAP тикеры
     */
    @GetMapping("/tickers")
    public List<String> getAllSwapTickers(@RequestParam(defaultValue = "false") boolean sorted) {
        return okxClient.getAllSwapTickers(sorted);
    }

    /**
     * Получить свечи по символу
     */
    @GetMapping("/candles")
    public List<Candle> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "15m") String timeFrame,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return okxClient.getCandleList(symbol, timeFrame, limit);
    }

    /**
     * Получить карту свечей для списка тикеров
     */
    @PostMapping("/candles/map")
    public Map<String, List<Candle>> getCandlesMap(
            @RequestBody List<String> symbols,
            @RequestParam(defaultValue = "15m") String timeFrame,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean sorted
    ) {
        Settings settings = new Settings();
        settings.setTimeframe(timeFrame);
        settings.setCandleLimit(limit);
        return okxClient.getCandlesMap(symbols, settings, sorted);
    }

    /**
     * Получить текущую цену
     */
    @GetMapping("/price")
    public BigDecimal getCurrentPrice(@RequestParam String symbol) {
        return okxClient.getCurrentPrice(symbol);
    }

    /**
     * Получить валидные тикеры (по объёму)
     */
    @PostMapping("/tickers/valid")
    public List<String> getValidTickers(
            @RequestBody List<String> symbols,
            @RequestParam(defaultValue = "15m") String timeFrame,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "1000000") double minQuoteVolume,
            @RequestParam(defaultValue = "false") boolean sorted
    ) {
        log.info("Получен реквест timeFrame={}, limit={}, minQuoteVolume={}, sorted={}", timeFrame, limit, minQuoteVolume, sorted);
        return okxClient.getValidTickersV2(symbols, timeFrame, limit, minQuoteVolume, sorted);
    }

    @PostMapping("/tickers/valid-by-volume")
    public List<String> getValidTickersByVolume(
            @RequestParam(defaultValue = "1000000") double minQuoteVolume,
            @RequestParam(defaultValue = "false") boolean sorted
    ) {
        log.info("Получен реквест minQuoteVolume={}, sorted={}", minQuoteVolume, sorted);
        return okxClient.getValidTickersByVolume(minQuoteVolume, sorted);
    }

    /**
     * Получить тикер по символу (DTO)
     */
    @GetMapping("/ticker")
    public OkxTickerDto getTicker(@RequestParam String symbol) {
        try {
            return okxClient.getTickerDto(symbol);
        } catch (Exception e) {
            log.error("❌ Ошибка при получении тикера для {}: {}", symbol, e.getMessage(), e);
            throw new RuntimeException("Ошибка при получении тикера для " + symbol + ": " + e.getMessage(), e);
        }
    }

    /**
     * Получить исторические свечи с параметром before для пагинации
     */
    @GetMapping("/candles/before")
    public List<Candle> getCandlesBefore(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "15m") String timeFrame,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam long before
    ) {
        return okxClient.getCandlesWithBefore(symbol, timeFrame, limit, before);
    }
}
