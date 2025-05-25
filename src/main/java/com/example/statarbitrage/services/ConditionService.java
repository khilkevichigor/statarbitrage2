package com.example.statarbitrage.services;

import com.example.statarbitrage.conditionals.CandleConditions;
import com.example.statarbitrage.conditionals.RenkoConditions;
import com.example.statarbitrage.model.CoinParameters;
import com.example.statarbitrage.model.UserSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConditionService {
    public boolean checkAndSetEmoji(UserSettings userSettings, CoinParameters coinParameters) {
        if (userSettings.isUseRenko()) {
            return RenkoConditions.check(userSettings, coinParameters);
        } else {
            return CandleConditions.check(userSettings, coinParameters);
        }
    }
}
