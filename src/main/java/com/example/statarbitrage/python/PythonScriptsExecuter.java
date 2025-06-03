package com.example.statarbitrage.python;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

@Slf4j
public final class PythonScriptsExecuter {

    private static final String SCRIPTS_ROOT_PATH = "src/main/java/com/example/statarbitrage/python/";
    private static final ObjectMapper mapper = new ObjectMapper();

    private PythonScriptsExecuter() {
    }

    public static <T> T executeAndReturnObject(String scriptName, Map<String, Object> inputData, TypeReference<T> typeRef) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python3", SCRIPTS_ROOT_PATH + scriptName);
            Process process = processBuilder.start();

            // Отправка данных в stdin Python
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                String jsonInput = mapper.writeValueAsString(inputData);
                writer.write(jsonInput);
                writer.flush();
            }

            // Чтение результата из stdout
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            // Проверка stderr (ошибки)
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errLine;
                    while ((errLine = errorReader.readLine()) != null) {
                        log.error("Python stderr: {}", errLine);
                    }
                }
                throw new RuntimeException("Python script failed with exit code " + exitCode);
            }

            // Десериализация результата
            return mapper.readValue(output.toString(), typeRef);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при исполнении Python-скрипта", e);
        }
    }
}
