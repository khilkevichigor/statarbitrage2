package com.example.core.repositories;

import com.example.shared.models.PortfolioHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PortfolioHistoryRepository extends JpaRepository<PortfolioHistory, Long> {

    /**
     * Найти историю за указанный период времени
     */
    @Query("SELECT ph FROM PortfolioHistory ph WHERE ph.snapshotTime >= :fromTime ORDER BY ph.snapshotTime ASC")
    List<PortfolioHistory> findBySnapshotTimeGreaterThanEqual(@Param("fromTime") LocalDateTime fromTime);

    /**
     * Найти историю за период между двумя датами
     */
    @Query("SELECT ph FROM PortfolioHistory ph WHERE ph.snapshotTime >= :fromTime AND ph.snapshotTime <= :toTime ORDER BY ph.snapshotTime ASC")
    List<PortfolioHistory> findBySnapshotTimeBetween(@Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime);

    /**
     * Найти последнюю запись
     */
    @Query("SELECT ph FROM PortfolioHistory ph ORDER BY ph.snapshotTime DESC LIMIT 1")
    PortfolioHistory findLatest();

    /**
     * Найти первую запись за определенный период
     */
    @Query("SELECT ph FROM PortfolioHistory ph WHERE ph.snapshotTime >= :fromTime ORDER BY ph.snapshotTime ASC LIMIT 1")
    PortfolioHistory findFirstAfter(@Param("fromTime") LocalDateTime fromTime);

    /**
     * Удалить записи старше указанной даты (для очистки старых данных)
     */
    @Modifying
    @Query("DELETE FROM PortfolioHistory ph WHERE ph.snapshotTime < :beforeTime")
    void deleteBySnapshotTimeBefore(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * Подсчитать количество записей за период
     */
    @Query("SELECT COUNT(ph) FROM PortfolioHistory ph WHERE ph.snapshotTime >= :fromTime")
    long countBySnapshotTimeGreaterThanEqual(@Param("fromTime") LocalDateTime fromTime);
}