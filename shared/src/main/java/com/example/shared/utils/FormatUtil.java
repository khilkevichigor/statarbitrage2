package com.example.shared.utils;

public final class FormatUtil {

    private FormatUtil() {
    }

    public static String color(Double value, double threshold) {
        if (value == null) return "N/A";
        return value <= threshold ? String.format("%.5f ✅", value) : String.format("%.5f ⚠️", value);
    }

    public static String color(Double value, double goodMin, boolean greaterIsBetter) {
        if (value == null) return "N/A";

        boolean isGood = greaterIsBetter ? value >= goodMin : value <= goodMin;
        return isGood ? String.format("%.5f ✅", value) : String.format("%.5f ⚠️", value);
    }

    public static String colorInt(Integer value, int threshold) {
        if (value == null) return "N/A";
        return value >= threshold ? value + " ✅" : value + " ⚠️";
    }
}
