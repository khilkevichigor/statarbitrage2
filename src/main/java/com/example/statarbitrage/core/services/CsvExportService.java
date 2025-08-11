package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CsvExportService {

    private static final String CSV_FILE_PREFIX = "closed_trades_";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> CSV_HEADERS = Arrays.asList(
            "id", "uuid", "version", "status", "errorDescription", "longTicker", "shortTicker", "pairName",
            "longTickerEntryPrice", "longTickerCurrentPrice", "shortTickerEntryPrice", "shortTickerCurrentPrice",
            "meanEntry", "meanCurrent", "spreadEntry", "spreadCurrent", "zScoreEntry", "zScoreCurrent",
            "pValueEntry", "pValueCurrent", "adfPvalueEntry", "adfPvalueCurrent", "correlationEntry", "correlationCurrent",
            "alphaEntry", "alphaCurrent", "betaEntry", "betaCurrent", "stdEntry", "stdCurrent", "zScoreChanges",
            "longUSDTChanges", "longPercentChanges", "shortUSDTChanges", "shortPercentChanges",
            "portfolioBeforeTradeUSDT", "profitUSDTChanges", "portfolioAfterTradeUSDT", "profitPercentChanges",
            "minutesToMinProfitPercent", "minutesToMaxProfitPercent", "minProfitPercentChanges", "maxProfitPercentChanges",
            "formattedTimeToMinProfit", "formattedTimeToMaxProfit", "formattedProfitLong", "formattedProfitShort", "formattedProfitCommon",
            "timestamp", "entryTime", "updatedTime", "maxZ", "minZ", "maxLong", "minLong", "maxShort", "minShort", "maxCorr", "minCorr",
            "exitReason", "closeAtBreakeven", "settingsTimeframe", "settingsCandleLimit", "settingsMinZ",
            "settingsMinWindowSize", "settingsMinPValue", "settingsMaxAdfValue", "settingsMinRSquared", "settingsMinCorrelation",
            "settingsMinVolume", "settingsCheckInterval", "settingsMaxLongMarginSize", "settingsMaxShortMarginSize",
            "settingsLeverage", "settingsExitTake", "settingsExitStop", "settingsExitZMin", "settingsExitZMax",
            "settingsExitZMaxPercent", "settingsExitTimeHours", "settingsExitBreakEvenPercent",
            "settingsAutoTradingEnabled", "settingsUseMinZFilter", "settingsUseMinRSquaredFilter", "settingsUseMinPValueFilter",
            "settingsUseMaxAdfValueFilter", "settingsUseMinCorrelationFilter", "settingsUseMinVolumeFilter",
            "settingsUseExitTake", "settingsUseExitStop", "settingsUseExitZMin", "settingsUseExitZMax",
            "settingsUseExitZMaxPercent", "settingsUseExitTimeHours", "settingsUseExitBreakEvenPercent", "settingsMinimumLotBlacklist"
    );

    public synchronized void appendPairDataToCsv(PairData pairData) {
        try {
            pairData.updateFormattedFieldsBeforeExportToCsv();

            String schemaSignature = getSchemaSignature();
            String csvFileName = CSV_FILE_PREFIX + schemaSignature + ".csv";
            File csvFile = new File(csvFileName);
            boolean isNewFile = !csvFile.exists();

            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, true))) {
                if (isNewFile) {
                    writer.println(getCsvHeader());
                }
                writer.println(toCsvRow(pairData));
                log.info("✅ Пара {} успешно добавлена в CSV журнал: {}\n", pairData.getPairName(), csvFileName);

            } catch (IOException e) {
                log.error("❌ Ошибка при записи в CSV файл: {}\n", e.getMessage(), e);
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("❌ Ошибка при создании сигнатуры схемы для CSV: {}\n", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Непредвиденная ошибка при экспорте в CSV: {}\n", e.getMessage(), e);
        }
    }

    private String getSchemaSignature() throws NoSuchAlgorithmException {
        String schemaString = String.join(";", CSV_HEADERS);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(schemaString.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString().substring(0, 8);
    }

    private String getCsvHeader() {
        return String.join(",", CSV_HEADERS);
    }

    private String toCsvRow(PairData p) {
        List<String> values = Arrays.asList(
                String.valueOf(p.getId()),
                p.getUuid(),
                String.valueOf(p.getVersion()),
                p.getStatus() != null ? p.getStatus().toString() : "",
                p.getErrorDescription(),
                p.getLongTicker(),
                p.getShortTicker(),
                p.getPairName(),
                String.valueOf(p.getLongTickerEntryPrice()),
                String.valueOf(p.getLongTickerCurrentPrice()),
                String.valueOf(p.getShortTickerEntryPrice()),
                String.valueOf(p.getShortTickerCurrentPrice()),
                String.valueOf(p.getMeanEntry()),
                String.valueOf(p.getMeanCurrent()),
                String.valueOf(p.getSpreadEntry()),
                String.valueOf(p.getSpreadCurrent()),
                String.valueOf(p.getZScoreEntry()),
                String.valueOf(p.getZScoreCurrent()),
                String.valueOf(p.getPValueEntry()),
                String.valueOf(p.getPValueCurrent()),
                String.valueOf(p.getAdfPvalueEntry()),
                String.valueOf(p.getAdfPvalueCurrent()),
                String.valueOf(p.getCorrelationEntry()),
                String.valueOf(p.getCorrelationCurrent()),
                String.valueOf(p.getAlphaEntry()),
                String.valueOf(p.getAlphaCurrent()),
                String.valueOf(p.getBetaEntry()),
                String.valueOf(p.getBetaCurrent()),
                String.valueOf(p.getStdEntry()),
                String.valueOf(p.getStdCurrent()),
                p.getZScoreChanges() != null ? p.getZScoreChanges().toPlainString() : "",
                p.getLongUSDTChanges() != null ? p.getLongUSDTChanges().toPlainString() : "",
                p.getLongPercentChanges() != null ? p.getLongPercentChanges().toPlainString() : "",
                p.getShortUSDTChanges() != null ? p.getShortUSDTChanges().toPlainString() : "",
                p.getShortPercentChanges() != null ? p.getShortPercentChanges().toPlainString() : "",
                p.getPortfolioBeforeTradeUSDT() != null ? p.getPortfolioBeforeTradeUSDT().toPlainString() : "",
                p.getProfitUSDTChanges() != null ? p.getProfitUSDTChanges().toPlainString() : "",
                p.getPortfolioAfterTradeUSDT() != null ? p.getPortfolioAfterTradeUSDT().toPlainString() : "",
                p.getProfitPercentChanges() != null ? p.getProfitPercentChanges().toPlainString() : "",
                String.valueOf(p.getMinutesToMinProfitPercent()),
                String.valueOf(p.getMinutesToMaxProfitPercent()),
                p.getMinProfitPercentChanges() != null ? p.getMinProfitPercentChanges().toPlainString() : "",
                p.getMaxProfitPercentChanges() != null ? p.getMaxProfitPercentChanges().toPlainString() : "",
                p.getFormattedTimeToMinProfit(),
                p.getFormattedTimeToMaxProfit(),
                p.getFormattedProfitLong(),
                p.getFormattedProfitShort(),
                p.getFormattedProfitCommon(),
                formatTimestamp(p.getTimestamp()),
                formatTimestamp(p.getEntryTime()),
                formatTimestamp(p.getUpdatedTime()),
                p.getMaxZ() != null ? p.getMaxZ().toPlainString() : "",
                p.getMinZ() != null ? p.getMinZ().toPlainString() : "",
                p.getMaxLong() != null ? p.getMaxLong().toPlainString() : "",
                p.getMinLong() != null ? p.getMinLong().toPlainString() : "",
                p.getMaxShort() != null ? p.getMaxShort().toPlainString() : "",
                p.getMinShort() != null ? p.getMinShort().toPlainString() : "",
                p.getMaxCorr() != null ? p.getMaxCorr().toPlainString() : "",
                p.getMinCorr() != null ? p.getMinCorr().toPlainString() : "",
                p.getExitReason(),
                String.valueOf(p.isCloseAtBreakeven()),
                p.getSettingsTimeframe(),
                String.valueOf(p.getSettingsCandleLimit()),
                String.valueOf(p.getSettingsMinZ()),
                String.valueOf(p.getSettingsMinWindowSize()),
                String.valueOf(p.getSettingsMinPValue()),
                String.valueOf(p.getSettingsMaxAdfValue()),
                String.valueOf(p.getSettingsMinRSquared()),
                String.valueOf(p.getSettingsMinCorrelation()),
                String.valueOf(p.getSettingsMinVolume()),
                String.valueOf(p.getSettingsCheckInterval()),
                String.valueOf(p.getSettingsMaxLongMarginSize()),
                String.valueOf(p.getSettingsMaxShortMarginSize()),
                String.valueOf(p.getSettingsLeverage()),
                String.valueOf(p.getSettingsExitTake()),
                String.valueOf(p.getSettingsExitStop()),
                String.valueOf(p.getSettingsExitZMin()),
                String.valueOf(p.getSettingsExitZMax()),
                String.valueOf(p.getSettingsExitZMaxPercent()),
                String.valueOf(p.getSettingsExitTimeHours()),
                String.valueOf(p.getSettingsExitBreakEvenPercent()),
                String.valueOf(p.isSettingsAutoTradingEnabled()),
                String.valueOf(p.isSettingsUseMinZFilter()),
                String.valueOf(p.isSettingsUseMinRSquaredFilter()),
                String.valueOf(p.isSettingsUseMinPValueFilter()),
                String.valueOf(p.isSettingsUseMaxAdfValueFilter()),
                String.valueOf(p.isSettingsUseMinCorrelationFilter()),
                String.valueOf(p.isSettingsUseMinVolumeFilter()),
                String.valueOf(p.isSettingsUseExitTake()),
                String.valueOf(p.isSettingsUseExitStop()),
                String.valueOf(p.isSettingsUseExitZMin()),
                String.valueOf(p.isSettingsUseExitZMax()),
                String.valueOf(p.isSettingsUseExitZMaxPercent()),
                String.valueOf(p.isSettingsUseExitTimeHours()),
                String.valueOf(p.isSettingsUseExitBreakEvenPercent()),
                p.getSettingsMinimumLotBlacklist()
        );

        return values.stream()
                .map(this::escapeCsv)
                .collect(Collectors.joining(","));
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}