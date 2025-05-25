package com.example.statarbitrage.model;

import com.example.statarbitrage.calculators.StochRsiCalculator;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CoinParameters {
    private String symbol;
    private String emoji;
    private Double currentPrice;
    private Double vol24h;
    private Double btcCorr;
    private Double volat24h;
    private Double chg24h;

    private List<Double> htfCloses;
    private List<Double> ltfCloses;

    private List<Double> htfEma1;
    private List<Double> htfEma2;

    private Double ema1Angle;
    private Double ema2Angle;


    private Double distPriceToEma1;
    private Double distPriceToEma2;

    private Double distEma1ToEma2;

    private StochRsiCalculator.StochRsiResult htfStochRsi;

    private StochRsiCalculator.StochRsiResult ltfStochRsi;

    private List<Double> htfRsi;
    private List<Double> ltfRsi;

    private String tvLink;
}
