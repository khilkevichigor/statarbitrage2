package com.example.core.notifications;

import com.example.core.common.model.PairData;
import org.springframework.stereotype.Service;

@Service
public interface NotificationService {
    void notifyOpen(PairData pairData);

    void notifyClose(PairData pairData);
}
