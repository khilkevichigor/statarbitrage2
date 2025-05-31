package com.example.statarbitrage.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    public void deleteSpecificFilesInProjectRoot(List<String> fileNames) {
        File projectRoot = new File(".");
        for (String fileName : fileNames) {
            File file = new File(projectRoot, fileName);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.info("✅ Удалён файл: {}", file.getAbsolutePath());
                } else {
                    log.warn("⚠️ Не удалось удалить файл: {}", file.getAbsolutePath());
                }
            } else {
                log.info("ℹ️ Файл не найден: {}", file.getAbsolutePath());
            }
        }
    }
}