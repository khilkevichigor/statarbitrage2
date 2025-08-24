//package com.example.core.bot;
//
//import com.example.core.common.model.PairData;
//import com.example.core.notifications.NotificationService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.math.BigDecimal;
//
//@RestController
//@RequestMapping("/test")
//@RequiredArgsConstructor
//public class TelegramTestController {
//    private final NotificationService notificationService;
//
//    @GetMapping("/telegram")
//    public ResponseEntity<String> sendTestMessage() {
//        PairData testPair = new PairData();
//        testPair.setPairName("Test 1INCH-USDT-SWAP/AUCTION-USDT-SWAP");
//        testPair.setUuid("7c7b22e4-2c1a-46c8-b444-b5a42e9dc376");
//
//        notificationService.notifyOpen(testPair);
//
//        testPair.setProfitUSDTChanges(new BigDecimal("0.12"));
//        testPair.setProfitPercentChanges(new BigDecimal("-10.41"));
//        testPair.setExitReason("Выход в ручную");
//
//        notificationService.notifyClose(testPair);
//
//        return ResponseEntity.ok("Message sent");
//    }
//}