package com.example.candles.repositories;

import com.example.shared.models.CachedCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CachedCandleRepository extends JpaRepository<CachedCandle, Long> {

    List<CachedCandle> findByTickerAndTimeframeAndExchangeOrderByTimestampAsc(
            String ticker, String timeframe, String exchange);

    List<CachedCandle> findByTickerAndTimeframeAndExchangeAndTimestampBetweenOrderByTimestampAsc(
            String ticker, String timeframe, String exchange, Long fromTimestamp, Long toTimestamp);

    @Query("SELECT cc FROM CachedCandle cc WHERE cc.ticker = :ticker AND cc.timeframe = :timeframe " +
            "AND cc.exchange = :exchange AND cc.timestamp >= :fromTimestamp ORDER BY cc.timestamp ASC")
    List<CachedCandle> findByTickerTimeframeExchangeFromTimestamp(
            @Param("ticker") String ticker,
            @Param("timeframe") String timeframe,
            @Param("exchange") String exchange,
            @Param("fromTimestamp") Long fromTimestamp);

    @Query("SELECT cc FROM CachedCandle cc WHERE cc.ticker = :ticker AND cc.timeframe = :timeframe " +
            "AND cc.exchange = :exchange AND cc.timestamp <= :toTimestamp ORDER BY cc.timestamp DESC")
    List<CachedCandle> findByTickerTimeframeExchangeToTimestamp(
            @Param("ticker") String ticker,
            @Param("timeframe") String timeframe,
            @Param("exchange") String exchange,
            @Param("toTimestamp") Long toTimestamp);

    @Query("SELECT MAX(cc.timestamp) FROM CachedCandle cc WHERE cc.ticker = :ticker " +
            "AND cc.timeframe = :timeframe AND cc.exchange = :exchange")
    Optional<Long> findMaxTimestampByTickerTimeframeExchange(
            @Param("ticker") String ticker,
            @Param("timeframe") String timeframe,
            @Param("exchange") String exchange);

    @Query("SELECT MIN(cc.timestamp) FROM CachedCandle cc WHERE cc.ticker = :ticker " +
            "AND cc.timeframe = :timeframe AND cc.exchange = :exchange")
    Optional<Long> findMinTimestampByTickerTimeframeExchange(
            @Param("ticker") String ticker,
            @Param("timeframe") String timeframe,
            @Param("exchange") String exchange);

    @Query("SELECT COUNT(cc) FROM CachedCandle cc WHERE cc.ticker = :ticker " +
            "AND cc.timeframe = :timeframe AND cc.exchange = :exchange")
    Long countByTickerTimeframeExchange(
            @Param("ticker") String ticker,
            @Param("timeframe") String timeframe,
            @Param("exchange") String exchange);

    @Query("SELECT DISTINCT cc.ticker FROM CachedCandle cc WHERE cc.exchange = :exchange " +
            "AND cc.timeframe = :timeframe ORDER BY cc.ticker")
    List<String> findDistinctTickersByExchangeAndTimeframe(
            @Param("exchange") String exchange,
            @Param("timeframe") String timeframe);

    @Query("SELECT DISTINCT cc.ticker FROM CachedCandle cc WHERE cc.exchange = :exchange " +
            "ORDER BY cc.ticker")
    List<String> findDistinctTickersByExchange(@Param("exchange") String exchange);

    @Query("SELECT cc FROM CachedCandle cc WHERE cc.exchange = :exchange " +
            "AND cc.timeframe = :timeframe AND cc.ticker IN :tickers " +
            "AND cc.timestamp >= :fromTimestamp ORDER BY cc.ticker, cc.timestamp")
    List<CachedCandle> findByExchangeTimeframeTickersFromTimestamp(
            @Param("exchange") String exchange,
            @Param("timeframe") String timeframe,
            @Param("tickers") List<String> tickers,
            @Param("fromTimestamp") Long fromTimestamp);

    @Query("SELECT cc.ticker, COUNT(cc) as candleCount FROM CachedCandle cc " +
            "WHERE cc.exchange = :exchange AND cc.timeframe = :timeframe " +
            "GROUP BY cc.ticker ORDER BY cc.ticker")
    List<Object[]> getCandleCountByTickerForTimeframe(
            @Param("exchange") String exchange,
            @Param("timeframe") String timeframe);

    // УДАЛЕНО: deleteByTickerTimeframeExchange - больше не нужно удаление
    // УДАЛЕНО: deleteOldCandlesByExchangeTimeframe - оставляем все исторические данные

    @Modifying
    @Transactional
    @Query("UPDATE CachedCandle cc SET cc.isValid = false WHERE cc.updatedAt < :cutoffDate")
    void markInvalidOldCandles(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT cc FROM CachedCandle cc WHERE cc.isValid = false")
    List<CachedCandle> findInvalidCandles();

    @Query("SELECT cc.exchange, cc.timeframe, COUNT(cc) as totalCount " +
            "FROM CachedCandle cc WHERE cc.isValid = true " +
            "GROUP BY cc.exchange, cc.timeframe ORDER BY cc.exchange, cc.timeframe")
    List<Object[]> getCacheStatistics();
}