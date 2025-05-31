package com.example.statarbitrage.processors;

import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.services.CalculateWithOnlyJavaProcessor;
import com.example.statarbitrage.services.CalculateWithPythonProcessor;
import com.example.statarbitrage.services.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenerProcessor {

    private final SettingsService settingsService;
    private final CalculateWithPythonProcessor calculateWithPythonProcessor;
    private final CalculateWithOnlyJavaProcessor calculateWithOnlyJavaProcessor;

    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();

    @Async
    public void testTrade(String chatId) {
        AtomicBoolean isRunning = runningTrades.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("testTrade уже выполняется для chatId = {}", chatId);
            return;
        }

        try {
            Settings settings = settingsService.getSettings();
            if (settings.isWithPython()) {
                calculateWithPythonProcessor.testTrade(chatId);
            } else {
                calculateWithOnlyJavaProcessor.sendBestChart(chatId);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка в testTrade: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }
    }

    @Async
    public void sendBestChart(String chatId) {
        Settings settings = settingsService.getSettings();
        if (settings.isWithPython()) {
            calculateWithPythonProcessor.sendBestChart(chatId);
        } else {
            calculateWithOnlyJavaProcessor.sendBestChart(chatId);
        }
    }
}
