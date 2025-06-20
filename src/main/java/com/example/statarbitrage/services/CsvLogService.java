package com.example.statarbitrage.services;

import com.example.statarbitrage.model.PairData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.example.statarbitrage.constant.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvLogService {

    public void logOrUpdatePair(PairData pairData) {
        try {
            Path path = Paths.get(TEST_TRADES_CSV_FILE);
            Files.createDirectories(path.getParent());

            List<String> lines = new ArrayList<>();
            String key = pairData.getLongTicker() + "," + pairData.getShortTicker();
            String newRow = getRowForCsv(pairData);
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


    private static String getRowForCsv(PairData pairData) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();

        String profitCurr = String.format("%.2f", pairData.getProfitChanges());
        String minProfit = String.format("%.2f", pairData.getMinProfitRounded());
        String timeToMin = pairData.getTimeInMinutesSinceEntryToMin() + "min";
        String maxProfit = String.format("%.2f", pairData.getMaxProfitRounded());
        String timeToMax = pairData.getTimeInMinutesSinceEntryToMax() + "min";

        String longCurr = String.format("%.2f", pairData.getLongChanges());
        String longMin = String.format("%.2f", pairData.getMinLong());
        String longMax = String.format("%.2f", pairData.getMaxLong());

        String shortCurr = String.format("%.2f", pairData.getShortChanges());
        String shortMin = String.format("%.2f", pairData.getMinShort());
        String shortMax = String.format("%.2f", pairData.getMaxShort());

        String zCurr = String.format("%.2f", pairData.getZScoreCurrent());
        String zMin = String.format("%.2f", pairData.getMinZ());
        String zMax = String.format("%.2f", pairData.getMaxZ());

        String corrCurr = String.format("%.2f", pairData.getCorrelationCurrent());
        String corrMin = String.format("%.2f", pairData.getMinCorr());
        String corrMax = String.format("%.2f", pairData.getMaxCorr());

        String exitReason = pairData.getExitReason() != null && !pairData.getExitReason().isEmpty() ? pairData.getExitReason() : "";

        String entryTime = Instant.ofEpochMilli(pairData.getEntryTime())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));

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

                exitReason,

                entryTime,

                timestamp);
    }

}
