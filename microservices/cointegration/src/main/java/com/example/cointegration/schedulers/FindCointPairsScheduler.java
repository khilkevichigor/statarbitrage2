package com.example.cointegration.schedulers;

import com.example.cointegration.messaging.SendEventService;
import com.example.cointegration.processors.FetchCointPairsProcessor;
import com.example.cointegration.service.SettingsService;
import com.example.shared.events.rabbit.CointegrationEvent;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FindCointPairsScheduler {
    private final SettingsService settingsService;
    private final FetchCointPairsProcessor fetchCointPairsProcessor;
    private final SendEventService sendEventService;

    //todo 1раз/мин но если ТФ=15ми то 15 раз один и тот же лог с тем же zScore! Можно добавить условие по ТФ из настроек и пропускать лишние заходы шедуллера
    @Scheduled(cron = "0 */1 * * * *")
    public void maintainCointPairs() {
        long schedulerStart = System.currentTimeMillis();

        log.info("🔄 Шедуллер поиска коинтегрированных пар запущен...");
        Settings settings = settingsService.getSettings();
        if (settings == null || !settings.isAutoTradingEnabled()) {
            return;
        }

        log.info("🆕 Начинаем отбор...");
        List<Pair> cointPairs = fetchCointPairsProcessor.fetchCointPairs();
        if (cointPairs.isEmpty()) {
            log.warn("⚠️ Отобрано 0 пар!");
            return;
        }
        log.info("Отобрано {} пар", cointPairs.size());

        long duration = System.currentTimeMillis() - schedulerStart;
        log.info("⏱️ Шедуллер поиска коинтегрированных пар закончил работу за {} сек. Найдено {} новых пар", duration / 1000.0, cointPairs.size());

        if (!cointPairs.isEmpty()) {
            log.info("");
            log.info("Отправка найденных пар в сore мс...");
            sendEventService.sendCointegrationEvent(new CointegrationEvent(cointPairs, CointegrationEvent.Type.NEW_COINT_PAIRS));
            log.info("Пары отправлены успешно.");
        }
    }
}