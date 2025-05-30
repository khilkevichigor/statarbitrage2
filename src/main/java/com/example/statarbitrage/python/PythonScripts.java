package com.example.statarbitrage.python;

public enum PythonScripts {
    CREATE_Z_SCORE_FILE("create_z_score_file.py"),
    CREATE_CHART("create_chart.py");

    PythonScripts(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}