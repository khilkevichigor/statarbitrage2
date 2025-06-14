package com.example.statarbitrage.model.threecommas.response.bot;

import com.example.statarbitrage.model.threecommas.ProfitData;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class DcaBotProfit {
    private List<ProfitData> data;
}
