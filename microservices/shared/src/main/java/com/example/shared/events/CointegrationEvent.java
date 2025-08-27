package com.example.shared.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CointegrationEvent extends BaseEvent {

    public CointegrationEvent(String eventType) {
        super(eventType);
    }
}