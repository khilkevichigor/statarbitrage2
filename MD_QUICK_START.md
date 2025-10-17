# ⚡ Быстрый старт для разработчика

## 🎯 Главное: КАК ЗАПУСТИТЬ?

### 1️⃣ Способ для разработки в IDE (рекомендуется)

#### IntelliJ IDEA / VS Code:
```bash
# 1. Открыть проект в IDE
# File → Open → выбрать папку microservices/

# 2. Запустить RabbitMQ
cd microservices
docker-compose up -d rabbitmq

# 3. Запустить нужные микросервисы правым кликом:
# Найти файл CoreApplication.java
# ПРАВЫЙ КЛИК → Run 'CoreApplication'
```

**Готово!** Ваш сервис запущен на http://localhost:8080

### 2️⃣ Способ через командную строку
```bash
# Запустить все сразу
cd microservices
./start-all.sh

# Или запустить отдельный сервис
cd core
mvn spring-boot:run
```

## 🎮 Какие порты использовать?

| Сервис | Порт | Назначение |
|---------|------|------------|
| **core** | 8080 | Основная логика |
| **notification** | 8081 | Telegram/Email |
| **analytics** | 8082 | Аналитика |
| **csv** | 8083 | Экспорт данных |
| **ui** | 8084 | Интерфейс |
| **okx** | 8088 | OKX API |
| **python** | 8090 | Python скрипты |

**RabbitMQ:** http://localhost:15672 (admin/admin123)

## 🔥 Самые важные файлы

### Для запуска в IDE:
```
📁 core/src/main/java/com/example/core/
   └── 📄 CoreApplication.java  ← ЗАПУСКАТЬ ЭТОТ ФАЙЛ

📁 notification/src/main/java/com/example/notification/
   └── 📄 NotificationApplication.java  ← ИЛИ ЭТОТ

📁 analytics/src/main/java/com/example/analytics/
   └── 📄 AnalyticsApplication.java  ← ИЛИ ЭТОТ
```

### Для добавления кода:
```
📁 core/src/main/java/com/example/core/
   ├── 📄 service/     ← Ваши сервисы
   ├── 📄 controller/  ← REST контроллеры  
   └── 📄 model/       ← Модели данных
```

## 🚀 Первые шаги после запуска

### 1. Проверить что работает:
```bash
curl http://localhost:8080/actuator/health
```
Должен вернуть: `{"status":"UP"}`

### 2. Перенести ваш код:
```bash
# Скопировать из основного проекта
cp -r ../src/main/java/com/example/statarbitrage/* core/src/main/java/com/example/core/
```

### 3. Отправить тестовое событие:
```java
@RestController
public class TestController {
    
    @Autowired
    private EventPublisher eventPublisher;
    
    @GetMapping("/test")
    public String test() {
        NotificationEvent event = new NotificationEvent(
            "🎉 Система работает!", 
            "test_user", 
            NotificationType.TELEGRAM, 
            Priority.HIGH
        );
        eventPublisher.publish("notification-events-out-0", event);
        return "Событие отправлено!";
    }
}
```

## 💡 Полезные команды

```bash
# Посмотреть логи
tail -f logs/core.log

# Остановить все
./stop-all.sh

# Пересобрать проект
mvn clean install -DskipTests

# Проверить какие порты заняты
lsof -i :8080,8081,8082
```

## ❌ Что НЕ НУЖНО делать

- ❌ **НЕ настраивать** Service Discovery (Eureka)
- ❌ **НЕ настраивать** API Gateway  
- ❌ **НЕ думать** о Docker (пока)
- ❌ **НЕ беспокоиться** о продакшене

## ✅ Что НУЖНО делать

- ✅ **Запустить** RabbitMQ
- ✅ **Запустить** нужные микросервисы в IDE
- ✅ **Скопировать** свой код в Core
- ✅ **Использовать** события для связи между сервисами

---

## 🎯 TL;DR (Совсем коротко)

```bash
# 1. Открыть microservices/ в IntelliJ IDEA
# 2. Запустить RabbitMQ
docker-compose up -d rabbitmq

# 3. Правый клик на CoreApplication.java → Run
# 4. Готово! Система работает на localhost:8080
```

**Всё! Можно разрабатывать!** 🚀