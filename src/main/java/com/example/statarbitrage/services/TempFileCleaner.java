package com.example.statarbitrage.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

@Slf4j
@Component
public class TempFileCleaner {

    @PostConstruct
    public void cleanTempJsonFiles() {
        File dir = new File(".");

        File[] files = dir.listFiles((d, name) ->
                name.matches("tmp_input_.*\\.json") || name.matches("tmp_output_.*\\.json")
        );

        if (files != null) {
            for (File file : files) {
                if (file.delete()) {
                    log.info("🧹 Удалён временный файл: {}", file.getName());
                } else {
                    log.warn("⚠️ Не удалось удалить файл: {}", file.getName());
                }
            }
        } else {
            log.info("Нет временных файлов для удаления.");
        }
    }
}
