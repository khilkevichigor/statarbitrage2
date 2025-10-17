# 🚀 НАЧАТЬ ОТСЮДА - Запуск микросервисов

## ✅ **ПРОБЛЕМА РЕШЕНА!** 

EventPublisher исправлен и больше не вызывает ошибок компиляции.

---

## 🎯 **КАК ЗАПУСТИТЬ В IDE:**

### 1. **Открыть проект в IntelliJ IDEA:**
```
File → Open → выбрать папку: 
/Users/igorkhilkevich/IdeaProjects/statarbitrage/microservices/
```

### 2. **Подождать пока IDEA загрузит проект** (30-60 сек)

### 3. **Запустить RabbitMQ:**
```bash
# В терминале IDEA или отдельном терминале
docker-compose up -d rabbitmq
```

### 4. **Запустить микросервис в IDEA:**
- Найти файл: `core/src/main/java/com/example/core/CoreApplication.java`
- **ПРАВЫЙ КЛИК на файле** → `Run 'CoreApplication'`
- ИЛИ нажать **зеленый треугольник** ▶️ рядом с `main` методом

### 5. **Проверить что работает:**
```bash
curl http://localhost:8080/actuator/health
# Должен вернуть: {"status":"UP"}
```

---

## 🎮 **КАКИЕ СЕРВИСЫ ЗАПУСКАТЬ:**

### 🔥 **Для начала запустите только:**
1. **Core** (8080) - основная логика
2. **Notification** (8081) - уведомления

### 📁 **Файлы для запуска в IDEA:**
```
core/src/main/java/com/example/core/CoreApplication.java
notification/src/main/java/com/example/notification/NotificationApplication.java
analytics/src/main/java/com/example/analytics/AnalyticsApplication.java
```

---

## 🛠️ **АЛЬТЕРНАТИВНЫЙ СПОСОБ (через командную строку):**

```bash
# 1. Запустить RabbitMQ
docker-compose up -d rabbitmq

# 2. Запустить Core
cd core
mvn spring-boot:run

# В другом терминале - запустить Notification
cd notification  
mvn spring-boot:run
```

---

## 🔍 **ЕСЛИ ЧТО-ТО НЕ РАБОТАЕТ:**

### 1. **Пересобрать проект:**
```bash
mvn clean install -DskipTests
```

### 2. **В IDEA:** 
`File` → `Reload Maven Projects`

### 3. **Проверить что RabbitMQ запущен:**
```bash
docker ps | grep rabbit
# Должен показать запущенный контейнер
```

### 4. **Посмотреть логи:**
```bash
tail -f logs/core.log
```

---

## 🎉 **РЕЗУЛЬТАТ:**

После успешного запуска у вас будут работать:

- ✅ **Core Service:** http://localhost:8080/actuator/health
- ✅ **Notification Service:** http://localhost:8081/actuator/health  
- ✅ **RabbitMQ Management:** http://localhost:15672 (admin/admin123)

---

## 🚀 **СЛЕДУЮЩИЕ ШАГИ:**

1. **Перенести ваш существующий код** в Core микросервис
2. **Добавить REST контроллеры** для вашей логики
3. **Использовать EventPublisher** для отправки событий между сервисами

**Система готова к разработке!** 🎊