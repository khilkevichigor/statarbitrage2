package com.example.statarbitrage.bot;

import com.example.statarbitrage.common.events.SendAsPhotoEvent;
import com.example.statarbitrage.common.events.SendAsTextEvent;
import com.example.statarbitrage.core.services.StatisticsService;
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

@Slf4j
@Service
public class TelegramBot extends TelegramLongPollingBot {
    @Autowired
    private StatisticsService statisticsService;

    private final BotConfig botConfig;

    public TelegramBot(BotConfig botConfig) {
        this.botConfig = botConfig;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand(BotMenu.GET_CSV.name(), "Получить csv"));
        listOfCommands.add(new BotCommand(BotMenu.GET_STATISTIC.name(), "Получить стату"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка установки команд бота: " + e.getMessage());
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

            switch (text) {
//                case GET_CSV_COMMAND -> getCsvCommand(chatIdStr);
//                case GET_STATISTIC_COMMAND -> getStatisticCommand(chatIdStr);
                default -> {
                    //ignore
                }
            }
        }
    }

//    private void getStatisticCommand(String chatIdStr) {
//        statisticsService.printTradeStatistics(chatIdStr);
//    }

//    private void getCsvCommand(String chatIdStr) {
//        log.info("-> {}", BotMenu.GET_CSV.name());
//        sendDocumentToTelegram(chatIdStr, new File(TEST_TRADES_CSV_FILE));
//    }

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
            log.error("❌ Ошибка при отправке фото: {}", e.getMessage(), e);
        }
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения в телеграм. Сообщение {}. Ошибка {}", message.getText(), e.getMessage(), e);
            e.printStackTrace();
        }
    }

//    public void sendDocumentToTelegram(String chatId, File file) {
//        SendDocument document = new SendDocument();
//        document.setChatId(chatId);
//        document.setDocument(new InputFile(file));
//
//        try {
//            execute(document);
//        } catch (TelegramApiException e) {
//            log.error("❌ Ошибка при отправке документа: {}", e.getMessage(), e);
//        }
//    }
}
