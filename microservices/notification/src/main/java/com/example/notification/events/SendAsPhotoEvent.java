package com.example.notification.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SendAsPhotoEvent {
    private String chatId;
    private File photo;
    private byte[] photoBytes;
    private String caption;
    private boolean enableMarkdown;
}
