//package com.example.cointegration.repositories;
//
//import com.example.shared.models.ChartSettings;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.stereotype.Repository;
//
//import java.util.Optional;
//
//@Repository
//public interface ChartSettingsRepository extends JpaRepository<ChartSettings, Long> {
//
//    /**
//     * Находит настройки чарта по типу
//     *
//     * @param chartType тип чарта
//     * @return настройки чарта если найдены
//     */
//    Optional<ChartSettings> findByChartType(String chartType);
//}