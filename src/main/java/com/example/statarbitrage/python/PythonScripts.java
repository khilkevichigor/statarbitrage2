package com.example.statarbitrage.python;

public enum PythonScripts {
    FIND_ALL_AND_SAVE("/find_all_and_save.py");

    PythonScripts(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}