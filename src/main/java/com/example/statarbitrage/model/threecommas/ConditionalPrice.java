package com.example.statarbitrage.model.threecommas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ConditionalPrice {
    private String value;
    private String type;
}