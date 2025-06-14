package com.example.statarbitrage.model.threecommas.response.trade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveTradesResponse {
    private List<Trade> trades;
}