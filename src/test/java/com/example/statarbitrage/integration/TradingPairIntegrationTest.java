package com.example.statarbitrage.integration;

import com.example.statarbitrage.common.dto.TradingPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тест интеграции с Python API
 */
@SpringBootTest
class TradingPairIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testTradingPairSerialization() throws Exception {
        // Создаем тестовый объект как ответ от Python API
        String pythonResponse = """
        {
            "undervaluedTicker": "ETH-USDT-SWAP",
            "overvaluedTicker": "BTC-USDT-SWAP",
            "latest_zscore": 2.65,
            "correlation": 0.78,
            "cointegration_pvalue": 0.01,
            "total_observations": 100,
            "avg_r_squared": 0.85
        }
        """;

        // Десериализуем в TradingPair
        TradingPair pair = objectMapper.readValue(pythonResponse, TradingPair.class);

        // Проверяем корректность маппинга
        assertEquals("ETH-USDT-SWAP", pair.getBuyTicker());
        assertEquals("BTC-USDT-SWAP", pair.getSellTicker());
        assertEquals(2.65, pair.getZscore(), 0.01);
        assertEquals(0.78, pair.getCorrelation(), 0.01);
        assertEquals(0.01, pair.getPValue(), 0.001);
        assertEquals(100, pair.getObservations());
        assertEquals(0.85, pair.getRSquared(), 0.01);

        // Проверяем обратную совместимость
        assertEquals("ETH-USDT-SWAP", pair.getLongTicker());
        assertEquals("BTC-USDT-SWAP", pair.getShortTicker());

        // Проверяем бизнес-логику
        assertTrue(pair.isValidForTrading(0.7, 0.05, 2.0));
        assertEquals(2.65, pair.getSignalStrength(), 0.01);
        assertEquals("MEAN_REVERSION", pair.getTradeDirection());
    }

    @Test
    void testTradingPairBusinessLogic() {
        TradingPair pair = TradingPair.builder()
                .buyTicker("ETH-USDT-SWAP")
                .sellTicker("BTC-USDT-SWAP")
                .zscore(2.5)
                .correlation(0.8)
                .pValue(0.02)
                .build();

        // Тест валидации
        assertTrue(pair.isValidForTrading(0.7, 0.05, 2.0));
        assertFalse(pair.isValidForTrading(0.9, 0.05, 2.0)); // Слишком высокая корреляция
        assertFalse(pair.isValidForTrading(0.7, 0.01, 2.0)); // Слишком низкий p-value

        // Тест отображения
        String expectedDisplay = "ETH-USDT-SWAP/BTC-USDT-SWAP (z=2.50, r=0.80)";
        assertEquals(expectedDisplay, pair.getDisplayName());

        // Тест силы сигнала
        assertEquals(2.5, pair.getSignalStrength());

        // Тест направления
        assertEquals("MEAN_REVERSION", pair.getTradeDirection());
    }

    @Test
    void testNegativeZScore() {
        TradingPair pair = TradingPair.builder()
                .buyTicker("BTC-USDT-SWAP")
                .sellTicker("ETH-USDT-SWAP")
                .zscore(-1.8)
                .correlation(-0.75)
                .pValue(0.03)
                .build();

        // При отрицательном Z-score направление должно быть TREND_FOLLOWING
        assertEquals("TREND_FOLLOWING", pair.getTradeDirection());
        assertEquals(1.8, pair.getSignalStrength());
        
        // Проверяем валидность с отрицательной корреляцией
        assertTrue(pair.isValidForTrading(0.7, 0.05, 1.5));
    }
}