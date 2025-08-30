package com.example.shared.events.rabbit;

import com.example.shared.models.CointPair;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CointegrationEvent extends BaseEvent {
    private static final String BINDING_NAME = "cointegration-events-out-0";
    private List<CointPair> cointPairs;
    private CointegrationEvent.Type type;

    public enum Type {
        NEW_COINT_PAIRS
    }

    public CointegrationEvent(List<CointPair> cointPairs, CointegrationEvent.Type type) {
        super("COINTEGRATION_EVENT");
        super.setBindingName(BINDING_NAME);
        this.cointPairs = cointPairs;
        this.type = type;
    }
}