package com.example.statarbitrage.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SendAsTextEvent {
    private String chatId;
    private String text;
    private boolean enableMarkdown;
}
