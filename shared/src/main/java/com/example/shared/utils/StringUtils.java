package com.example.shared.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class StringUtils {
    private StringUtils() {
    }

    public static String getCurrentDateTimeWithZ() {
        return LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }
}
