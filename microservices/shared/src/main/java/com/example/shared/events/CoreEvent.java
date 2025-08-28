package com.example.shared.events;

import com.example.shared.models.CointPair;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CoreEvent extends BaseEvent {
    private static final String BINDING_NAME = "core-events-out-0";
    private List<CointPair> cointPairs;
    private Type type;

    public enum Type {
        NEW_COINT_PAIRS,
        CLEAR_COINT_PAIRS
    }

    public CoreEvent(List<CointPair> cointPairs, Type type) {
        super("CORE_EVENT");
        super.setBindingName(BINDING_NAME);
        this.cointPairs = cointPairs;
        this.type = type;
    }
}