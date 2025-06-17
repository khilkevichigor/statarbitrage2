package com.example.statarbitrage.services;

import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.utils.ZScoreChart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;

import static com.example.statarbitrage.constant.Constants.CHARTS_DIR;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {
    private final EventSendService eventSendService;


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
        sendChart(chatId, getChart(), getCaptionV2(pairData), true);
    }

    @NotNull
    private static String getCaption(PairData pairData) {
        StringBuilder sb = new StringBuilder();
        sb.append(pairData.getLongTicker()).append(" / ").append(pairData.getShortTicker()).append("\n");
        if (pairData.getProfitChanges() != null) {
            sb.append("profit=").append(pairData.getProfitChanges()).append("%").append("\n");
        }
        if (pairData.getLongChanges() != null && pairData.getShortChanges() != null) {
            sb.append("longCh=").append(pairData.getLongChanges()).append("%").append(" | ").append("shortCh=").append(pairData.getShortChanges()).append("%").append("\n");
        }
        sb.append("z=").append(String.format("%.2f", pairData.getZScoreCurrent())).append(" | ").append("corr=").append(String.format("%.2f", pairData.getCorrelationCurrent())).append("\n");
        if (pairData.getMaxProfitRounded() != null && pairData.getMinProfitRounded() != null) {
            sb.append("maxProfit=").append(pairData.getMaxProfitRounded()).append("%(").append(pairData.getTimeInMinutesSinceEntryToMax()).append("min)").append(" | ").append("minProfit=").append(pairData.getMinProfitRounded()).append("%(").append(pairData.getTimeInMinutesSinceEntryToMin()).append("min)").append("\n");
        }
        return sb.toString();
    }

    private static String getCaptionV2(PairData pairData) {
        StringBuilder sb = new StringBuilder();
        sb.append(pairData.getLongTicker()).append(" / ").append(pairData.getShortTicker()).append("\n");

        if (pairData.getProfitChanges() != null) {
            sb.append("profit=").append(pairData.getProfitChanges()).append("%").append("\n");
        }

        if (pairData.getLongChanges() != null && pairData.getShortChanges() != null) {
            sb.append("longCh=").append(pairData.getLongChanges()).append("%")
                    .append(" | shortCh=").append(pairData.getShortChanges()).append("%").append("\n");
        }

        sb.append("z=").append(String.format("%.2f", pairData.getZScoreCurrent()))
                .append(" | corr=").append(String.format("%.2f", pairData.getCorrelationCurrent())).append("\n");

        if (pairData.getMaxProfitRounded() != null && pairData.getMinProfitRounded() != null) {
            sb.append("maxProfit=").append(pairData.getMaxProfitRounded()).append("%(")
                    .append(pairData.getTimeInMinutesSinceEntryToMax()).append("min)")
                    .append(" | minProfit=").append(pairData.getMinProfitRounded()).append("%(")
                    .append(pairData.getTimeInMinutesSinceEntryToMin()).append("min)").append("\n");
        }

        if (pairData.getMaxZ() != null && pairData.getMinZ() != null) {
            sb.append("Z-score: max=").append(pairData.getMaxZ().setScale(2, RoundingMode.HALF_UP))
                    .append(" | min=").append(pairData.getMinZ().setScale(2, RoundingMode.HALF_UP)).append("\n");
        }

        if (pairData.getMaxLong() != null && pairData.getMinLong() != null) {
            sb.append("LongCh: max=").append(pairData.getMaxLong().setScale(2, RoundingMode.HALF_UP)).append("%")
                    .append(" | min=").append(pairData.getMinLong().setScale(2, RoundingMode.HALF_UP)).append("%").append("\n");
        }

        if (pairData.getMaxShort() != null && pairData.getMinShort() != null) {
            sb.append("ShortCh: max=").append(pairData.getMaxShort().setScale(2, RoundingMode.HALF_UP)).append("%")
                    .append(" | min=").append(pairData.getMinShort().setScale(2, RoundingMode.HALF_UP)).append("%").append("\n");
        }

        return sb.toString();
    }

}
