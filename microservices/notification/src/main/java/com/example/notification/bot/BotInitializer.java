package com.example.notification.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotInitializer {
    private final TelegramBot bot;
    private boolean initialized = false;

    @EventListener({ContextRefreshedEvent.class})
    public void init() throws TelegramApiException {
        if (initialized) {
            log.debug("Telegram бот уже инициализирован, пропускаем");
            return;
        }
        
        if (bot.getBotToken() == null || bot.getBotToken().equals("YOUR_BOT_TOKEN") || bot.getBotToken().isEmpty()) {
            log.warn("⚠️ Telegram бот не инициализирован - токен не задан или некорректен");
            return;
        }
        
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            telegramBotsApi.registerBot(bot);
            initialized = true;
            log.info("✅ Telegram бот успешно зарегистрирован");
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка регистрации Telegram бота: {}", e.getMessage());
        }
    }
}
