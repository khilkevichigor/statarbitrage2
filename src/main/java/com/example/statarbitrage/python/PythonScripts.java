package com.example.statarbitrage.python;

public enum PythonScripts {
    FIND_ALL("/find_all.py");

    PythonScripts(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}