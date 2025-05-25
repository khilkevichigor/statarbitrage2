package com.example.statarbitrage.python;

public enum PythonScripts {
    Z_SCORE_FIND_ALL_AND_SAVE("z_score.py"),
    ROLLING_CORRELATION_FIND_ALL_AND_SAVE("rolling_correlation.py"),
    ADF_FIND_ALL_AND_SAVE("adf.py"),
    CREATE_CHARTS("create_charts.py");

    PythonScripts(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}