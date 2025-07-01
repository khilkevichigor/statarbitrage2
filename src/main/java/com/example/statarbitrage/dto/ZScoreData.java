package com.example.statarbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ZScoreData {
    private String longTicker;
    private String shortTicker;
    private List<ZScoreParam> zscoreParams;

    public ZScoreParam getLastZScoreParam() {
        return zscoreParams.get(zscoreParams.size() - 1);
    }
}
