package com.example.statarbitrage.python;

import com.example.statarbitrage.utils.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
public final class PythonScriptsExecuter {

    private static final String SCRIPTS_ROOT_PATH = "src/main/java/com/example/statarbitrage/python/";

    private PythonScriptsExecuter() {
    }

    public static void execute(String scriptName, boolean withLogging) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python3", SCRIPTS_ROOT_PATH + scriptName);
            Process process = processBuilder.start();

            // Читаем stdout (обычный вывод)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (withLogging) {
                            log.info("Python вывод: " + line);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Читаем stderr (ошибки)
            new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        if (withLogging) {
                            log.error("Python ошибка: " + line);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            int exitCode = process.waitFor();
            if (withLogging) {
                log.info("Python скрипт завершен с кодом: " + exitCode);
            }
            ThreadUtil.sleep(1000 * 2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
