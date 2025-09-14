package com.example.shared.events.rabbit;

import com.example.shared.models.Pair;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CoreEvent extends BaseEvent {
    private static final String BINDING_NAME = "core-events-out-0";
    private Pair tradingPair;
    private Type type;
    private String message;
    private byte[] data;

    public enum Type {
        ADD_CLOSED_TO_CSV,
        CLEAR_COINT_PAIRS,
        MESSAGE_TO_TELEGRAM,
        CLOSED_MESSAGE_TO_TELEGRAM,
        ENTRY_INTERSECTION_CHART
    }

    public CoreEvent(Pair tradingPair, Type type) {
        super("CORE_EVENT");
        super.setBindingName(BINDING_NAME);
        this.tradingPair = tradingPair;
        this.type = type;
    }

    public CoreEvent(String message, byte[] data, Type type) {
        super("CORE_EVENT");
        super.setBindingName(BINDING_NAME);
        this.data = data;
        this.type = type;
        this.message = message;
    }

    public CoreEvent(String message, Type type) {
        super("CORE_EVENT");
        super.setBindingName(BINDING_NAME);
        this.message = message;
        this.type = type;
    }
}