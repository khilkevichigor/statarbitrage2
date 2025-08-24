package com.example.core.core.services;

import com.example.core.common.dto.ChangesData;
import com.example.shared.models.PairData;
import org.springframework.stereotype.Service;

@Service
public interface CalculateChangesService {
    ChangesData getChanges(PairData pairData);
}
