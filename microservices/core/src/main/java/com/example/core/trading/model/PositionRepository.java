package com.example.core.trading.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findFirstByPairDataIdAndTypeOrderByIdDesc(Long pairDataId, PositionType type);

    List<Position> findAllByPairDataIdAndType(Long pairDataId, PositionType type);

    List<Position> findAllByStatus(PositionStatus status);

    Optional<Position> findByPositionId(String positionId);

}
