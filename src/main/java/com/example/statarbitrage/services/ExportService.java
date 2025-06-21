package com.example.statarbitrage.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.ResultSetMetaData;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final JdbcTemplate jdbcTemplate;

    public void exportToCsv() {
        jdbcTemplate.query("SELECT * FROM TRADE_LOG", rs -> {
            try (PrintWriter writer = new PrintWriter(new FileWriter("trade_log_export.csv"))) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                // Write header
                for (int i = 1; i <= columnCount; i++) {
                    writer.print(meta.getColumnName(i));
                    if (i < columnCount) writer.print(",");
                }
                writer.println();

                // Write rows
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        writer.print(rs.getString(i));
                        if (i < columnCount) writer.print(",");
                    }
                    writer.println();
                }

                log.info("✅ trade_log_export.csv создан");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void exportToCsvV2() {
        jdbcTemplate.query("SELECT * FROM TRADE_LOG", rs -> {
            try {
                // Создаём папку logs, если её нет
                File logsDir = new File("logs");
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }

                // Путь к файлу
                File file = new File(logsDir, "trade_log_export.csv");

                try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    // Заголовки
                    for (int i = 1; i <= columnCount; i++) {
                        writer.print(meta.getColumnName(i));
                        if (i < columnCount) writer.print(",");
                    }
                    writer.println();

                    // Данные
                    while (rs.next()) {
                        for (int i = 1; i <= columnCount; i++) {
                            writer.print(rs.getString(i));
                            if (i < columnCount) writer.print(",");
                        }
                        writer.println();
                    }

                    log.info("✅ CSV сохранён: " + file.getAbsolutePath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
