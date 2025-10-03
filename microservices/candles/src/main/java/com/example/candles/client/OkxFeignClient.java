package com.example.candles.client;

import com.example.shared.dto.Candle;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@FeignClient(name = "okx-service", url = "${okx.service.url:http://localhost:8088}")
public interface OkxFeignClient {

    @GetMapping("/api/okx/tickers")
    List<String> getAllSwapTickers(@RequestParam(defaultValue = "false") boolean sorted);

    @GetMapping("/api/okx/candles")
    List<Candle> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1m") String timeFrame,
            @RequestParam(defaultValue = "100") int limit
    );

    @PostMapping("/api/okx/candles/map")
    Map<String, List<Candle>> getCandlesMap(
            @RequestBody List<String> symbols,
            @RequestParam(defaultValue = "1m") String timeFrame,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean sorted
    );

    @GetMapping("/api/okx/price")
    BigDecimal getCurrentPrice(@RequestParam String symbol);

    @PostMapping("/api/okx/tickers/valid")
    List<String> getValidTickers(
            @RequestBody List<String> symbols,
            @RequestParam(defaultValue = "1m") String timeFrame,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "1000000") double minQuoteVolume,
            @RequestParam(defaultValue = "false") boolean sorted
    );

    @PostMapping("/api/okx/tickers/valid-by-volume")
    List<String> getValidTickersByVolume(
            @RequestParam(defaultValue = "1000000") double minQuoteVolume,
            @RequestParam(defaultValue = "false") boolean sorted
    );

    /**
     * Получить исторические свечи с параметром before для пагинации
     */
    @GetMapping("/api/okx/candles/before")
    List<Candle> getCandlesBefore(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1m") String timeFrame,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam long before
    );
}