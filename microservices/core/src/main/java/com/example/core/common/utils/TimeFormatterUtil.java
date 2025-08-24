package com.example.core.common.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeFormatterUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE_ID = ZoneId.systemDefault(); // Можно поменять на конкретную зону, если нужно

    public static String formatFromMillis(long millis) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE_ID);
        return FORMATTER.format(dateTime);
    }

    public static String formatFromSeconds(long seconds) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZONE_ID);
        return FORMATTER.format(dateTime);
    }

    public static String formatDurationFromMillis(long durationMillis) {
        long totalSeconds = durationMillis / 1000;
        long days = totalSeconds / (24 * 3600);
        long hours = (totalSeconds % (24 * 3600)) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("д ");
        if (hours > 0) sb.append(hours).append("ч ");
        if (minutes > 0 || (days == 0 && hours == 0)) sb.append(minutes).append("м");

        return sb.toString().trim();
    }
}
