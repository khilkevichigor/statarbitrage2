package com.example.statarbitrage.python;

public enum PythonScripts {
    CALC_COINT("calc_coint.py"),
    CALC_ZSCORES("calc_zscores.py");

    PythonScripts(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}