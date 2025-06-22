package com.example.statarbitrage.repositories;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PairDataRepository extends JpaRepository<PairData, Long> {
    // 1. Сортировка по entryTime (от новых к старым)
    @Query("SELECT p FROM PairData p WHERE p.status = :status ORDER BY p.entryTime DESC")
    List<PairData> findAllByStatusOrderByEntryTimeDesc(@Param("status") TradeStatus status);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PairData p WHERE p.status = :status")
    int deleteAllByStatus(@Param("status") TradeStatus status);
}
