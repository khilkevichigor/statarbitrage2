package com.example.statarbitrage.model.threecommas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Trailing {
    private boolean enabled;
    private String value;
    private String percent;
}