package com.example.notification.bot;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

@Data
@EnableCaching
@EnableScheduling
@Configuration
@PropertySource(value = {"classpath:application.properties"})
public class AppPropertySource {
    @Value("${telegram.bot.name}")
    private String botName;
    @Value("${telegram.bot.token}")
    private String botToken;
    @Value("${telegram.bot.owner.chat.id}")
    private String ownerChatId;
}