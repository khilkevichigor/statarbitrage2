package com.example.statarbitrage.bot;

import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.UserSettings;
import com.example.statarbitrage.processors.ScreenerProcessor;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.utils.StringUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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
    private final BotConfig botConfig;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isAutoScanRunning = new AtomicBoolean(false);
    private ScheduledFuture<?> autoScanTask;
    private Integer lastSentMessageId;
    private String lastSentText = ""; // новое поле

    public TelegramBot(BotConfig botConfig) {
        this.botConfig = botConfig;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand(BotMenu.SCAN_ALL.getName(), "Сканировать"));
        listOfCommands.add(new BotCommand(BotMenu.SCAN_BTC.getName(), "Сканировать BTC"));
        listOfCommands.add(new BotCommand(BotMenu.START_AUTOSCAN.getName(), "Старт автоскан"));
        listOfCommands.add(new BotCommand(BotMenu.STOP_AUTOSCAN.getName(), "Стоп автоскан"));
        listOfCommands.add(new BotCommand(BotMenu.GET_SETTINGS.getName(), "Получить настройки"));
        listOfCommands.add(new BotCommand(BotMenu.RESET_SETTINGS.getName(), "Сбросить настройки"));
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

            if (Objects.equals(text, BotMenu.SCAN_BTC.getName())) {
                log.info("-> SCAN_BTC");
                screenerProcessor.process(chatIdStr, "BTC-USDT-SWAP");
            } else if (Objects.equals(text, BotMenu.SCAN_ALL.getName())) {
                log.info("-> SCAN_ALL");
                screenerProcessor.process(chatIdStr, null);
            } else if (text.equals("/get_settings")) {
                log.info("-> GET_SETTINGS");
                UserSettings settings = settingsService.getSettings(chatId);
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
                    UserSettings newSettings = new com.fasterxml.jackson.databind.ObjectMapper().readValue(jsonPart, UserSettings.class);
                    settingsService.updateAllSettings(chatId, newSettings);
                    sendMessage(chatIdStr, "✅ Настройки успешно обновлены!");
                } catch (Exception e) {
                    log.warn("❌ Ошибка разбора JSON: {}", e.getMessage());
                    sendMessage(chatIdStr, "❌ Ошибка разбора JSON: " + e.getMessage());
                }
            } else if (text.equals("/reset_settings")) {
                log.info("-> RESET_SETTINGS");
                settingsService.resetSettings(chatId);
                sendMessage(chatIdStr, "🔄 Настройки сброшены на значения по умолчанию.");
            } else if (text.startsWith("/check")) {
                log.info("-> CHECK COIN");
                try {
                    String symbolPart = text.replace("/check", "").trim();
                    screenerProcessor.process(chatIdStr, StringUtil.getSymbol(symbolPart));
                } catch (Exception e) {
                    log.warn("❌ Ошибка разбора команды: {}", e.getMessage());
                    sendMessage(chatIdStr, "❌ Ошибка разбора команды: " + e.getMessage());
                }
            } else if (Objects.equals(text, BotMenu.START_AUTOSCAN.getName())) {
                log.info("-> START_AUTOSCAN");
                startAutoScan(chatIdStr);
            } else if (Objects.equals(text, BotMenu.STOP_AUTOSCAN.getName())) {
                log.info("-> STOP_AUTOSCAN");
                stopAutoScan(chatIdStr);
            }
        }
    }

    private void startAutoScan(String chatId) {
        if (isAutoScanRunning.get()) {
            sendMessage(chatId, "⏳ Авто-скан уже запущен");
            return;
        }

        isAutoScanRunning.set(true);
        sendMessage(chatId, "🔍 Автоскан запущен...");

        UserSettings userSettings = settingsService.getSettings(Long.parseLong(chatId));
        boolean sendNewEachTime = userSettings.isSendNewMessageOnAutoScan(); // новый флаг

        if (!sendNewEachTime) {
            // отправляем первое сообщение только если будет обновляться
            SendMessage initial = new SendMessage();
            initial.setChatId(chatId);
            initial.setText("Ждем монеты...");
            try {
                Message sent = execute(initial);
                lastSentMessageId = sent.getMessageId();
            } catch (TelegramApiException e) {
                log.error("Ошибка отправки стартового сообщения", e);
                return;
            }
        }

        autoScanTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                String result = screenerProcessor.scanAllAuto(chatId);
                String newText = result.isEmpty() ? "🤷Ничего не найдено" : result;

                if (!newText.equals(lastSentText)) {
                    if (sendNewEachTime) {
                        SendMessage newMessage = new SendMessage();
                        newMessage.setChatId(chatId);
                        newMessage.setText(newText);
                        execute(newMessage);
                    } else {
                        EditMessageText edit = new EditMessageText();
                        edit.setChatId(chatId);
                        edit.setMessageId(lastSentMessageId);
                        edit.setText(newText);
                        execute(edit);
                    }

                    lastSentText = newText;
                    log.info("Обновление авто-скана: {}", newText);
                }

            } catch (Exception e) {
                log.error("Ошибка в autoScan", e);
            }
        }, 0, userSettings.getAutoScan().getIntervalSec().intValue(), TimeUnit.SECONDS);
    }


    private void stopAutoScan(String chatId) {
        if (!isAutoScanRunning.get()) {
            sendMessage(chatId, "⛔ Авто-скан не запущен");
            return;
        }

        isAutoScanRunning.set(false);
        if (autoScanTask != null) {
            autoScanTask.cancel(true);
        }

        sendMessage(chatId, "✅ Авто-скан остановлен");
    }

    @EventListener
    public void onSendAsTextEvent(SendAsTextEvent event) {
        SendMessage message = new SendMessage();
        message.setChatId(event.getChatId());
        message.setText(event.getText());
        message.enableMarkdown(event.isEnableMarkdown());
        sendMessage(message);
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.enableMarkdown(true);
        sendMessage(message);
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
