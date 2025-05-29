package com.example.statarbitrage.python;

public enum PythonScripts {
    Z_SCORE("z_score.py"),
    CREATE_CHARTS("create_charts.py"),
    CREATE_CHARTS_BY_CANDLES("create_charts_by_candles.py");

    PythonScripts(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}