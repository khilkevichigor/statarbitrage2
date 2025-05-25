package com.example.statarbitrage.messages;

import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;

public final class SignalMessageBuilder {
    private SignalMessageBuilder() {
    }

    public static String buildSignalText(UserSettings settings, CoinParameters coinParameters) {
        StringBuilder sb = new StringBuilder();

        //price+ema
        sb.append("*").append(coinParameters.getSymbol()).append("* ")
                .append(settings.isUseRenko() ? " (Renko) " : "")
                .append(coinParameters.getEmoji()).append("\n\n");

        sb.append("Цена: ").append(String.format("%.4f", coinParameters.getCurrentPrice())).append("\n");

        sb.append(settings.getHtf()).append(" Ema")
                .append(settings.getHtf().getEma1Period()).append(": ")
                .append(String.format("%.2f", coinParameters.getHtfEma1().get(0))).append("\n\n");

        //indicators
        sb.append(settings.isUsePriceToEma1() ? "☑️ " : "").append(settings.getHtf()).append(" Расстояние до Ema")
                .append(settings.getHtf().getEma1Period()).append(": ")
                .append(String.format("%.1f", coinParameters.getDistPriceToEma1())).append("%\n");

        sb.append(settings.isUseHtfStochRsiCross() ? "☑️ " : "").append(settings.getHtf()).append(" StochRsi(")
                .append(settings.getHtf().getStochRsi().getOversold()).append(",")
                .append(settings.getHtf().getStochRsi().getOverbought()).append(") ")
                .append("Crossing").append("\n\n");

        //common
        sb.append("Волатильность(24ч): ")
                .append(String.format("%.0f", coinParameters.getVolat24h())).append("%\n");

        sb.append("Корреляция(")
                .append(settings.getCorrelation().getBtcCorrPeriodHrs()).append("ч): ")
                .append(String.format("%.0f", coinParameters.getBtcCorr())).append("%\n");

        sb.append("Изм цены(")
                .append(settings.getPriceChangePeriodHrs()).append("ч): ")
                .append(String.format("%.2f", coinParameters.getChg24h())).append("%\n\n");

        sb.append(coinParameters.getTvLink());

        return sb.toString();
    }

    public static String getInfo(CoinParameters coinParameters, UserSettings userSettings) {
        StringBuilder sb = new StringBuilder();
        boolean isAdded = false;
        if (userSettings.isUseHtfStochRsi()) {
            isAdded = true;
            sb.append("\n");
            sb.append("Htf StochRsi: ");
            sb.append("K").append(String.format("%.2f", coinParameters.getHtfStochRsi().kValues.get(0))).append(", ");
            sb.append("D").append(String.format("%.2f", coinParameters.getHtfStochRsi().dValues.get(0)));
        }
        if (userSettings.isUseLtfStochRsi()) {
            isAdded = true;
            sb.append("\n");
            sb.append("Ltf StochRsi: ");
            sb.append("K").append(String.format("%.2f", coinParameters.getLtfStochRsi().kValues.get(0))).append(", ");
            sb.append("D").append(String.format("%.2f", coinParameters.getLtfStochRsi().dValues.get(0)));
        }
        if (userSettings.isUseHtfRsi()) {
            isAdded = true;
            sb.append("\n");
            sb.append("Htf Rsi: ");
            sb.append(String.format("%.2f", coinParameters.getHtfRsi().get(0)));
        }
        if (userSettings.isUseLtfStochRsi()) {
            isAdded = true;
            sb.append("\n");
            sb.append("Ltf Rsi: ");
            sb.append(String.format("%.2f", coinParameters.getLtfRsi().get(0)));
        }
        if (userSettings.isUseEma1() || userSettings.isUseEma2()) {
            if (userSettings.isUseEma1Angle() || userSettings.isUseEma2Angle()) {
                isAdded = true;
                sb.append("\n");
                sb.append("Ema angle: ").append(String.format("%.2f", coinParameters.getEma2Angle()));
            }
            if (userSettings.isUsePriceToEma1() || userSettings.isUsePriceToEma2()) {
                isAdded = true;
                sb.append("\n");
                sb.append("Dist to Ema: ").append(String.format("%.2f", coinParameters.getDistPriceToEma2()));
            }
        }
        if (userSettings.isUseTopGainersLosers()) {
            isAdded = true;
            sb.append("\n");
            sb.append("Chg24h: ").append(String.format("%.2f", coinParameters.getChg24h()));
        }
        if (isAdded) {
            sb.append("\n");
        }
        return sb.toString();
    }
}
