//package com.example.candles.controller;
//
//import com.example.candles.service.CandlesService;
//import com.example.shared.dto.Candle;
//import com.example.shared.dto.ExtendedCandlesRequest;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/candles")
//@RequiredArgsConstructor
//@Slf4j
//public class CandlesController {
//    private final CandlesService candlesService;
//
//    /**
//     * Главный эндпоинт получения свечей!
//     * Берем из КЭША, если нехватает - догружаем и пополняем КЭШ
//     */
//    @Deprecated //todo уже не используется
//    @PostMapping("/all-extended")
//    public Map<String, List<Candle>> getAllCandlesExtended(@RequestBody ExtendedCandlesRequest request) {
//        return candlesService.getAllCandlesExtended(request);
//    }
//}