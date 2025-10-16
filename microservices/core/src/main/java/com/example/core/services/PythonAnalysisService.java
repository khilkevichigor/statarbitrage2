package com.example.core.services;

import com.example.core.client_python.PythonRestClient;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ZScoreData;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с Python API анализа
 * Изолирует вызовы Python API от других сервисов для разрешения циклических зависимостей
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PythonAnalysisService {

    private final PythonRestClient pythonRestClient;

    /**
     * Анализирует пару и возвращает ZScore данные
     */
    public ZScoreData calculateZScoreData(Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🐍 Вызываем Python API для анализа пары. Тикеры: {}", candlesMap.keySet());
        // Получаем результат из Python
        ZScoreData zScoreData = pythonRestClient.analyzePair(candlesMap, settings, true);
        if (zScoreData == null) {
            log.warn("⚠️ Python API вернул null для тикеров: {}", candlesMap.keySet());
            return null; // Возвращаем null вместо исключения для Z-Score расчетов
        }

        log.info("✅ Python API успешно вернул ZScoreData для тикеров: {}", candlesMap.keySet());
        return zScoreData;
    }

    /**
     * Получает ZScore данные для множественных пар
     */
    public List<ZScoreData> fetchZScoreData(Settings settings, Map<String, List<Candle>> candlesMap) {
        return pythonRestClient.fetchZScoreData(settings, candlesMap);
    }
}