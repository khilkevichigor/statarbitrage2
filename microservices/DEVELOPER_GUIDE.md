# 🚀 Руководство разработчика по микросервисам StatArbitrage

## 📋 Содержание
- [Быстрый старт](#-быстрый-старт)
- [Запуск микросервисов](#-запуск-микросервисов)
- [Разработка в IDE](#-разработка-в-ide)
- [Работа с событиями](#-работа-с-событиями)
- [Отладка и мониторинг](#-отладка-и-мониторинг)
- [Миграция существующего кода](#-миграция-существующего-кода)
- [FAQ](#-faq)

## 🚀 Быстрый старт

### 1. Предварительные требования
```bash
# Проверить Java
java -version  # Должна быть Java 17+

# Проверить Maven
mvn -version   # Должен быть Maven 3.6+

# Проверить Docker
docker -v      # Для RabbitMQ
```

### 2. Первый запуск системы
```bash
# Перейти в директорию микросервисов
cd /Users/igorkhilkevich/IdeaProjects/statarbitrage/microservices

# Запустить все сервисы
./start-all.sh

# Дождаться полного запуска (30-60 сек)
```

### 3. Проверка работоспособности
```bash
# Проверить RabbitMQ
open http://localhost:15672  # admin/admin123

# Проверить микросервисы
curl http://localhost:8080/actuator/health  # Core
curl http://localhost:8081/actuator/health  # Notification
curl http://localhost:8082/actuator/health  # Analytics
```

## 🔧 Запуск микросервисов

### Способ 1: Скрипт для всех сервисов (рекомендуется)
```bash
# Запуск всех микросервисов
./start-all.sh

# Остановка всех микросервисов
./stop-all.sh

# Проверка статуса
./test-system.sh
```

### Способ 2: Запуск отдельного микросервиса из командной строки
```bash
# Запустить только RabbitMQ
docker-compose up -d rabbitmq

# Запустить отдельный микросервис
cd core
mvn spring-boot:run

# Или в фоне
nohup mvn spring-boot:run > ../logs/core.log 2>&1 &
```

### Способ 3: Запуск в IDE (IntelliJ IDEA/VS Code)

#### В IntelliJ IDEA:
1. **Импортировать проект:**
   - `File` → `Open` → выбрать папку `microservices`
   - IntelliJ автоматически распознает Maven проект

2. **Запустить RabbitMQ:**
   ```bash
   cd microservices
   docker-compose up -d rabbitmq
   ```

3. **Запустить микросервис:**
   - Найти файл `CoreApplication.java` в папке `core/src/main/java/com/example/core/`
   - **Правый клик** → `Run 'CoreApplication'`
   - Или нажать **зеленый треугольник** рядом с `main` методом

4. **Запустить несколько сервисов:**
   - Аналогично найти `NotificationApplication.java`, `AnalyticsApplication.java` и т.д.
   - Запустить каждый **правым кликом** → `Run`

#### В VS Code:
1. **Открыть папку микросервисов**
2. **Установить расширения:**
   - Extension Pack for Java
   - Spring Boot Extension Pack

3. **Запустить сервис:**
   - Найти `CoreApplication.java`
   - Нажать `Run` над `main` методом
   - Или `F5` для отладки

### Способ 4: Выборочный запуск через Maven
```bash
# Запустить только нужные сервисы
mvn spring-boot:run -pl core
mvn spring-boot:run -pl notification  
mvn spring-boot:run -pl analytics
```

## 🎯 Разработка в IDE

### Настройка проекта
1. **Импорт как Maven Multi-Module проект:**
   ```
   microservices/
   ├── pom.xml                 ← Открыть этот файл в IDE
   ├── shared/
   ├── core/
   └── ...
   ```

2. **Структура модулей:**
   - **shared** - общие компоненты (не запускается)
   - **core** - основной сервис
   - **notification** - уведомления  
   - **analytics** - аналитика
   - **default** - шаблон для новых сервисов

### Добавление нового функционала

#### В существующий микросервис (например, Core):
```java
// Файл: core/src/main/java/com/example/core/service/TradingService.java
@Service
@Slf4j
public class TradingService {
    
    @Autowired
    private EventPublisher eventPublisher;
    
    public void executeTrading() {
        log.info("💰 Выполнение торговой операции");
        
        // Ваша логика торговли
        
        // Отправка события о сделке
        TradingEvent event = new TradingEvent("BTC-USDT", "BUY", 
            new BigDecimal("0.001"), new BigDecimal("45000"), "EXECUTED");
        eventPublisher.publish("trading-events-out-0", event);
    }
}
```

#### Создание нового микросервиса:
```bash
# 1. Скопировать шаблон
cp -r default trading-bot

# 2. Переименовать класс приложения
# trading-bot/src/main/java/com/example/defaultservice/DefaultServiceApplication.java
# → trading-bot/src/main/java/com/example/tradingbot/TradingBotApplication.java

# 3. Обновить pom.xml
sed -i '' 's/default/trading-bot/g' trading-bot/pom.xml
sed -i '' 's/Default Template Service/Trading Bot Service/g' trading-bot/pom.xml

# 4. Изменить порт в application.yml
sed -i '' 's/8097/8098/g' trading-bot/src/main/resources/application.yml

# 5. Добавить в родительский pom.xml
echo "        <module>trading-bot</module>" >> pom.xml
```

## 📨 Работа с событиями

### Отправка событий
```java
@RestController
@RequiredArgsConstructor
public class TradingController {
    
    private final EventPublisher eventPublisher;
    
    @PostMapping("/trade")
    public ResponseEntity<String> executeTrade() {
        // Отправка торгового события
        TradingEvent event = new TradingEvent("BTC-USDT", "BUY", 
            new BigDecimal("0.001"), new BigDecimal("45000"), "EXECUTED");
        eventPublisher.publish("trading-events-out-0", event);
        
        // Отправка уведомления
        NotificationEvent notification = new NotificationEvent(
            "🚀 Сделка выполнена: BTC-USDT BUY 0.001", 
            "telegram_chat_id",
            NotificationType.TELEGRAM, 
            Priority.HIGH
        );
        eventPublisher.publish("notification-events-out-0", notification);
        
        return ResponseEntity.ok("Сделка отправлена в обработку");
    }
}
```

### Обработка событий
```java
@Service
@Slf4j
public class TradingEventsProcessor {
    
    @Bean
    public Consumer<TradingEvent> tradingEventsConsumer() {
        return event -> {
            log.info("📨 Получено торговое событие: {} {}", 
                     event.getSymbol(), event.getAction());
                     
            // Обработка события
            processTradingEvent(event);
        };
    }
    
    private void processTradingEvent(TradingEvent event) {
        switch (event.getAction()) {
            case "BUY" -> handleBuyOrder(event);
            case "SELL" -> handleSellOrder(event);
            default -> log.warn("Неизвестное действие: {}", event.getAction());
        }
    }
}
```

### Конфигурация событий в application.yml
```yaml
spring:
  cloud:
    function:
      definition: tradingEventsConsumer
    stream:
      bindings:
        # Исходящие события
        trading-events-out-0:
          destination: trading.events
          content-type: application/json
        # Входящие события
        tradingEventsConsumer-in-0:
          destination: trading.events
          content-type: application/json
          group: trading-service-group
```

## 🔍 Отладка и мониторинг

### Просмотр логов
```bash
# Логи всех сервисов
ls logs/
tail -f logs/core.log
tail -f logs/notification.log

# Логи в реальном времени
tail -f logs/*.log
```

### Мониторинг системы
```bash
# Проверка портов
lsof -i :8080,8081,8082  # Core, Notification, Analytics

# Проверка Java процессов
ps aux | grep java

# Память и CPU
htop  # или Activity Monitor на Mac
```

### Health Check всех сервисов
```bash
# Скрипт для проверки всех сервисов
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8091 8092 8093 8094 8095 8096 8097; do
    echo "Проверка порта $port:"
    curl -s http://localhost:$port/actuator/health | jq .status 2>/dev/null || echo "Не запущен"
done
```

### RabbitMQ Management
- **URL:** http://localhost:15672
- **Логин:** admin / admin123
- **Что смотреть:**
  - `Queues` - очереди сообщений
  - `Connections` - подключения микросервисов
  - `Exchanges` - точки обмена сообщениями

## 🔄 Миграция существующего кода

### Пошаговая миграция:

#### Шаг 1: Скопировать основные классы в Core
```bash
# Скопировать все классы из основного проекта
cp -r ../src/main/java/com/example/statarbitrage/* core/src/main/java/com/example/core/

# Обновить package в каждом файле
find core/src/main/java -name "*.java" -exec sed -i '' 's/package com.example.core/package com.example.core/g' {} \;
```

#### Шаг 2: Исправить импорты

```java
// Было:

// Стало:
import com.example.core.service.TradingService;
```

#### Шаг 3: Заменить прямые вызовы на события
```java
// Было (синхронный вызов):
notificationService.sendTelegram("Сделка выполнена");

// Стало (асинхронное событие):
NotificationEvent event = new NotificationEvent("Сделка выполнена", 
    "chat_id", NotificationType.TELEGRAM, Priority.HIGH);
eventPublisher.publish("notification-events-out-0", event);
```

#### Шаг 4: Выделить специализированные сервисы
```java
// Переместить в микросервис 'okx':
OkxClient okxClient = ...;
okxClient.getBalance();

// Переместить в микросервис 'notification':
TelegramBot bot = ...;
bot.sendMessage();

// Переместить в микросервис 'analytics':
StatisticsService stats = ...;
stats.calculateMetrics();
```

## ❓ FAQ

### Q: Как запустить только нужные мне сервисы?
```bash
# Запустить только Core + Notification
docker-compose up -d rabbitmq
cd core && mvn spring-boot:run &
cd notification && mvn spring-boot:run &
```

### Q: Как остановить конкретный сервис?
```bash
# Найти PID процесса
ps aux | grep "core.*spring-boot:run"

# Остановить по PID
kill <PID>

# Или остановить все Java процессы
pkill -f "spring-boot:run"
```

### Q: Сервис не запускается - что делать?
```bash
# 1. Проверить порт
lsof -i :8080

# 2. Посмотреть логи
tail -f logs/core.log

# 3. Проверить RabbitMQ
docker logs statarbitrage-rabbitmq

# 4. Пересобрать проект
mvn clean install -DskipTests
```

### Q: Как добавить новый тип события?
```java
// 1. Создать в shared/src/main/java/com/example/shared/events/
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MyCustomEvent extends BaseEvent {
    private String customData;
    
    public MyCustomEvent(String customData) {
        super("MY_CUSTOM_EVENT");
        this.customData = customData;
    }
}

// 2. Пересобрать shared модуль
cd shared && mvn clean install

// 3. Использовать в сервисах
MyCustomEvent event = new MyCustomEvent("test data");
eventPublisher.publish("custom-events-out-0", event);
```

### Q: Нужны ли Service Discovery и API Gateway?
**НЕТ!** Для разработки и начального использования они не нужны:

- **Service Discovery** (Eureka) - нужен только при развертывании в продакшене с динамическим масштабированием
- **API Gateway** - нужен только если хотите единую точку входа для внешних клиентов
- **Сейчас все работает через:**
  - Прямое обращение по портам (localhost:8080, localhost:8081...)
  - Асинхронные события через RabbitMQ
  - Это проще и достаточно для разработки!

### Q: Как изменить порт микросервиса?
```yaml
# В файле application.yml конкретного сервиса
server:
  port: 8099  # Новый порт
```

---

## 🎉 Готово к разработке!

Теперь у вас есть все инструменты для работы с микросервисной архитектурой:
- ✅ Запуск сервисов в IDE или командной строке
- ✅ Отправка и получение событий
- ✅ Мониторинг и отладка
- ✅ Поэтапная миграция кода

**Начинайте разработку прямо сейчас!** 🚀