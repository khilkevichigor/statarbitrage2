package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.TradeLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static com.example.statarbitrage.constant.Constants.TEST_TRADES_CSV_FILE;
import static com.example.statarbitrage.constant.Constants.TEST_TRADES_CSV_FILE_HEADER;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvLogService {
    private final SettingsService settingsService;

    public void logOrUpdatePair(TradeLog tradeLog) {
        try {
            Settings settings = settingsService.getSettingsFromJson();
            Path path = Paths.get(TEST_TRADES_CSV_FILE);
            Files.createDirectories(path.getParent());

            List<String> lines = new ArrayList<>();
            String key = tradeLog.getLongTicker() + "," + tradeLog.getShortTicker();
            String newRow = getRowForCsv(tradeLog);
            boolean updated = false;

            if (Files.exists(path)) {
                List<String> existingLines = Files.readAllLines(path);

                for (int i = 0; i < existingLines.size(); i++) {
                    String line = existingLines.get(i);
                    if (line.equals(TEST_TRADES_CSV_FILE_HEADER)) {
                        lines.add(line); // заголовок
                        continue;
                    }

                    // Если это последняя строка по паре — заменим
                    if (line.startsWith(key)) {
                        // ищем, есть ли такая же пара дальше
                        boolean isLastMatching = true;
                        for (int j = i + 1; j < existingLines.size(); j++) {
                            if (existingLines.get(j).startsWith(key)) {
                                isLastMatching = false;
                                break;
                            }
                        }

                        if (isLastMatching) {
                            lines.add(newRow); // заменяем последнюю
                            updated = true;
                        } else {
                            lines.add(line); // оставляем как есть
                        }
                    } else {
                        lines.add(line); // не связанная пара
                    }
                }
            }

            if (!updated) {
                if (lines.isEmpty()) {
                    lines.add(TEST_TRADES_CSV_FILE_HEADER); // добавим заголовок если файл был пуст
                }
                lines.add(newRow); // добавим новую строку
            }

            Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("Ошибка при обновлении CSV: ", e);
        }
    }


    private static String getRowForCsv(TradeLog tradeLog) {
        String longTicker = tradeLog.getLongTicker();
        String shortTicker = tradeLog.getShortTicker();

        String profitCurr = String.format("%.2f", tradeLog.getCurrentProfitPercent());
        String minProfit = String.format("%.2f", tradeLog.getMinProfitPercent());
        String timeToMin = tradeLog.getMinProfitMinutes();
        String maxProfit = String.format("%.2f", tradeLog.getMaxProfitPercent());
        String timeToMax = tradeLog.getMaxProfitMinutes();

        String longCurr = String.format("%.2f", tradeLog.getCurrentLongPercent());
        String longMin = String.format("%.2f", tradeLog.getMinLongPercent());
        String longMax = String.format("%.2f", tradeLog.getMaxLongPercent());

        String shortCurr = String.format("%.2f", tradeLog.getCurrentShortPercent());
        String shortMin = String.format("%.2f", tradeLog.getMinShortPercent());
        String shortMax = String.format("%.2f", tradeLog.getMaxShortPercent());

        String zCurr = String.format("%.2f", tradeLog.getCurrentZ());
        String zMin = String.format("%.2f", tradeLog.getMinZ());
        String zMax = String.format("%.2f", tradeLog.getMaxZ());

        String corrCurr = String.format("%.2f", tradeLog.getCurrentCorr());
        String corrMin = String.format("%.2f", tradeLog.getMinCorr());
        String corrMax = String.format("%.2f", tradeLog.getMaxCorr());

        String exitStop = String.format("%.2f", tradeLog.getExitStop());
        String exitTake = String.format("%.2f", tradeLog.getExitTake());
        String exitZMin = String.format("%.2f", tradeLog.getExitZMin());
        String exitZMax = String.format("%.2f", tradeLog.getExitZMax());
        String exitTimeHours = String.format("%.2f", tradeLog.getExitTimeHours());

        String exitReason = tradeLog.getExitReason() != null && !tradeLog.getExitReason().isEmpty() ? tradeLog.getExitReason() : "";

        String entryTime = tradeLog.getEntryTime();

        String timestamp = tradeLog.getTimestamp();

        return String.join(",",
                longTicker,
                shortTicker,

                profitCurr,
                minProfit, timeToMin,
                maxProfit, timeToMax,

                longCurr,
                longMin,
                longMax,

                shortCurr,
                shortMin,
                shortMax,

                zCurr,
                zMin,
                zMax,

                corrCurr,
                corrMin,
                corrMax,

                exitStop,
                exitTake,
                exitZMin,
                exitZMax,
                exitTimeHours,

                exitReason,

                entryTime,

                timestamp);
    }

}
