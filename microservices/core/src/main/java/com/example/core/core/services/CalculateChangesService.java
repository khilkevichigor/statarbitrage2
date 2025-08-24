package com.example.core.core.services;

import com.example.core.common.dto.ChangesData;
import com.example.core.common.model.PairData;
import org.springframework.stereotype.Service;

@Service
public interface CalculateChangesService {
    ChangesData getChanges(PairData pairData);
}
