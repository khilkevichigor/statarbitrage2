package com.example.statarbitrage.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSetMetaData;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final JdbcTemplate jdbcTemplate;

    //todo берет не все строки
//    @Scheduled(fixedDelay = 30_000) // каждые 10 секунд
//    public void scheduledExportToCsv() {
//        try {
//            exportToCsv();
//        } catch (Exception e) {
//            log.error("Ошибка при плановом экспорте CSV", e);
//        }
//    }

    public void exportToCsv() {
        String query = "SELECT * FROM TRADE_LOG ORDER BY id";

        File logsDir = new File("logs");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            log.error("Не удалось создать папку logs");
            return;
        }

        File file = new File(logsDir, "trade_log_export.csv");

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            jdbcTemplate.query(query, rs -> {
                try {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    // Пишем заголовки
                    for (int i = 1; i <= columnCount; i++) {
                        writer.print(meta.getColumnName(i));
                        if (i < columnCount) writer.print(",");
                    }
                    writer.println();

                    boolean hasRows = false;

                    // Пишем строки
                    while (rs.next()) {
                        hasRows = true;
                        for (int i = 1; i <= columnCount; i++) {
                            String value = rs.getString(i);
                            // Экранируем кавычки и запятые
                            if (value != null && (value.contains(",") || value.contains("\""))) {
                                value = "\"" + value.replace("\"", "\"\"") + "\"";
                            }
                            writer.print(value);
                            if (i < columnCount) writer.print(",");
                        }
                        writer.println();
                    }

                    if (hasRows) {
                        log.info("✅ CSV экспорт завершён: {}", file.getAbsolutePath());
                    } else {
                        log.warn("⚠️ Нет записей для экспорта. CSV создан только с заголовком: {}", file.getAbsolutePath());
                    }

                } catch (Exception e) {
                    log.error("Ошибка при обработке результата запроса", e);
                }
            });
        } catch (Exception e) {
            log.error("Ошибка при записи CSV файла", e);
        }
    }


}
