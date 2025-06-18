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

import static com.example.statarbitrage.constant.Constants.TEST_TRADES_CSV_FILE;
import static com.example.statarbitrage.constant.Constants.TEST_TRADES_CSV_FILE_HEADER;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvLogService {

    public void logOrUpdatePair(PairData pairData) {
        try {
            Path path = Paths.get(TEST_TRADES_CSV_FILE);
            Files.createDirectories(path.getParent());

            List<String> lines = new ArrayList<>();
            boolean updated = false;

            String key = pairData.getLongTicker() + "," + pairData.getShortTicker(); //уникальный ключ чтобы не перезатереть
            String newRow = getRowForCsv(pairData);

            if (Files.exists(path)) {
                List<String> existingLines = Files.readAllLines(path);
                for (int i = 0; i < existingLines.size(); i++) {
                    String line = existingLines.get(i);
                    if (line.equals(TEST_TRADES_CSV_FILE_HEADER)) {
                        lines.add(line); // заголовок
                    } else if (line.startsWith(key)) {
                        // проверяем, есть ли ещё такие строки после текущей
                        boolean isLastMatching = true;
                        for (int j = i + 1; j < existingLines.size(); j++) {
                            if (existingLines.get(j).startsWith(key)) {
                                isLastMatching = false;
                                break;
                            }
                        }

                        if (isLastMatching) {
                            // только последнюю строку по key обновляем
                            lines.add(newRow);
                            updated = true;
                        } else {
                            lines.add(line); // не трогаем промежуточные
                        }
                    }
                }
            }

            if (!updated) {
                if (lines.isEmpty()) {
                    lines.add(TEST_TRADES_CSV_FILE_HEADER);
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
        String minProfit = String.format("%.2f", pairData.getMinProfitRounded());
        String timeToMin = pairData.getTimeInMinutesSinceEntryToMin() + "min";
        String maxProfit = String.format("%.2f", pairData.getMaxProfitRounded());
        String timeToMax = pairData.getTimeInMinutesSinceEntryToMax() + "min";

        String longCh = String.format("%.2f", pairData.getLongChanges());
        String longChMin = String.format("%.2f", pairData.getMinLong());
        String longChMax = String.format("%.2f", pairData.getMaxLong());

        String shortCh = String.format("%.2f", pairData.getShortChanges());
        String shortChMin = String.format("%.2f", pairData.getMinShort());
        String shortChMax = String.format("%.2f", pairData.getMaxShort());

        String z = String.format("%.2f", pairData.getZScoreCurrent());
        String zMin = String.format("%.2f", pairData.getMinZ());
        String zMax = String.format("%.2f", pairData.getMaxZ());

        String corr = String.format("%.2f", pairData.getCorrelationCurrent());
        String corrMin = String.format("%.2f", pairData.getMinCorr());
        String corrMax = String.format("%.2f", pairData.getMaxCorr());

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        /*
        Long Ticker
        Short Ticker

        Profit %
        ProfitMin %,ProfitMin Time min
        ProfitMax %,ProfitMax Time min

        LongCh %
        LongChMin %
        LongChMax %

        ShortCh %
        ShortChMin %
        ShortChMax %

        Z
        ZMin
        ZMax

        Corr
        CorrMin
        CorrMax

        Timestamp";
         */
        return String.join(",",
                longTicker,
                shortTicker,

                profit,
                minProfit, timeToMin,
                maxProfit, timeToMax,

                longCh,
                longChMin,
                longChMax,

                shortCh,
                shortChMin,
                shortChMax,

                z,
                zMin,
                zMax,

                corr,
                corrMin,
                corrMax,

                timestamp);
    }

}
