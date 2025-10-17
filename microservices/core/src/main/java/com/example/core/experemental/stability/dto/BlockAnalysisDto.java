package com.example.core.experemental.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class BlockAnalysisDto {
    private Integer score;
    private Boolean stability;
    private Map<String, Object> details;
}