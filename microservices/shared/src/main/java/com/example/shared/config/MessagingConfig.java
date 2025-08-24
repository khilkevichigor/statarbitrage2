package com.example.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;

@Configuration
public class MessagingConfig {
    
    @Bean
    public MessageConverter messageConverter() {
        return new MappingJackson2MessageConverter();
    }
}