package com.example.core.ui.interfaces;

/**
 * Интерфейс для View, которые могут обновляться через UIUpdateService
 */
public interface UIUpdateable {
    void handleUiUpdateRequest();
}