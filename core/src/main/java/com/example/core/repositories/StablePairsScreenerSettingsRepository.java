package com.example.core.repositories;

import com.example.shared.models.StablePairsScreenerSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с настройками скриннера стабильных пар
 */
@Repository
public interface StablePairsScreenerSettingsRepository extends JpaRepository<StablePairsScreenerSettings, Long> {

    /**
     * Найти настройки по умолчанию
     */
    Optional<StablePairsScreenerSettings> findByIsDefaultTrue();

    /**
     * Найти все настройки, отсортированные по времени последнего использования
     */
    @Query("SELECT s FROM StablePairsScreenerSettings s ORDER BY " +
            "CASE WHEN s.isDefault = true THEN 0 ELSE 1 END, " +
            "s.lastUsedAt DESC NULLS LAST, " +
            "s.updatedAt DESC")
    List<StablePairsScreenerSettings> findAllOrderedByUsage();

    /**
     * Найти настройки с автоматическим запуском
     */
    List<StablePairsScreenerSettings> findByRunOnScheduleTrue();

    /**
     * Найти настройки по имени (с игнорированием регистра)
     */
    Optional<StablePairsScreenerSettings> findByNameIgnoreCase(String name);

    /**
     * Проверить существование настроек с данным именем (кроме текущего ID)
     */
    @Query("SELECT COUNT(s) > 0 FROM StablePairsScreenerSettings s WHERE " +
            "LOWER(s.name) = LOWER(:name) AND " +
            "(:id IS NULL OR s.id != :id)")
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Сбросить флаг "по умолчанию" у всех настроек
     */
    @Query("UPDATE StablePairsScreenerSettings s SET s.isDefault = false")
    void resetAllDefaultFlags();
}