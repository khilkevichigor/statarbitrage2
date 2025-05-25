package com.example.statarbitrage.python;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class PythonScriptsExecuter {

    private static final String SCRIPTS_ROOT_PATH = "src/main/java/com/example/statarbitrage/python/";

    private PythonScriptsExecuter() {
    }

    public static void execute(String scriptName) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python3", SCRIPTS_ROOT_PATH + scriptName);
            Process process = processBuilder.start();

            // Отдельно читаем stderr (ошибки)
            new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        System.err.println("Python error: " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            int exitCode = process.waitFor();
            System.out.println("Python script exited with code: " + exitCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
