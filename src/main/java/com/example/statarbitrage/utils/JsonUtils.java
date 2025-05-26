package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.ZScoreEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;

public class JsonUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<ZScoreEntry> readZScoreJson(String filePath) {
        try {
            return mapper.readValue(new File(filePath), new TypeReference<List<ZScoreEntry>>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void writeZScoreJson(String filePath, List<ZScoreEntry> entries) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), entries);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
