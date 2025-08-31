package com.example.core.messaging;

import com.example.core.handlers.NewCointPairsEventHandler;
import com.example.shared.events.rabbit.CointegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Основной сервис для получения событий
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiveEventService {
    private final NewCointPairsEventHandler newCointPairsEventHandler;

    /**
     * Вычитываем топик Коинтеграции
     */
    @Bean
    public Consumer<CointegrationEvent> cointegrationEventsConsumer() {
        return this::handleEvent;
    }

    //todo сделать кнопку "Торговать" для наблюдаемых пар - например перевести BTC-ETH в торговлю! Или же сделать техтАреа для Отобранные пары по
    // аналогии с Наблюдаемыми! Или же сделать техтАреа в настройках что бы указывать пары которые нужно всегда мониторить на торговлю.

    //todo сделать таблицу открытых позиций для управления

    //todo брать окх дто позиции и хранить ее как есть у себя в бд и работать чисто с ней а не с текущей Position

    //todo сделать счетчик усреднений для TradingPairs - тупо поле =0 и потом инкрементить его

    //todo сделать в таблице на UI колонки long/short allocatedAmount для каждой монеты

    //todo сделать хард стоп при ошибках + уведомление в телеграм иначе за ночь можно влить весь депо в сделки по какой то причине (например при открытии второй монеты инет отвелился на 1мин и потом появился) - нужно стопать все и уведомлять и потом разбирать инцидент

    // + todo сделать в настройках апгрейд усреднения - сделать шаг для просадки - усредняемся первый раз - при -10%, второй - при последний процент(-10%) * шаг 1.5, третий - последний(-15%) * шаг 1.5, и тд

    private void handleEvent(CointegrationEvent event) {
        log.info("");
        log.info("📄 Получено событие: {}", event.getEventType());
        event.getCointPairs().forEach(v -> log.info("{} z={}", v.getPairName(), v.getZScoreCurrent()));
        try {
            // Обработка различных типов событий
            switch (event.getType()) {
                case NEW_COINT_PAIRS:
                    newCointPairsEventHandler.handle(event);
                    break;
                default:
                    log.warn("⚠️ Неизвестный тип события: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при обработке события: {}", e.getMessage(), e);
        }
    }
}