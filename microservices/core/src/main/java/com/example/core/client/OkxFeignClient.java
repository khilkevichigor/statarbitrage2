package com.example.core.client;

import com.example.shared.dto.okx.OkxTickerDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "okx-service", url = "${okx.service.url:http://localhost:8088}")
public interface OkxFeignClient {

    @GetMapping("/api/okx/ticker")
    OkxTickerDto getTicker(@RequestParam String symbol);

    @GetMapping("/api/okx/tickers")
    List<String> getAllSwapTickers(@RequestParam(defaultValue = "false") boolean sorted);
}