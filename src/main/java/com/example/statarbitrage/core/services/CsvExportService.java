package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.annotation.CsvExportable;
import com.example.statarbitrage.common.model.PairData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CsvExportService {

    private static final String CSV_FILE_PREFIX = "closed_trades_";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public synchronized void appendPairDataToCsv(PairData pairData) {
        try {
            pairData.updateFormattedFieldsBeforeExportToCsv();

            List<Field> exportableFields = getExportableFields(PairData.class);
            String schemaSignature = getSchemaSignature(exportableFields);

            File existingFile = findExistingFile(schemaSignature);
            String csvFileName;
            boolean isNewFile;

            if (existingFile != null) {
                csvFileName = existingFile.getName();
                isNewFile = false;
            } else {
                String timestamp = DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm").format(LocalDateTime.now());
                csvFileName = CSV_FILE_PREFIX + timestamp + "_" + schemaSignature + ".csv";
                isNewFile = true;
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFileName, true))) {
                if (isNewFile) {
                    writer.println(getCsvHeader(exportableFields));
                }
                writer.println(toCsvRow(pairData, exportableFields));
                log.info("✅ Пара {} успешно добавлена в CSV журнал: {}\n", pairData.getPairName(), csvFileName);

            } catch (IOException | IllegalAccessException e) {
                log.error("❌ Ошибка при записи в CSV файл: {}\n", e.getMessage(), e);
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("❌ Ошибка при создании сигнатуры схемы для CSV: {}\n", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Непредвиденная ошибка при экспорте в CSV: {}\n", e.getMessage(), e);
        }
    }

    private File findExistingFile(String schemaSignature) {
        File dir = new File(".");
        File[] matchingFiles = dir.listFiles((d, name) -> name.startsWith(CSV_FILE_PREFIX) && name.endsWith("_" + schemaSignature + ".csv"));
        if (matchingFiles != null && matchingFiles.length > 0) {
            return matchingFiles[0];
        }
        return null;
    }

    private List<Field> getExportableFields(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(CsvExportable.class))
                .sorted(Comparator.comparingInt(field -> field.getAnnotation(CsvExportable.class).order()))
                .collect(Collectors.toList());
    }

    private String getSchemaSignature(List<Field> fields) throws NoSuchAlgorithmException {
        String schemaString = fields.stream()
                .map(Field::getName)
                .collect(Collectors.joining(";"));
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

    private String getCsvHeader(List<Field> fields) {
        return fields.stream()
                .map(Field::getName)
                .collect(Collectors.joining(","));
    }

    private String toCsvRow(PairData pairData, List<Field> fields) throws IllegalAccessException {
        List<String> values = new ArrayList<>();
        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(pairData);
            if (value instanceof Long && (field.getName().toLowerCase().contains("time"))) {
                values.add(escapeCsv(formatTimestamp((Long) value)));
            } else {
                values.add(escapeCsv(value != null ? value.toString() : ""));
            }
        }
        return String.join(",", values);
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