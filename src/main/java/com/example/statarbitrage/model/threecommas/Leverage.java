package com.example.statarbitrage.model.threecommas;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Leverage {
    private boolean enabled;
    private String type;
}