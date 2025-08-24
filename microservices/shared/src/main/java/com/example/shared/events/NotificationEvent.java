package com.example.shared.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationEvent extends BaseEvent {
    private String message;
    private String recipient;
    private NotificationType type;
    private Priority priority;
    
    public enum NotificationType {
        TELEGRAM, EMAIL, SMS
    }
    
    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public NotificationEvent(String message, String recipient, NotificationType type, Priority priority) {
        super("NOTIFICATION_EVENT");
        this.message = message;
        this.recipient = recipient;
        this.type = type;
        this.priority = priority;
    }
}