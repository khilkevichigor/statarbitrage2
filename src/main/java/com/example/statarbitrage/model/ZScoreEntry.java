package com.example.statarbitrage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZScoreEntry {
    private String a;
    private String b;
    private double zscore;
    private double pvalue;
    private String direction;
}
