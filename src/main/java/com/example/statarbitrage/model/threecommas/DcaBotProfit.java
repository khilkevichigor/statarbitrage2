package com.example.statarbitrage.model.threecommas;

import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class DcaBotProfit {
    private List<ProfitData> data;
}
