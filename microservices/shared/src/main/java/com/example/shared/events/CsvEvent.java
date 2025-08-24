package com.example.shared.events;

import com.example.shared.models.PairData;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CsvEvent extends BaseEvent {
    private PairData pairData;

    public CsvEvent(PairData pairData) {
        super("EXPORT_PAIR_DATA_REPORT");
        this.pairData = pairData;
    }
}