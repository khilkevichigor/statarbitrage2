package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.model.PairData;
import org.springframework.stereotype.Service;

@Service
public interface CalculateChangesService {
    ChangesData getChanges(PairData pairData);
}
