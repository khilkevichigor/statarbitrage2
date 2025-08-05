package com.example.statarbitrage.common.events;

import lombok.*;

@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class SendAsTextEvent {
    private String chatId;
    private String text;
    private boolean enableMarkdown;
}
