package com.example.cointegration.schedulers;

import com.example.cointegration.messaging.SendEventService;
import com.example.cointegration.processors.FetchCointPairsProcessor;
import com.example.cointegration.repositories.TradingPairRepository;
import com.example.cointegration.service.CointPairService;
import com.example.cointegration.service.SettingsService;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.events.CointegrationEvent;
import com.example.shared.models.CointPair;
import com.example.shared.models.Settings;
import com.example.shared.models.TradeStatus;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FindCointPairsScheduler {
    private final SettingsService settingsService;
    private final CointPairService cointPairService;
    private final TradingPairRepository tradingPairRepository;
    private final FetchCointPairsProcessor fetchCointPairsProcessor;
    private final SendEventService sendEventService;

    //todo 1раз/мин но если ТФ=15ми то 15 раз один и тот же лог с тем же zScore! Можно добавить условие по ТФ из настроек и пропускать лишние заходы шедуллера
    @Scheduled(cron = "0 */1 * * * *")
    public void maintainCointPairs() {
        long schedulerStart = System.currentTimeMillis();
        List<CointPair> cointPairs = fetchCointPairs();

        long duration = System.currentTimeMillis() - schedulerStart;
        log.info("⏱️ Шедуллер поиска коинтегрированных пар закончил работу за {} сек. Найдено {} новых пар", duration / 1000.0, cointPairs.size());

        if (!cointPairs.isEmpty()) {
            log.info("Отправка найденных пар в сore мс...");
            sendEventService.sendCointegrationEvent(new CointegrationEvent(cointPairs, CointegrationEvent.Type.NEW_COINT_PAIRS));
            log.info("Пары отправлены успешно.");
        }
    }

    private List<CointPair> fetchCointPairs() {
        log.info("🔄 Шедуллер поиска коинтегрированных пар запущен...");
        Settings settings = settingsService.getSettings();
        if (settings == null || !settings.isAutoTradingEnabled()) {
            return Collections.emptyList();
        }
        int missingCointPairs = calculateMissingCointPairs(settings);
        if (missingCointPairs <= 0) {
            return Collections.emptyList();
        }
        return createNewCointPairs(missingCointPairs);
    }

    private int calculateMissingCointPairs(Settings settings) {
        try {
            //todo брать все коинт пары отфильтрованные по zScore и отдавать в Core, а там фильтровать по монетам - разделение ответственности - лучше не мешать обязанности!
            List<TradingPair> tradingPairs = tradingPairRepository.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            int maxActive = (int) settings.getUsePairs();
            int currentActive = tradingPairs.size();
            return maxActive - currentActive;
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете недостающих пар: {}", e.getMessage());
            return 0;
        }
    }

    private List<CointPair> createNewCointPairs(int missingCointPairs) {
        log.info("🆕 Не хватает {} пар — начинаем подбор", missingCointPairs);
//        cleanupOldCointPairs(); //переместил ближе к созданию ноывых что бы небыло пусто 30сек
        List<CointPair> newCointPairs = fetchNewCointPairs(missingCointPairs);
        if (newCointPairs.isEmpty()) {
            log.warn("⚠️ Отобрано 0 пар!");
            return Collections.emptyList();
        }
        log.info("Отобрано {} пар", newCointPairs.size());

        return newCointPairs;
    }

    private void cleanupOldCointPairs() {
        try {
            cointPairService.deleteAllByStatus(TradeStatus.SELECTED);
        } catch (Exception e) {
            log.error("❌ Ошибка при очистке старых пар SELECTED: {}", e.getMessage());
        }
    }

    private List<CointPair> fetchNewCointPairs(int count) {
        try {
            return fetchCointPairsProcessor.fetchCointPairs(FetchPairsRequest.builder()
                    .countOfPairs(count)
                    .build());
        } catch (Exception e) {
            log.error("❌ Ошибка при поиске новых пар: {}", e.getMessage());
            return List.of();
        }
    }
}