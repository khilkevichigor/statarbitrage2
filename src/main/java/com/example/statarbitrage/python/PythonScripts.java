package com.example.statarbitrage.python;

public enum PythonScripts {
    Z_SCORE_FIND_ALL_AND_SAVE("/z_score_find_all_and_save.py"),
    ROLLING_CORRELATION_FIND_ALL_AND_SAVE("/rolling_correlation_find_all_and_save.py"),
    ADF_FIND_ALL_AND_SAVE("/adf_find_all_and_save.py");

    PythonScripts(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}