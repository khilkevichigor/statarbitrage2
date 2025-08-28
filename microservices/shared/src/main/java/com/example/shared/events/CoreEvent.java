package com.example.shared.events;

import com.example.shared.models.TradingPair;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CoreEvent extends BaseEvent {
    private static final String BINDING_NAME = "core-events-out-0";
    private List<TradingPair> tradingPairs;
    private Type type;
    private String message;
    private String recipient;
    private CoreEvent.Priority priority;


    public enum Type {
        ADD_CLOSED_TO_CSV,
        CLEAR_COINT_PAIRS,
        MESSAGE_TO_TELEGRAM
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public CoreEvent(List<TradingPair> tradingPairs, Type type) {
        super("CORE_EVENT");
        super.setBindingName(BINDING_NAME);
        this.tradingPairs = tradingPairs;
        this.type = type;
    }

    public CoreEvent(String message, String recipient, CoreEvent.Priority priority, CoreEvent.Type type) {
        super("CORE_EVENT");
        super.setBindingName(BINDING_NAME);
        this.message = message;
        this.recipient = recipient;
        this.priority = priority;
        this.type = type;
    }
}