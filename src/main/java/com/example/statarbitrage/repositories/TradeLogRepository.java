package com.example.statarbitrage.repositories;

import com.example.statarbitrage.model.TradeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {
    List<TradeLog> findByLongTickerAndShortTicker(String longTicker, String shortTicker);
}
