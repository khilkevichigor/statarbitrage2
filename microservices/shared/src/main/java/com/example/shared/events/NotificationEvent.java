package com.example.shared.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationEvent extends BaseEvent {
    private static final String BINDING_NAME = "notification-events-out-0";
    private String message;
    private String recipient;
    private Type type;
    private Priority priority;

    public enum Type {
        TELEGRAM
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public NotificationEvent(String message, String recipient, Priority priority, Type type) {
        super("NOTIFICATION_EVENT");
        super.setBindingName(BINDING_NAME);
        this.message = message;
        this.recipient = recipient;
        this.type = type;
        this.priority = priority;
    }
}