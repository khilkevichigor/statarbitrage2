package com.example.statarbitrage.python;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class PythonExecuteProcessor {

    private static final String SCRIPTS_ROOT_PATH = "src/main/java/com/example/statarbitrage/python/";

    private PythonExecuteProcessor() {
    }

    public static void execute(String scriptName) {
        try {
            // Указываем интерпретатор и путь к скрипту
            ProcessBuilder pb = new ProcessBuilder("python3", SCRIPTS_ROOT_PATH + scriptName);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Читаем вывод скрипта
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Вывод Python: " + line);
            }

            int exitCode = process.waitFor();
            System.out.println("Python завершился с кодом: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
