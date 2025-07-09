package com.example.statarbitrage.ui.interfaces;

/**
 * Интерфейс для View, которые могут обновляться через UIUpdateService
 */
public interface UIUpdateable {
    void handleUiUpdateRequest();
}