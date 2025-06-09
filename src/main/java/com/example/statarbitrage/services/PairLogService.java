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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairLogService {
    private static final String CSV_HEADER = "Long,Short,Profit %,LongCh %,ShortCh %,Z,Corr,MaxProfit %,TimeToMax min,MinProfit %,TimeToMin min,Timestamp";
    private static final String FILE_PATH = "logs/pairs.csv";

    public void logOrUpdatePair(PairData pairData) {
        try {
            Path path = Paths.get(FILE_PATH);
            Files.createDirectories(path.getParent());

            List<String> lines = new ArrayList<>();
            boolean updated = false;

            String key = pairData.getLongTicker() + "," + pairData.getShortTicker();
            String newRow = getRowForCsv(pairData);

            if (Files.exists(path)) {
                List<String> existingLines = Files.readAllLines(path);
                for (int i = 0; i < existingLines.size(); i++) {
                    String line = existingLines.get(i);
                    if (line.equals(CSV_HEADER)) {
                        lines.add(line); // заголовок
                    } else if (line.startsWith(key)) {
                        lines.add(newRow); // обновляем
                        updated = true;
                    } else {
                        lines.add(line);
                    }
                }
            }

            if (!updated) {
                if (lines.isEmpty()) {
                    lines.add(CSV_HEADER);
                }
                lines.add(newRow); // добавляем новую
            }

            Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            log.error("Ошибка при обновлении CSV: ", e);
        }
    }

    private static String getRowForCsv(PairData pairData) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();
        String profit = String.format("%.2f", pairData.getProfitChanges());
        String longCh = String.format("%.2f", pairData.getLongChanges());
        String shortCh = String.format("%.2f", pairData.getShortChanges());
        String z = String.format("%.2f", pairData.getZScoreCurrent());
        String corr = String.format("%.2f", pairData.getCorrelationCurrent());
        String maxProfit = String.format("%.2f", pairData.getMaxProfitRounded());
        String timeToMax = pairData.getTimeInMinutesSinceEntryToMax() + "min";
        String minProfit = String.format("%.2f", pairData.getMinProfitRounded());
        String timeToMin = pairData.getTimeInMinutesSinceEntryToMin() + "min";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        ;

        return String.join(",", longTicker, shortTicker, profit, longCh, shortCh, z, corr, maxProfit, timeToMax, minProfit, timeToMin, timestamp);
    }

}
