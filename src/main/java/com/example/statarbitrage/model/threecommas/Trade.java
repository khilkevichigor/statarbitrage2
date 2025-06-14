package com.example.statarbitrage.model.threecommas;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Trade {
    private String uuid;
    private int account_id;
    private String pair;
    private Order order;
    private Units units;
    private Status status;
    private Filled filled;
    private Long created_at;
    private Long closed_at;
    private Price price;
    private Conditional conditional;
    private Trailing trailing;
    private Timeout timeout;
}
