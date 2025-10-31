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
     * Получить все позиции
     */
    public List<Position> getAllPositions() {
        return positionRepository.findAll();
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
     * Получить позиции по ID торговой пары
     */
    public List<Position> getPositionsByTradingPairId(Long tradingPairId) {
        return positionRepository.findAll().stream()
                .filter(p -> p.getTradingPairId().equals(tradingPairId))
                .collect(Collectors.toList());
    }

    /**
     * Получить позиции по типу
     */
    public List<Position> getPositionsByType(PositionType type) {
        return positionRepository.findAll().stream()
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
     * Удалить позицию
     */
    @Transactional
    public void delete(Position position) {
        positionRepository.delete(position);
    }

    /**
     * Найти позицию по ID
     */
    public Position findById(Long id) {
        return positionRepository.findById(id).orElse(null);
    }

    /**
     * Найти позицию по внешнему ID
     */
    public Position findByPositionId(String positionId) {
        return positionRepository.findByPositionId(positionId).orElse(null);
    }
}