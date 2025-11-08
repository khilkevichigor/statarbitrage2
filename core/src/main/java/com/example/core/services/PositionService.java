package com.example.core.services;

import com.example.core.repositories.PositionRepository;
import com.example.shared.enums.PositionStatus;
import com.example.shared.enums.PositionType;
import com.example.shared.models.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;

    /**
     * Получить все позиции (без удаленных)
     */
    public List<Position> getAllPositions() {
        return positionRepository.findAllByIsDeleted(false);
    }

    /**
     * Получить все позиции с фильтром по isDeleted
     */
    public List<Position> getAllPositions(Boolean includeDeleted) {
        if (includeDeleted == null || !includeDeleted) {
            return getAllPositions();
        }
        return positionRepository.findAll();
    }

    /**
     * Получить только удаленные позиции
     */
    public List<Position> getDeletedPositions() {
        return positionRepository.findAllByIsDeleted(true);
    }

    /**
     * Получить позиции по статусу
     */
    public List<Position> getPositionsByStatus(PositionStatus status) {
        return positionRepository.findAllByStatus(status);
    }

    /**
     * Получить открытые позиции
     */
    public List<Position> getOpenPositions() {
        return getPositionsByStatus(PositionStatus.OPEN);
    }

    /**
     * Получить закрытые позиции
     */
    public List<Position> getClosedPositions() {
        return getPositionsByStatus(PositionStatus.CLOSED);
    }

    /**
     * Получить позиции по ID торговой пары (без удаленных)
     */
    public List<Position> getPositionsByTradingPairId(Long tradingPairId) {
        return getAllPositions().stream()
                .filter(p -> p.getTradingPairId().equals(tradingPairId))
                .collect(Collectors.toList());
    }

    /**
     * Получить позиции по ID торговой пары с фильтром по isDeleted
     */
    public List<Position> getPositionsByTradingPairId(Long tradingPairId, Boolean includeDeleted) {
        return getAllPositions(includeDeleted).stream()
                .filter(p -> p.getTradingPairId().equals(tradingPairId))
                .collect(Collectors.toList());
    }

    /**
     * Получить позиции по типу (без удаленных)
     */
    public List<Position> getPositionsByType(PositionType type) {
        return getAllPositions().stream()
                .filter(p -> p.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Получить позиции по типу с фильтром по isDeleted
     */
    public List<Position> getPositionsByType(PositionType type, Boolean includeDeleted) {
        return getAllPositions(includeDeleted).stream()
                .filter(p -> p.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Получить статистику по позициям
     */
    public Map<String, Object> getPositionsStatistics() {
        List<Position> allPositions = getAllPositions();

        long totalPositions = allPositions.size();
        long openPositions = allPositions.stream().mapToLong(p -> p.isOpen() ? 1 : 0).sum();
        long closedPositions = totalPositions - openPositions;

        // Подсчет по типам
        long longPositions = allPositions.stream().mapToLong(p -> p.getType() == PositionType.LONG ? 1 : 0).sum();
        long shortPositions = totalPositions - longPositions;

        // Подсчет прибыли/убытка
        BigDecimal totalUnrealizedPnL = allPositions.stream()
                .filter(Position::isOpen)
                .map(Position::getUnrealizedPnLUSDT)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRealizedPnL = allPositions.stream()
                .filter(p -> !p.isOpen())
                .map(Position::getRealizedPnLUSDT)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalPositions", totalPositions,
                "openPositions", openPositions,
                "closedPositions", closedPositions,
                "longPositions", longPositions,
                "shortPositions", shortPositions,
                "totalUnrealizedPnL", totalUnrealizedPnL,
                "totalRealizedPnL", totalRealizedPnL
        );
    }

    /**
     * Сохранить позицию
     */
    @Transactional
    public Position save(Position position) {
        return positionRepository.save(position);
    }

    /**
     * Мягкое удаление позиции (soft delete)
     */
    @Transactional
    public void softDelete(Position position) {
        position.setIsDeleted(true);
        positionRepository.save(position);
        log.info("Position {} soft deleted", position.getId());
    }

    /**
     * Мягкое удаление позиции по ID
     */
    @Transactional
    public void softDeleteById(Long id) {
        Position position = findById(id);
        if (position != null && !position.getIsDeleted()) {
            softDelete(position);
        }
    }

    /**
     * Восстановление удаленной позиции
     */
    @Transactional
    public void restore(Position position) {
        position.setIsDeleted(false);
        positionRepository.save(position);
        log.info("Position {} restored", position.getId());
    }

    /**
     * Восстановление удаленной позиции по ID
     */
    @Transactional
    public void restoreById(Long id) {
        Position position = positionRepository.findById(id).orElse(null);
        if (position != null && position.getIsDeleted()) {
            restore(position);
        }
    }

    /**
     * Массовое мягкое удаление позиций для торговой пары
     */
    @Transactional
    public void softDeleteByTradingPairId(Long tradingPairId) {
        List<Position> positions = getPositionsByTradingPairId(tradingPairId, false); // Только активные
        positions.forEach(this::softDelete);
        log.info("Soft deleted {} positions for trading pair {}", positions.size(), tradingPairId);
    }

    /**
     * Жесткое удаление позиции (безвозвратное) - только для административных целей
     * ВНИМАНИЕ: Этот метод приватный, чтобы предотвратить случайное использование
     */
    @Transactional
    private void hardDelete(Position position) {
        positionRepository.delete(position);
        log.warn("Position {} hard deleted (irreversible)", position.getId());
    }

    /**
     * Найти позицию по ID (включая удаленные)
     */
    public Position findById(Long id) {
        return positionRepository.findById(id).orElse(null);
    }

    /**
     * Найти активную позицию по ID (без удаленных)
     */
    public Position findActiveById(Long id) {
        Position position = findById(id);
        return (position != null && !position.getIsDeleted()) ? position : null;
    }

    /**
     * Найти позицию по внешнему ID
     */
    public Position findByPositionId(String positionId) {
        return positionRepository.findByPositionId(positionId).orElse(null);
    }
}