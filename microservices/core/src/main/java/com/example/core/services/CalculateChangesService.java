package com.example.core.services;

import com.example.shared.dto.ChangesData;
import com.example.shared.models.Pair;
import org.springframework.stereotype.Service;

@Service
public interface CalculateChangesService {
    ChangesData getChanges(Pair tradingPair);
}
