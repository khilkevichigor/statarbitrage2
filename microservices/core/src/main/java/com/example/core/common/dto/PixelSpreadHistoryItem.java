package com.example.core.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PixelSpreadHistoryItem {
    private long timestamp;
    private double pixelDistance;
}