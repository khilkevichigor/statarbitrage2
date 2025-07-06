//package com.example.statarbitrage.core.services;
//
//import com.example.statarbitrage.common.events.SendAsPhotoEvent;
//import com.example.statarbitrage.common.model.PairData;
//import com.example.statarbitrage.common.utils.ZScoreChart;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.jetbrains.annotations.NotNull;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.math.RoundingMode;
//import java.util.Arrays;
//import java.util.Comparator;
//
//import static com.example.statarbitrage.common.constant.Constants.CHARTS_DIR;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class ChartService {
//    private final EventSendService eventSendService;
//
//    public void clearChartDir() {
//        File dir = new File(CHARTS_DIR);
//        if (dir.exists() && dir.isDirectory()) {
//            File[] files = dir.listFiles();
//            if (files != null) {
//                for (File file : files) {
//                    if (file.isDirectory()) {
//                        clearChartDir();
//                    }
//                    if (!file.delete()) {
//                        log.warn("Не удалось удалить файл: {}", file.getAbsolutePath());
//                    }
//                    log.info("Очистили папку с чартами");
//                }
//            }
//        }
//    }
//
//    public File getChart() {
//        File chartDir = new File(CHARTS_DIR);
//        File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
//
//        if (chartFiles != null && chartFiles.length > 0) {
//            // Сортировка по времени последнего изменения (от новых к старым)
//            Arrays.sort(chartFiles, Comparator.comparingLong(File::lastModified).reversed());
//            return chartFiles[0]; // Самый свежий чарт
//        }
//
//        return null; // Если файлов нет
//    }
//
//    public void sendChart(String chatId, File chartFile, String caption, boolean withLogging) {
//        try {
//            eventSendService.sendTelegramMessageAsPhotoEvent(SendAsPhotoEvent.builder()
//                    .chatId(chatId)
//                    .photo(chartFile)
//                    .caption(caption)
//                    .build());
//            if (withLogging) {
//                log.info("📤 Чарт отправлен в Telegram: {}", chartFile.getName());
//            }
//        } catch (Exception e) {
//            log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
//        }
//    }
//
//    public void createAndSend(String chatId, PairData pairData) {
//        clearChartDir();
//        ZScoreChart.create(pairData);
//        sendChart(chatId, getChart(), getCaptionV2(pairData), true);
//    }
//
//    @NotNull
//    private static String getCaption(PairData pairData) {
//        StringBuilder sb = new StringBuilder();
//        sb.append(pairData.getLongTicker()).append(" / ").append(pairData.getShortTicker()).append("\n");
//        if (pairData.getProfitChanges() != null) {
//            sb.append("profit=").append(pairData.getProfitChanges()).append("%").append("\n");
//        }
//        if (pairData.getLongChanges() != null && pairData.getShortChanges() != null) {
//            sb.append("longCh=").append(pairData.getLongChanges()).append("%").append(" | ").append("shortCh=").append(pairData.getShortChanges()).append("%").append("\n");
//        }
//        sb.append("z=").append(String.format("%.2f", pairData.getZScoreCurrent())).append(" | ").append("corr=").append(String.format("%.2f", pairData.getCorrelationCurrent())).append("\n");
//        if (pairData.getMaxProfitRounded() != null && pairData.getMinProfitRounded() != null) {
//            sb.append("maxProfit=").append(pairData.getMaxProfitRounded()).append("%(").append(pairData.getTimeInMinutesSinceEntryToMax()).append("min)").append(" | ").append("minProfit=").append(pairData.getMinProfitRounded()).append("%(").append(pairData.getTimeInMinutesSinceEntryToMin()).append("min)").append("\n");
//        }
//        return sb.toString();
//    }
//
//    private static String getCaptionV2(PairData pairData) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("longTicker=").append(pairData.getLongTicker()).append("\n");
//        sb.append("shortTicker=").append(pairData.getShortTicker()).append("\n\n");
//
//        if (pairData.getProfitChanges() != null) {
//            sb.append("profit=").append(pairData.getProfitChanges()).append("%").append("\n");
//        }
//        if (pairData.getMaxProfitRounded() != null && pairData.getMinProfitRounded() != null) {
//            sb.append("min=").append(pairData.getMinProfitRounded()).append("%(")
//                    .append(pairData.getTimeInMinutesSinceEntryToMin()).append("min)").append("\n");
//            sb.append("max=").append(pairData.getMaxProfitRounded()).append("%(")
//                    .append(pairData.getTimeInMinutesSinceEntryToMax()).append("min)").append("\n\n");
//        }
//
//        sb.append("z=").append(String.format("%.2f", pairData.getZScoreCurrent()));
//        if (pairData.getMaxZ() != null && pairData.getMinZ() != null) {
//            sb.append(" min=").append(pairData.getMinZ().setScale(2, RoundingMode.HALF_UP));
//            sb.append(" max=").append(pairData.getMaxZ().setScale(2, RoundingMode.HALF_UP)).append("\n");
//        } else {
//            sb.append(" "); //для sendBestChart
//        }
//
//        sb.append("corr=").append(String.format("%.2f", pairData.getCorrelationCurrent()));
//        if (pairData.getMaxCorr() != null && pairData.getMinCorr() != null) {
//            sb.append(" min=").append(pairData.getMinCorr().setScale(2, RoundingMode.HALF_UP));
//            sb.append(" max=").append(pairData.getMaxCorr().setScale(2, RoundingMode.HALF_UP)).append("\n");
//        }
//
//        if (pairData.getLongChanges() != null) {
//            sb.append("long=").append(pairData.getLongChanges()).append("%");
//        }
//        if (pairData.getMaxLong() != null && pairData.getMinLong() != null) {
//            sb.append(" min=").append(pairData.getMinLong().setScale(2, RoundingMode.HALF_UP)).append("%");
//            sb.append(" max=").append(pairData.getMaxLong().setScale(2, RoundingMode.HALF_UP)).append("%").append("\n");
//        }
//
//        if (pairData.getShortChanges() != null) {
//            sb.append("short=").append(pairData.getShortChanges()).append("%");
//        }
//        if (pairData.getMaxShort() != null && pairData.getMinShort() != null) {
//            sb.append(" min=").append(pairData.getMinShort().setScale(2, RoundingMode.HALF_UP)).append("%");
//            sb.append(" max=").append(pairData.getMaxShort().setScale(2, RoundingMode.HALF_UP)).append("%").append("\n");
//        }
//        return sb.toString();
//    }
//}
