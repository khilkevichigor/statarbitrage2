package com.example.core.common.utils;

public final class ThreadUtil {
    private ThreadUtil() {
    }

    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}