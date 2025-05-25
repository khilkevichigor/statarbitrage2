package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {
    private Double priceChangePeriodHrs;
    private int topGainersLosersQnty;

    private TimeFrameSettings htf;
    private TimeFrameSettings ltf;

    private VolumeSettings volume;
    private AngleSettings emaAngles;
    private DistanceSettings distances;
    private CorrelationSettings correlation;
    private AutoScanSettings autoScan;

    private boolean useRenko;
    private boolean useRandomizer;
    private boolean useLong;
    private boolean useShort;
    private boolean useVolume;
    private boolean useTopGainersLosers;
    private boolean useCurrBar; //если false значит используем prevBar
    private boolean sendNewMessageOnAutoScan;

    private boolean useEma1;
    private boolean useEma2;
    private boolean usePriceToEma1;
    private boolean usePriceToEma2;
    private boolean useEma1ToEma2;
    private boolean useEma1Angle;
    private boolean useEma2Angle;
    private boolean usePriceCrossEmas;
    private boolean useStochRsiDiver;

    private boolean useHtfStochRsi;
    private boolean useLtfStochRsi;
    private boolean useHtfStochRsiLevel;
    private boolean useLtfStochRsiLevel;
    private boolean useHtfStochRsiCross;
    private boolean useLtfStochRsiCross;
    private boolean useTrendBreak;
    private boolean useHtfRsi;
    private boolean useLtfRsi;

    public boolean htfStochRsiOversoldIsApplicable() {
        return useHtfStochRsi
                && useHtfStochRsiLevel
                && htf != null
                && htf.getStochRsi() != null
                && htf.getStochRsi().getOversold() != null;
    }

    public boolean htfStochRsiOverboughtIsApplicable() {
        return useHtfStochRsi
                && useHtfStochRsiLevel
                && htf != null
                && htf.getStochRsi() != null
                && htf.getStochRsi().getOverbought() != null;
    }

    public boolean ltfStochRsiOversoldIsApplicable() {
        return useLtfStochRsi
                && useLtfStochRsiLevel
                && ltf != null
                && ltf.getStochRsi() != null
                && ltf.getStochRsi().getOversold() != null;
    }

    public boolean ltfStochRsiOverboughtIsApplicable() {
        return useLtfStochRsi
                && useLtfStochRsiLevel
                && ltf != null
                && ltf.getStochRsi() != null
                && ltf.getStochRsi().getOverbought() != null;
    }

    public boolean topGainersLosersIsApplicable() {
        return useTopGainersLosers && topGainersLosersQnty > 0;
    }

    public boolean htfStochRsiCrossIsApplicable() {
        return useHtfStochRsi
                && htf != null
                && htf.getStochRsi() != null
                && htf.getStochRsi().getOversold() != null
                && htf.getStochRsi().getOverbought() != null;
    }

    public boolean ltfStochRsiCrossIsApplicable() {
        return useLtfStochRsi
                && ltf != null
                && ltf.getStochRsi() != null
                && ltf.getStochRsi().getOversold() != null
                && ltf.getStochRsi().getOverbought() != null;
    }

    public boolean htfRsiIsApplicable() {
        return useHtfRsi
                && htf != null
                && htf.getRsi() != null
                && htf.getRsi().getOversold() != null
                && htf.getRsi().getOverbought() != null;
    }

    public boolean ltfRsiIsApplicable() {
        return useLtfRsi
                && ltf != null
                && ltf.getRsi() != null
                && ltf.getRsi().getOversold() != null
                && ltf.getRsi().getOverbought() != null;
    }

    // --- вложенные классы ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeFrameSettings {
        private String tfName;
        private Double ema1Period;
        private Double ema2Period;
        private StochRsiSettings stochRsi;
        private RsiSettings rsi;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StochRsiSettings {
        private Double rsiPeriod;
        private Double stochPeriod;
        private Double kPeriod;
        private Double dPeriod;
        private Double oversold;
        private Double overbought;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RsiSettings {
        private Double rsiPeriod;
        private Double oversold;
        private Double overbought;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeSettings {
        private Double thresholdMln;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AngleSettings {
        private Double ema1Min;
        private Double ema1Max;
        private Double ema2Min;
        private Double ema2Max;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistanceSettings {
        private Double priceToEma1Min;
        private Double priceToEma1Max;
        private Double priceToEma2Min;
        private Double priceToEma2Max;
        private Double ema1ToEma2Min;
        private Double ema1ToEma2Max;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorrelationSettings {
        private Double btcCorrPeriodHrs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoScanSettings {
        private Double intervalSec;
    }
}
