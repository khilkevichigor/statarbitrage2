package com.example.statarbitrage.calculators;

public final class CandlesCalculator {
    private CandlesCalculator() {
    }

    public static int getCandlesPer24h(String barSize) {
        return switch (barSize) {
            case "1m" -> 24 * 60;
            case "3m" -> 24 * 60 / 3;
            case "5m" -> 24 * 60 / 5;
            case "15m" -> 24 * 60 / 15;
            case "30m" -> 24 * 60 / 30;
            case "1h", "1H" -> 24;
            case "2h", "2H" -> 12;
            case "4h", "4H" -> 6;
            case "6h", "6H" -> 4;
            case "12h", "12H" -> 2;
            case "1d", "1D" -> 1;
            default -> throw new IllegalArgumentException("Unsupported barSize format: " + barSize);
        };
    }

    public static int getMinutesPerBar(String barSize) {
        if (barSize.endsWith("m")) {
            return Integer.parseInt(barSize.replace("m", ""));
        } else if (barSize.endsWith("h")) {
            return Integer.parseInt(barSize.replace("h", "")) * 60;
        } else if (barSize.endsWith("d")) {
            return Integer.parseInt(barSize.replace("d", "")) * 1440;
        } else {
            throw new IllegalArgumentException("Unsupported barSize format: " + barSize);
        }
    }
}