package com.example.statarbitrage.services;

import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.utils.ZScoreChart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

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
            eventSendService.sendTelegramMessageAsPhotoEvent(SendAsPhotoEvent.builder()
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

    public void createAndSend(String chatId, PairData pairData) {
        clearChartDir();
        ZScoreChart.create(pairData);
        sendChart(chatId, getChart(), getCaption(pairData), true);
    }

    @NotNull
    private static String getCaption(PairData pairData) {
        StringBuilder sb = new StringBuilder();
        sb.append(pairData.getA()).append(" / ").append(pairData.getB()).append("\n");
        if (pairData.getProfitChanges() != null) {
            sb.append("profit=").append(pairData.getProfitChanges()).append("%").append("\n");
        }
        if (pairData.getLongChanges() != null && pairData.getShortChanges() != null) {
            sb.append("longCh=").append(pairData.getLongChanges()).append("%").append(" | ").append("shortCh=").append(pairData.getShortChanges()).append("%").append("\n");
        }
        sb.append("z=").append(String.format("%.2f", pairData.getZScoreCurrent())).append(" | ").append("corr=").append(String.format("%.2f", pairData.getCorrelationCurrent()));
        return sb.toString();
    }
}
