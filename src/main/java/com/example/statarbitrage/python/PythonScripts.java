package com.example.statarbitrage.python;

public enum PythonScripts {
    Z_SCORE("z_score.py"),
    Z_SCORE_CANDLES("z_score_candles.py"),
    CREATE_CHARTS("create_charts.py"),
    CREATE_CHARTS_CANDLES("create_charts_candles.py");

    PythonScripts(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}