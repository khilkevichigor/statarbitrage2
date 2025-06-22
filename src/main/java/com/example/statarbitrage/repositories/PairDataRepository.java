package com.example.statarbitrage.repositories;

import com.example.statarbitrage.model.PairData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PairDataRepository extends JpaRepository<PairData, Long> {
}
