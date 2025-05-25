package com.example.statarbitrage.experemental;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) {
        try {
            // Указываем интерпретатор и путь к скрипту
            ProcessBuilder pb = new ProcessBuilder("python3", "src/main/java/com/example/okxscreener/experemental/chatgpt/script.py");
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
