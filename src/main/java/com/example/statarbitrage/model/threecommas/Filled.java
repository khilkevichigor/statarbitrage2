package com.example.statarbitrage.model.threecommas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Filled {
    private String units;
    private String total;
    private String price;
}