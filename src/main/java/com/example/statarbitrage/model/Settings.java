package com.example.statarbitrage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String timeframe;
    private double candleLimit;
    private double windowSize;
    private double significanceLevel;
    private double adfSignificanceLevel;
    private double checkInterval;
    private double capitalLong;
    private double capitalShort;
    private double leverage;
    private double feePctPerTrade;

    private double exitTake;
    private double exitStop;
    private double exitZMin;
    private double exitZMaxPercent;
    private double exitTimeHours;

    private double minCorrelation;
    private double minVolume;

    @Column(name = "simulation_enabled")
    private boolean simulationEnabled = false;
}
