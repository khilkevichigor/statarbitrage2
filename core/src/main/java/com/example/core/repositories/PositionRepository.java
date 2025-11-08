package com.example.core.repositories;

import com.example.shared.enums.PositionStatus;
import com.example.shared.enums.PositionType;
import com.example.shared.models.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findFirstByTradingPairIdAndTypeAndIsDeletedOrderByIdDesc(Long tradingPairId, PositionType type, Boolean isDeleted);

    List<Position> findAllByTradingPairIdAndTypeAndIsDeleted(Long tradingPairId, PositionType type, Boolean isDeleted);

    List<Position> findAllByStatusAndIsDeleted(PositionStatus status, Boolean isDeleted);

    Optional<Position> findByPositionIdAndIsDeleted(String positionId, Boolean isDeleted);

    List<Position> findAllByIsDeleted(Boolean isDeleted);

    @Query("SELECT p FROM Position p WHERE p.isDeleted = :isDeleted")
    List<Position> findAllWithDeletedFilter(@Param("isDeleted") Boolean isDeleted);

    // Методы без фильтра isDeleted для обратной совместимости (будут использовать false по умолчанию)
    default Optional<Position> findFirstByTradingPairIdAndTypeOrderByIdDesc(Long tradingPairId, PositionType type) {
        return findFirstByTradingPairIdAndTypeAndIsDeletedOrderByIdDesc(tradingPairId, type, false);
    }

    default List<Position> findAllByTradingPairIdAndType(Long tradingPairId, PositionType type) {
        return findAllByTradingPairIdAndTypeAndIsDeleted(tradingPairId, type, false);
    }

    default List<Position> findAllByStatus(PositionStatus status) {
        return findAllByStatusAndIsDeleted(status, false);
    }

    default Optional<Position> findByPositionId(String positionId) {
        return findByPositionIdAndIsDeleted(positionId, false);
    }

}
