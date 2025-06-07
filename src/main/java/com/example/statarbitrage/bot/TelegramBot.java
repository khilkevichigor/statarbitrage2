package com.example.statarbitrage.bot;

import com.example.statarbitrage.events.ResetProfitEvent;
import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.processors.ScreenerProcessor;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.FileService;
import com.example.statarbitrage.services.SettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TelegramBot extends TelegramLongPollingBot {
    @Autowired
    private ScreenerProcessor screenerProcessor;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private FileService fileService;
    @Autowired
    private EventSendService eventSendService;
    private final BotConfig botConfig;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isStartTestTradeRunning = new AtomicBoolean(false);
    private ScheduledFuture<?> testTradeTask;

    public TelegramBot(BotConfig botConfig) {
        this.botConfig = botConfig;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand(BotMenu.FIND.getName(), "Искать"));
//        listOfCommands.add(new BotCommand(BotMenu.START_TEST_TRADE.getName(), "Старт тест-трейд"));
        listOfCommands.add(new BotCommand(BotMenu.START_TEST_TRADE_L_A_S_B.getName(), "Старт тест-трейд long a, short b"));
        listOfCommands.add(new BotCommand(BotMenu.START_TEST_TRADE_L_B_S_A.getName(), "Старт тест-трейд long b, short a"));
        listOfCommands.add(new BotCommand(BotMenu.STOP_TEST_TRADE.getName(), "Стоп тест-трейд"));
        listOfCommands.add(new BotCommand(BotMenu.GET_SETTINGS.getName(), "Получить настройки"));
        listOfCommands.add(new BotCommand(BotMenu.RESET_SETTINGS.getName(), "Сбросить настройки"));
        listOfCommands.add(new BotCommand(BotMenu.DELETE_FILES.getName(), "Удалить файлы"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            String chatIdStr = String.valueOf(update.getMessage().getChatId());
            long chatId = update.getMessage().getChatId();
            Message message = update.getMessage();
            String text = message.getText();

            if (Objects.equals(text, BotMenu.FIND.getName())) {
                log.info("-> FIND");
                stopTestTrade(chatIdStr);
                sendMessage(chatIdStr, "🔍 Поиск лучшей пары запущен...");
                //todo сначала слать лучший чарт по огромному тф 1ч или 1д для понимания
                screenerProcessor.sendBestChart(chatIdStr);
            } else if (Objects.equals(text, BotMenu.GET_SETTINGS.getName())) {
                log.info("-> GET_SETTINGS");
                Settings settings = settingsService.getSettings();
                String json;
                try {
                    json = new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(settings);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                sendMessage(chatIdStr, "```json\n" + json + "\n```");

            } else if (text.startsWith("/set_settings") || text.startsWith("/ss")) {
                log.info("-> SET_SETTINGS");
                try {
                    String jsonPart = text.replace(text.startsWith("/set_settings") ? "/set_settings" : "/ss", "").trim();
                    Settings newSettings = new com.fasterxml.jackson.databind.ObjectMapper().readValue(jsonPart, Settings.class);
                    settingsService.updateAllSettings(chatId, newSettings);
                    sendMessage(chatIdStr, "✅ Настройки успешно обновлены!");
                } catch (Exception e) {
                    log.warn("❌ Ошибка разбора JSON: {}", e.getMessage());
                    sendMessage(chatIdStr, "❌ Ошибка разбора JSON: " + e.getMessage());
                }
            } else if (Objects.equals(text, BotMenu.RESET_SETTINGS.getName())) {
                log.info("-> RESET_SETTINGS");
                settingsService.resetSettings(chatId);
                sendMessage(chatIdStr, "🔄 Настройки сброшены на значения по умолчанию.");
            }
//            else if (Objects.equals(text, BotMenu.START_TEST_TRADE.getName())) {
//                log.info("-> START_TEST_TRADE");
//                startTestTrade(chatIdStr);
//            }
            else if (Objects.equals(text, BotMenu.START_TEST_TRADE_L_A_S_B.getName())) {
                log.info("-> START_TEST_TRADE_L_A_S_B");
                startTestTrade(chatIdStr, true);
            } else if (Objects.equals(text, BotMenu.START_TEST_TRADE_L_B_S_A.getName())) {
                log.info("-> START_TEST_TRADE_L_B_S_A");
                startTestTrade(chatIdStr, false);
            } else if (Objects.equals(text, BotMenu.STOP_TEST_TRADE.getName())) {
                log.info("-> STOP_TEST_TRADE");
                stopTestTrade(chatIdStr);
            } else if (Objects.equals(text, BotMenu.DELETE_FILES.getName())) {
                log.info("-> DELETE_FILES");
                fileService.deleteSpecificFilesInProjectRoot(List.of("z_score.json", "pair_data.json", "candles.json"));
            }
        }
    }

    private void startTestTrade(String chatId, boolean isLasb) {
        if (isStartTestTradeRunning.get()) {
            sendMessage(chatId, "⏳ Тест-трейд уже запущен");
            return;
        }

        isStartTestTradeRunning.set(true);
        sendMessage(chatId, "🔍 Тест-трейд запущен...");

        testTradeTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                screenerProcessor.testTrade(chatId, isLasb);
            } catch (Exception e) {
                log.error("Ошибка в testTrade()", e);
            }
        }, 0, 60L * settingsService.getSettings().getCheckInterval(), TimeUnit.SECONDS);
    }

    private void stopTestTrade(String chatId) {
        if (!isStartTestTradeRunning.get()) {
            sendMessage(chatId, "⛔ Тест-трейд не запущен");
            return;
        }

        isStartTestTradeRunning.set(false);
        if (testTradeTask != null) {
            testTradeTask.cancel(true);
        }

        resetProfit(true);
        sendMessage(chatId, "✅ Тест-трейд остановлен");
    }

    public void resetProfit(boolean withLogging) {
        try {
            eventSendService.sendResetProfitEvent(ResetProfitEvent.builder().build());
            if (withLogging) {
                log.info("Сбросили профит");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при сбросе профита: {}", e.getMessage(), e);
        }
    }

    @EventListener
    public void onSendAsTextEvent(SendAsTextEvent event) {
        SendMessage message = new SendMessage();
        message.setChatId(event.getChatId());
        message.setText(event.getText());
        message.enableMarkdown(event.isEnableMarkdown());
        sendMessage(message);
    }

    @EventListener
    public void onSendAsPhotoEvent(SendAsPhotoEvent event) {
        SendPhoto photo = new SendPhoto();
        photo.setChatId(event.getChatId());
        photo.setPhoto(new InputFile(event.getPhoto()));
        photo.setCaption(event.getCaption());
        photo.setParseMode(event.isEnableMarkdown() ? "Markdown" : null);

        sendPhoto(photo); // ✅ метод, который отправляет фото
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.enableMarkdown(true);
        sendMessage(message);
    }

    private void sendPhoto(SendPhoto photo) {
        try {
            execute(photo);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке фото: {}", e.getMessage(), e);
        }
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
