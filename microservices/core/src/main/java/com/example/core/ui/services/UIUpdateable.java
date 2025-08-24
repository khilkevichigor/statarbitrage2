package com.example.core.ui.services;

/**
 * Интерфейс для View, которые могут обновляться через UIUpdateService
 */
public interface UIUpdateable {
    void handleUiUpdateRequest();
}