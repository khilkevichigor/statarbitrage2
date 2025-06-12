package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DcaBotProfitResponse {
    private List<ProfitData> data;

    public List<ProfitData> getData() {
        return data;
    }

    public void setData(List<ProfitData> data) {
        this.data = data;
    }

    public static class ProfitData {
        @JsonProperty("s_date")
        private String sDate;

        @JsonProperty("unix_timestamp")
        private long unixTimestamp;

        private Profit profit;

        public String getSDate() {
            return sDate;
        }

        public void setSDate(String sDate) {
            this.sDate = sDate;
        }

        public long getUnixTimestamp() {
            return unixTimestamp;
        }

        public void setUnixTimestamp(long unixTimestamp) {
            this.unixTimestamp = unixTimestamp;
        }

        public Profit getProfit() {
            return profit;
        }

        public void setProfit(Profit profit) {
            this.profit = profit;
        }
    }

    public static class Profit {
        private String btc;
        private String usd;

        public String getBtc() {
            return btc;
        }

        public void setBtc(String btc) {
            this.btc = btc;
        }

        public String getUsd() {
            return usd;
        }

        public void setUsd(String usd) {
            this.usd = usd;
        }
    }
}
