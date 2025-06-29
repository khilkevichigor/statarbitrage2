package com.example.statarbitrage.bot;

import com.example.statarbitrage.events.ResetProfitEvent;
import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.events.StartNewTradeEvent;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.processors.ScreenerProcessor;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.FileService;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.services.StatisticsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.statarbitrage.constant.Constants.*;

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
    @Autowired
    private StatisticsService statisticsService;

    private final BotConfig botConfig;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isStartTestTradeRunning = new AtomicBoolean(false);
    private ScheduledFuture<?> testTradeTask;

    public TelegramBot(BotConfig botConfig) {
        this.botConfig = botConfig;
        List<BotCommand> listOfCommands = new ArrayList<>();
//        listOfCommands.add(new BotCommand(BotMenu.FIND.getName(), "Искать"));
//        listOfCommands.add(new BotCommand(BotMenu.START_TEST_TRADE.getName(), "Старт тест-трейд"));
        listOfCommands.add(new BotCommand(BotMenu.FIND_AND_START_TEST_TRADE.getName(), "Искать+тест-трейд"));
//        listOfCommands.add(new BotCommand(BotMenu.START_REAL_TRADE.getName(), "Старт рил-трейд"));
//        listOfCommands.add(new BotCommand(BotMenu.STOP_REAL_TRADE.getName(), "Стоп рил-трейд"));
//        listOfCommands.add(new BotCommand(BotMenu.STOP_TEST_TRADE.getName(), "Стоп тест-трейд"));
//        listOfCommands.add(new BotCommand(BotMenu.START_SIMULATION.getName(), "Старт симуляции"));
//        listOfCommands.add(new BotCommand(BotMenu.TEST_3COMMAS_API.getName(), "Тест 3commas API"));
        listOfCommands.add(new BotCommand(BotMenu.GET_SETTINGS.getName(), "Получить настройки"));
//        listOfCommands.add(new BotCommand(BotMenu.RESET_SETTINGS.getName(), "Сбросить настройки"));
        listOfCommands.add(new BotCommand(BotMenu.GET_CSV.getName(), "Получить csv"));
        listOfCommands.add(new BotCommand(BotMenu.GET_STATISTIC.getName(), "Получить стату"));
//        listOfCommands.add(new BotCommand(BotMenu.DELETE_FILES.getName(), "Удалить файлы"));
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

            switch (text) {
                case FIND_COMMAND -> findCommand(chatIdStr);
                case GET_SETTINGS_COMMAND -> getSettingsCommand(chatIdStr);
                case RESET_SETTINGS_COMMAND -> resetSettingsCommand(chatId, chatIdStr);
                case START_TEST_TRADE_COMMAND -> startTestTradeCommand(chatIdStr);
                case START_REAL_TRADE_COMMAND -> startRealTradeCommand(chatIdStr);
                case STOP_REAL_TRADE_COMMAND -> stopRealTradeCommand(chatIdStr);
                case STOP_TEST_TRADE_COMMAND -> stopTestTradeCommand(chatIdStr);
                case DELETE_FILES_COMMAND -> deleteFilesCommand();
                case START_SIMULATION_COMMAND -> startSimulationCommand(chatIdStr);
                case GET_CSV_COMMAND -> getCsvCommand(chatIdStr);
                case TEST_3COMMAS_API_COMMAND -> test3commasApiCommand(chatIdStr);
                case FIND_AND_TEST_TRADE_COMMAND -> findAndTestNewTradeCommand(chatIdStr);
                case GET_STATISTIC_COMMAND -> getStatisticCommand(chatIdStr);
                default -> handleMessage(text, chatIdStr);
            }
        }
    }

    private void getStatisticCommand(String chatIdStr) {
        statisticsService.printTradeStatistics(chatIdStr);
    }

    private void handleMessage(String text, String chatIdStr) {
        if (text.startsWith(SET_SETTINGS) || text.startsWith(SET_SETTINGS_SHORT)) {
            log.info("-> {}", SET_SETTINGS);
            setSettings(text, chatIdStr);
        }
    }

    private void findCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.FIND.name());
        stopTestTrade(chatIdStr);
        sendMessage(chatIdStr, "🔍 Поиск лучшей пары запущен...");
        screenerProcessor.findBestAsync(chatIdStr);
    }

    private void getSettingsCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.GET_SETTINGS.name());
        sendSettings(chatIdStr);
    }

    private void resetSettingsCommand(long chatId, String chatIdStr) {
        log.info("-> {}", BotMenu.RESET_SETTINGS.name());
        settingsService.resetSettings(chatId);
        sendMessage(chatIdStr, "🔄 Настройки сброшены на значения по умолчанию.");
    }

    private void startTestTradeCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.START_TEST_TRADE.name());
        startTestTrade(chatIdStr);
    }

    private void startRealTradeCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.START_REAL_TRADE.name());
        startRealTrade(chatIdStr);
        //startTestTrade(chatIdStr);
    }

    private void stopRealTradeCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.STOP_REAL_TRADE.name());
        stopRealTrade(chatIdStr);
        stopTestTrade(chatIdStr);
    }

    private void stopTestTradeCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.STOP_TEST_TRADE.name());
        stopTestTrade(chatIdStr);
    }

    private void deleteFilesCommand() {
        log.info("-> {}", BotMenu.DELETE_FILES.name());
        fileService.deleteSpecificFilesInProjectRoot(List.of(Z_SCORE_FILE_NAME, PAIR_DATA_FILE_NAME, CANDLES_FILE_NAME));
    }

    private void startSimulationCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.START_SIMULATION.name());
        startSimulation(chatIdStr, TradeType.GENERAL);
    }

    private void getCsvCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.GET_CSV.name());
        sendDocumentToTelegram(chatIdStr, new File(TEST_TRADES_CSV_FILE));
    }

    private void test3commasApiCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.TEST_3COMMAS_API.name());
        screenerProcessor.test3commasApi(chatIdStr);
    }

    private void findAndTestNewTradeCommand(String chatIdStr) {
        log.info("-> {}", BotMenu.FIND_AND_START_TEST_TRADE.name());
        stopTestTrade(chatIdStr);
        sendMessage(chatIdStr, "🔍 Поиск лучшей пары запущен...");
        screenerProcessor.findBest(chatIdStr);
        startTestTrade(chatIdStr);
    }

    private void startRealTrade(String chatIdStr) {
        screenerProcessor.startRealTrade(chatIdStr);
    }

    private void stopRealTrade(String chatIdStr) {
        screenerProcessor.stopRealTrade(chatIdStr);
    }

    private void setSettings(String text, String chatIdStr) {
        try {
            String jsonPart = text.replace(text.startsWith(SET_SETTINGS) ? SET_SETTINGS : SET_SETTINGS_SHORT, "").trim();
            Settings newSettings = new com.fasterxml.jackson.databind.ObjectMapper().readValue(jsonPart, Settings.class);
            settingsService.updateAllSettings(Long.parseLong(chatIdStr), newSettings);
            sendMessage(chatIdStr, "✅ Настройки успешно обновлены!");
        } catch (Exception e) {
            log.warn("❌ Ошибка разбора JSON: {}", e.getMessage());
            sendMessage(chatIdStr, "❌ Ошибка разбора JSON: " + e.getMessage());
        }
    }

    private void sendSettings(String chatIdStr) {
        Settings settings = settingsService.getSettingsFromDb();
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        sendMessage(chatIdStr, "```json\n" + json + "\n```");
    }

    private void startSimulation(String chatId, TradeType tradeType) {

    }

    private void startTestTrade(String chatId) {
        if (isStartTestTradeRunning.get()) {
            sendMessage(chatId, "⏳ Тест-трейд уже запущен");
            return;
        }

        isStartTestTradeRunning.set(true);
        sendMessage(chatId, "🔍 Тест-трейд запущен...");

        testTradeTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                screenerProcessor.testTradeAsync(chatId);
            } catch (Exception e) {
                log.error("Ошибка в testTrade()", e);
            }
        }, 0, 60L * (int) settingsService.getSettingsFromDb().getCheckInterval(), TimeUnit.SECONDS);
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

    @EventListener
    public void onStartNewTradeEvent(StartNewTradeEvent event) {
        sendMessage(event.getChatId(), "Новый трейд...");
        findAndTestNewTradeCommand(event.getChatId());
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

    public void sendDocumentToTelegram(String chatId, File file) {
        SendDocument document = new SendDocument();
        document.setChatId(chatId);
        document.setDocument(new InputFile(file));

        try {
            execute(document);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке документа: {}", e.getMessage(), e);
        }
    }
}
