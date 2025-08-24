package com.example.shared.models;

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