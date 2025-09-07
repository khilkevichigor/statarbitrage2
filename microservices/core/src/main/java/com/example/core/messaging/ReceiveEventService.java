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

    //===КЕЙС ликвидация BERA/NMR===

    /*
    ПЕРЕСЕЧЕНИЕ ГРАФИКОВ
    у меня есть сервис который считает пересечение в пикселях для картинки и в точках для нормализованных цен
    пиксели: com.example.core.experemental.intersections.AnalyzeChartIntersectionsService.analyze
    точки:   com.example.core.services.PriceIntersectionService.calculateIntersectionsWithChart

    чарты с точками попадают в /Users/igorkhilkevich/IdeaProjects/statarbitrage/microservices/charts/filter/intersections работает на этапе получения пар от Cointegration
    чарты с пикселями генерятся на лету и сохраняются на рабочем столе. Работает через Постман

    теперь нужно определиться что выбрать, точки вроде норм!
    нужно при открытии пары слать в телегу чарт с пересечениями что бы трекать как работает

     */

    //todo перейти на отбор пар из топ-N по coinmarketcap
    //todo сделать уведомления что просадка >10, >20, >30 - соответственно ставим на карандаш и чекаем ликвидацию
    //todo чекер что позицию ликвиднуло
    //todo после ликвидации BERA-NMR может есть смысл торговать только проверенные пары во избежания такого трешака
    //todo как то чекать чарт наложенных цен - что цены переплетаются (может через телегу - кидать чарт и кнопку "go")
    //todo сделать глобально черный список пар, может через бд, что бы для определенного таймфрейма пара не отбиралась
    //todo если ликвиднуло 1 монету из пары - сделать синк чтобы я понимал что происходит и что одна позиция закрылась! При получении закрытых позиций или ордеров чекать "Ликвидация купить"
    //todo на UI сделать ввод notes - что бы туда я мог добавлять комменты по паре - например "ликвиднуло А, Б закрыл по кнопке"
    //===КЕЙС ликвидация BERA/NMR===

    // !!! todo сделать раздельные колонки "Max просадка" и "Время до Max просадки" что бы можно было фильтровать пары и потом сделать страницу анализа - сколько в среднем у нас просадка по всем парам, какая она, сколько времени торгуется пара, и это поможет понять какие настройки выставлять
    // !! todo в настройках сделать чекбокс - "автообъем" по которому будем брать 1/11 от депо на папу, последняя 11-ая часть идет на усреднение в просадках

    //todo сделать функцию автоинвестирования - размер позиций расчитывается в зависимости от баланса! Баланс растет - объем позиции больше! Можно сделать путем взятия % от депо! Например делить депо на 11 частей! 10 в пары, 1 часть на усреднения! И так каждый раз!

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