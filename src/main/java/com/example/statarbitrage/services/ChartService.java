package com.example.statarbitrage.services;

import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.model.ZScorePoint;
import com.example.statarbitrage.utils.ComboProfitAndZChart;
import com.example.statarbitrage.utils.ComboTwoTrendsAndSpreadChart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {
    private final EventSendService eventSendService;

    private static final String CHARTS_DIR = "charts";

    public void clearChartDir() {
        File dir = new File(CHARTS_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        clearChartDir();
                    }
                    if (!file.delete()) {
                        log.warn("Не удалось удалить файл: {}", file.getAbsolutePath());
                    }
                    log.info("Очистили папку с чартами");
                }
            }
        }
    }

    public File getChart() {
        File chartDir = new File(CHARTS_DIR);
        File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

        if (chartFiles != null && chartFiles.length > 0) {
            // Сортировка по времени последнего изменения (от новых к старым)
            Arrays.sort(chartFiles, Comparator.comparingLong(File::lastModified).reversed());
            return chartFiles[0]; // Самый свежий чарт
        }

        return null; // Если файлов нет
    }

    public void sendChart(String chatId, File chartFile, String caption, boolean withLogging) {
        try {
            eventSendService.sendAsPhoto(SendAsPhotoEvent.builder()
                    .chatId(chatId)
                    .photo(chartFile)
                    .caption(caption)
                    .build());
            if (withLogging) {
                log.info("📤 Чарт отправлен в Telegram: {}", chartFile.getName());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
        }
    }

    public void generateCombinedChartOls(String chatId, ConcurrentHashMap<String, List<Candle>> candlesMap, ZScoreEntry bestPair, EntryData entryData) {
        ComboTwoTrendsAndSpreadChart.generateCombinedChartOls(candlesMap, bestPair, entryData);
        sendChart(chatId, getChart(), "Stat Arbitrage Combined Chart", true);
    }

    public void sendCombinedChartProfitVsZ(String chatId, List<ZScorePoint> history) {

        ComboProfitAndZChart.sendCombinedChartProfitVsZ(history);

        BigDecimal profit = history.stream()
                .map(ZScorePoint::profit)
                .reduce((first, second) -> second)
                .orElseThrow(RuntimeException::new);

        BigDecimal zChanges = history.stream()
                .map(ZScorePoint::zScoreChanges)
                .reduce((first, second) -> second)
                .orElseThrow(RuntimeException::new);

        sendChart(chatId, getChart(), "Profit:" + profit + "%, z:" + zChanges + "%", true);
    }
}
