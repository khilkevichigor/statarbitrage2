package com.example.statarbitrage.services;

import com.example.statarbitrage.model.UserSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class SettingsService {
    private static final String SETTINGS_FILE = "user-settings.json";
    private final ObjectMapper mapper = new ObjectMapper();
    private Map<Long, UserSettings> userSettings = new HashMap<>();

    public SettingsService() {
        loadSettings();
    }

    public UserSettings getSettings(long chatId) {
        if (!userSettings.containsKey(chatId)) {
            UserSettings defaultSettings = getDefaultSettings();
            userSettings.put(chatId, defaultSettings);
            saveSettings();
        }
        return userSettings.get(chatId);
    }

    private static UserSettings getDefaultSettings() {
        return UserSettings.builder()
                .priceChangePeriodHrs(24.0)
                .topGainersLosersQnty(10)

                .volume(UserSettings.VolumeSettings.builder()
                        .thresholdMln(50.0)
                        .build())
                .correlation(UserSettings.CorrelationSettings.builder()
                        .btcCorrPeriodHrs(1.0)
                        .build())
                .htf(UserSettings.TimeFrameSettings.builder()
                        .tfName("15m")
                        .ema1Period(9.0)
                        .ema2Period(21.0)
                        .stochRsi(UserSettings.StochRsiSettings.builder()
                                .rsiPeriod(14.0)
                                .stochPeriod(14.0)
                                .kPeriod(3.0)
                                .dPeriod(2.0)
                                .oversold(20.0)
                                .overbought(80.0)
                                .build())
                        .rsi(UserSettings.RsiSettings.builder()
                                .rsiPeriod(14.0)
                                .oversold(30.0)
                                .overbought(70.0)
                                .build())
                        .build())
                .ltf(UserSettings.TimeFrameSettings.builder()
                        .tfName("1m")
                        .ema1Period(9.0)
                        .ema2Period(21.0)
                        .stochRsi(UserSettings.StochRsiSettings.builder()
                                .rsiPeriod(14.0)
                                .stochPeriod(14.0)
                                .kPeriod(3.0)
                                .dPeriod(2.0)
                                .oversold(20.0)
                                .overbought(80.0)
                                .build())
                        .rsi(UserSettings.RsiSettings.builder()
                                .rsiPeriod(14.0)
                                .oversold(30.0)
                                .overbought(70.0)
                                .build())
                        .build())
                .emaAngles(UserSettings.AngleSettings.builder()
                        .ema1Min(1.0)
                        .ema1Max(10.0)
                        .ema2Min(1.0)
                        .ema2Max(10.0)
                        .build())
                .distances(UserSettings.DistanceSettings.builder()
                        .priceToEma1Min(0.1)
                        .priceToEma1Max(10.0)
                        .priceToEma2Min(0.1)
                        .priceToEma2Max(10.0)
                        .ema1ToEma2Min(0.1)
                        .ema1ToEma2Max(10.0)
                        .build())
                .autoScan(UserSettings.AutoScanSettings.builder()
                        .intervalSec(60.0)
                        .build())

                .useRenko(false)
                .useRandomizer(false)
                .useLong(true)
                .useShort(true)
                .useVolume(false)
                .useTopGainersLosers(false)
                .useCurrBar(false)
                .sendNewMessageOnAutoScan(false)

                .useEma1(false)
                .usePriceToEma1(false)
                .useEma1Angle(false)

                .useEma2(false)
                .usePriceToEma2(false)
                .useEma2Angle(false)

                .useEma1ToEma2(false)
                .usePriceCrossEmas(false)

                .useHtfStochRsi(false)
                .useLtfStochRsi(false)
                .useHtfStochRsiLevel(false)
                .useLtfStochRsiLevel(false)
                .useHtfStochRsiCross(false)
                .useLtfStochRsiCross(false)
                .useStochRsiDiver(false)
                .useTrendBreak(false)
                .useHtfRsi(false)
                .useLtfRsi(false)

                .build();
    }

    public void updateAllSettings(long chatId, UserSettings newSettings) {
        userSettings.put(chatId, newSettings);
        saveSettings();
    }

    private void loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (file.exists()) {
            try {
                userSettings = mapper.readValue(file, new TypeReference<>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveSettings() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(SETTINGS_FILE), userSettings);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void resetSettings(long chatId) {
        UserSettings defaultSettings = getDefaultSettings(); // с дефолтными значениями
        userSettings.put(chatId, defaultSettings);
        saveSettings();
    }

}
