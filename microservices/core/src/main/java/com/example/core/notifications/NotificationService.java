package com.example.core.notifications;

import com.example.shared.models.PairData;
import org.springframework.stereotype.Service;

@Service
public interface NotificationService {
    void notifyClose(PairData pairData);
}
