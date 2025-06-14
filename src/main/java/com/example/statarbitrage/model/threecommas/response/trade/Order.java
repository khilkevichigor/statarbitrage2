package com.example.statarbitrage.model.threecommas.response.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class Order {
    private String type;
    private String side;
    private String strategy;
    private String position_side;
    private boolean reduce_only;
}
