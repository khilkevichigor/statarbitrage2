package com.example.statarbitrage.notifications;

import com.example.statarbitrage.common.model.PairData;
import org.springframework.stereotype.Service;

@Service
public interface NotificationService {
    void notifyOpen(PairData pairData);

    void notifyClose(PairData pairData);
}
