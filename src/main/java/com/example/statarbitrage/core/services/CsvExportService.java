package com.example.statarbitrage.core.services;

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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CsvExportService {

    private static final String CSV_FILE_PREFIX = "closed_trades_";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public synchronized void appendPairDataToCsv(PairData pairData) {
        try {
            String schemaSignature = getSchemaSignature(pairData.getClass());
            String csvFileName = CSV_FILE_PREFIX + schemaSignature + ".csv";
            File csvFile = new File(csvFileName);
            boolean isNewFile = !csvFile.exists();

            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, true))) {
                List<Field> exportableFields = getExportableFields(pairData.getClass());

                if (isNewFile) {
                    writer.println(getCsvHeader(exportableFields));
                }

                writer.println(toCsvRow(pairData, exportableFields));
                log.info("✅ Пара {} успешно добавлена в CSV журнал: {}", pairData.getPairName(), csvFileName);

            } catch (IOException | IllegalAccessException e) {
                log.error("❌ Ошибка при записи в CSV файл: {}", e.getMessage(), e);
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("❌ Ошибка при создании сигнатуры схемы для CSV: {}", e.getMessage(), e);
        }
    }

    private String getSchemaSignature(Class<?> clazz) throws NoSuchAlgorithmException {
        List<Field> fields = getExportableFields(clazz);
        String schemaString = fields.stream()
                .map(field -> field.getName() + ":" + field.getType().getName())
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

    private List<Field> getExportableFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (isExportable(field)) {
                fields.add(field);
            }
        }
        return fields;
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

    private boolean isExportable(Field field) {
        return !java.lang.reflect.Modifier.isTransient(field.getModifiers()) &&
                !field.getName().toLowerCase().contains("json") &&
                !field.getName().equalsIgnoreCase("cachedZScoreChart") &&
                !field.getName().equalsIgnoreCase("chartGeneratedAt");
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
