package com.example.shared.models;

import com.example.shared.dto.Candle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cached_candles", indexes = {
        @Index(name = "idx_cached_candles_ticker_timeframe_exchange",
                columnList = "ticker, timeframe, exchange"),
        @Index(name = "idx_cached_candles_timestamp",
                columnList = "timestamp"),
        @Index(name = "idx_cached_candles_ticker_timeframe_timestamp",
                columnList = "ticker, timeframe, timestamp"),
        @Index(name = "idx_cached_candles_exchange",
                columnList = "exchange")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"ticker", "timeframe", "exchange", "timestamp"})
public class CachedCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 50)
    private String ticker;

    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    @Column(name = "exchange", nullable = false, length = 20)
    private String exchange;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "open_price", nullable = false)
    private Double openPrice;

    @Column(name = "high_price", nullable = false)
    private Double highPrice;

    @Column(name = "low_price", nullable = false)
    private Double lowPrice;

    @Column(name = "close_price", nullable = false)
    private Double closePrice;

    @Column(name = "volume", nullable = false)
    private Double volume;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_valid", nullable = false)
    @Builder.Default
    private Boolean isValid = true;

    public static CachedCandle fromCandle(Candle candle, String ticker, String timeframe, String exchange) {
        return CachedCandle.builder()
                .ticker(ticker)
                .timeframe(timeframe)
                .exchange(exchange)
                .timestamp(candle.getTimestamp())
                .openPrice(candle.getOpen())
                .highPrice(candle.getHigh())
                .lowPrice(candle.getLow())
                .closePrice(candle.getClose())
                .volume(candle.getVolume())
                .isValid(true)
                .build();
    }

    public Candle toCandle() {
        return Candle.builder()
                .timestamp(this.timestamp)
                .open(this.openPrice)
                .high(this.highPrice)
                .low(this.lowPrice)
                .close(this.closePrice)
                .volume(this.volume)
                .build();
    }

    public String getCacheKey() {
        return String.format("%s_%s_%s_%d", exchange, ticker, timeframe, timestamp);
    }

    public boolean isOutdated(int maxAgeHours) {
        if (updatedAt == null) return true;
        return updatedAt.isBefore(LocalDateTime.now().minusHours(maxAgeHours));
    }
}