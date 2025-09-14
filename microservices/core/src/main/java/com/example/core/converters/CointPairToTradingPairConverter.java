//package com.example.core.converters;
//
//import com.example.shared.models.CointPair;
//import java.math.BigDecimal;
//import com.example.shared.models.Pair;
//import com.example.shared.enums.PairType;
//import org.springframework.core.convert.converter.Converter;
//import org.springframework.stereotype.Component;
//
//@Component
//public class CointPairToTradingPairConverter implements Converter<CointPair, Pair> {
//
//    @Override
//    public Pair convert(CointPair source) {
//        if (source == null) {
//            return null;
//        }
//
//        return Pair.builder()
//                .type(PairType.TRADING)
////                .id(source.getId())
//                .uuid(source.getUuid())
////                .version(source.getVersion())
//                .status(source.getStatus())
//                .errorDescription(source.getErrorDescription())
//                .longTickerCandlesJson(source.getLongTickerCandlesJson())
//                .shortTickerCandlesJson(source.getShortTickerCandlesJson())
//                .longTickerCandles(source.getLongTickerCandles())
//                .shortTickerCandles(source.getShortTickerCandles())
//                .zScoreHistoryJson(source.getZScoreHistoryJson())
//                .profitHistoryJson(source.getProfitHistoryJson())
//                .pixelSpreadHistoryJson(source.getPixelSpreadHistoryJson())
//                .zScoreHistory(source.getZScoreHistory())
//                .profitHistory(source.getProfitHistory())
//                .pixelSpreadHistory(source.getPixelSpreadHistory())
//                .tickerA(source.getLongTicker())
//                .tickerB(source.getShortTicker())
//                .pairName(source.getPairName())
//                .longTickerEntryPrice(BigDecimal.valueOf(source.getLongTickerEntryPrice()))
//                .longTickerCurrentPrice(BigDecimal.valueOf(source.getLongTickerCurrentPrice()))
//                .shortTickerEntryPrice(BigDecimal.valueOf(source.getShortTickerEntryPrice()))
//                .shortTickerCurrentPrice(BigDecimal.valueOf(source.getShortTickerCurrentPrice()))
//                .meanEntry(BigDecimal.valueOf(source.getMeanEntry()))
//                .meanCurrent(BigDecimal.valueOf(source.getMeanCurrent()))
//                .spreadEntry(BigDecimal.valueOf(source.getSpreadEntry()))
//                .spreadCurrent(BigDecimal.valueOf(source.getSpreadCurrent()))
//                .zScoreEntry(BigDecimal.valueOf(source.getZScoreEntry()))
//                .zScoreCurrent(BigDecimal.valueOf(source.getZScoreCurrent()))
//                .pValueEntry(BigDecimal.valueOf(source.getPValueEntry()))
//                .pValueCurrent(BigDecimal.valueOf(source.getPValueCurrent()))
//                .adfPvalueEntry(BigDecimal.valueOf(source.getAdfPvalueEntry()))
//                .adfPvalueCurrent(BigDecimal.valueOf(source.getAdfPvalueCurrent()))
//                .correlationEntry(BigDecimal.valueOf(source.getCorrelationEntry()))
//                .correlationCurrent(BigDecimal.valueOf(source.getCorrelationCurrent()))
//                .alphaEntry(BigDecimal.valueOf(source.getAlphaEntry()))
//                .alphaCurrent(BigDecimal.valueOf(source.getAlphaCurrent()))
//                .betaEntry(BigDecimal.valueOf(source.getBetaEntry()))
//                .betaCurrent(BigDecimal.valueOf(source.getBetaCurrent()))
//                .stdEntry(BigDecimal.valueOf(source.getStdEntry()))
//                .stdCurrent(BigDecimal.valueOf(source.getStdCurrent()))
//                .zScoreChanges(source.getZScoreChanges())
//                .longUSDTChanges(source.getLongUSDTChanges())
//                .longPercentChanges(source.getLongPercentChanges())
//                .shortUSDTChanges(source.getShortUSDTChanges())
//                .shortPercentChanges(source.getShortPercentChanges())
//                .portfolioBeforeTradeUSDT(source.getPortfolioBeforeTradeUSDT())
//                .profitUSDTChanges(source.getProfitUSDTChanges())
//                .portfolioAfterTradeUSDT(source.getPortfolioAfterTradeUSDT())
//                .profitPercentChanges(source.getProfitPercentChanges())
//                .minutesToMinProfitPercent(source.getMinutesToMinProfitPercent())
//                .minutesToMaxProfitPercent(source.getMinutesToMaxProfitPercent())
//                .minProfitPercentChanges(source.getMinProfitPercentChanges())
//                .maxProfitPercentChanges(source.getMaxProfitPercentChanges())
//                .formattedTimeToMinProfit(source.getFormattedTimeToMinProfit())
//                .formattedTimeToMaxProfit(source.getFormattedTimeToMaxProfit())
//                .formattedProfitLong(source.getFormattedProfitLong())
//                .formattedProfitShort(source.getFormattedProfitShort())
//                .formattedProfitCommon(source.getFormattedProfitCommon())
//                .timestamp(source.getTimestamp())
//                .createdAt(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(source.getEntryTime()), java.time.ZoneId.systemDefault()))
//                .searchDate(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(source.getEntryTime()), java.time.ZoneId.systemDefault()))
//                .entryTime(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(source.getEntryTime()), java.time.ZoneId.systemDefault()))
//                .updatedTime(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(source.getUpdatedTime()), java.time.ZoneId.systemDefault()))
//                .maxZ(source.getMaxZ())
//                .minZ(source.getMinZ())
//                .maxLong(source.getMaxLong())
//                .minLong(source.getMinLong())
//                .maxShort(source.getMaxShort())
//                .minShort(source.getMinShort())
//                .maxCorr(source.getMaxCorr())
//                .minCorr(source.getMinCorr())
//                .exitReason(source.getExitReason())
//                .closeAtBreakeven(source.isCloseAtBreakeven())
//                .settingsCandleLimit(BigDecimal.valueOf(source.getSettingsCandleLimit()))
//                .settingsMinZ(BigDecimal.valueOf(source.getSettingsMinZ()))
//                // Оставляем только основные поля, которые есть в Pair
//                .averagingCount(source.getAveragingCount())
//                .lastAveragingTimestamp(source.getLastAveragingTimestamp())
//                .intersectionsCount(source.getIntersectionsCount())
//                .build();
//    }
//}
