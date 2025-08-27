package com.example.shared.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CointegrationEvent extends BaseEvent {
    private static final String BINDING_NAME = "cointegration-events-out-0";
    private Type type;

    public enum Type {
        CLEAR_PAIRS
    }

    public CointegrationEvent(Type type) {
        super("COINTEGRATION_EVENT");
        super.setBindingName(BINDING_NAME);
        this.type = type;
    }
}