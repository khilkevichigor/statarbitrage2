# 🚨 ВАЖНО: Правильный запуск микросервисов

## ❌ **ПРОБЛЕМА:**
Вы запускаете сервисы из **неправильного проекта**!

### 🔍 **Что происходит:**
- **OKX запускается из:** `/Users/igorkhilkevich/IdeaProjects/statarbitrage/target/classes` 
- **Это основной проект**, а НЕ микросервисы!
- Поэтому он игнорирует настройки портов микросервисов

---

## ✅ **ПРАВИЛЬНОЕ РЕШЕНИЕ:**

### 1. **Закрыть все запущенные сервисы**
```bash
# Остановить все Java процессы
pkill -f java
```

### 2. **Открыть ПРАВИЛЬНЫЙ проект в IntelliJ IDEA:**
```
❌ НЕ ОТКРЫВАТЬ: /Users/igorkhilkevich/IdeaProjects/statarbitrage/
✅ ОТКРЫВАТЬ:     /Users/igorkhilkevich/IdeaProjects/statarbitrage/microservices/
```

### 3. **В IntelliJ IDEA:**
- `File` → `Close Project` (закрыть текущий)
- `File` → `Open` 
- **Выбрать папку:** `/Users/igorkhilkevich/IdeaProjects/statarbitrage/microservices/`
- Подождать пока загрузится Maven структура

### 4. **Проверить что открыт правильный проект:**
В IntelliJ должны быть видны модули:
```
📁 microservices (root)
├── 📁 shared
├── 📁 core  
├── 📁 notification
├── 📁 okx
├── 📁 analytics
└── ...
```

### 5. **Запустить микросервисы из правильного места:**
- Найти: `okx/src/main/java/com/example/okx/OkxApplication.java`
- **Правый клик** → `Run 'OkxApplication'`
- Должен запуститься на порту **8088**!

---

## 🎯 **КАК ПРОВЕРИТЬ ЧТО ВСЁ ПРАВИЛЬНО:**

### Правильные логи должны показывать:
```
INFO [OKX] com.example.okx.OkxApplication - Starting OkxApplication
Tomcat initialized with port 8088 (http)  ← ПРАВИЛЬНЫЙ ПОРТ
```

### Правильные пути в логах:
```
(/Users/igorkhilkevich/IdeaProjects/statarbitrage/microservices/okx/target/classes
```

---

## 📋 **СПИСОК ПОРТОВ (для проверки):**

| Сервис | Порт | Application класс |
|---------|------|-------------------|
| core | 8080 | CoreApplication.java |
| notification | 8081 | NotificationApplication.java |
| analytics | 8082 | AnalyticsApplication.java |
| csv | 8083 | CsvApplication.java |
| ui | 8084 | UiApplication.java |
| database | 8085 | DatabaseApplication.java |
| cointegration | 8086 | CointegrationApplication.java |
| trading | 8087 | TradingApplication.java |
| **okx** | **8088** | **OkxApplication.java** |
| 3commas | 8089 | CommasApplication.java |
| python | 8090 | PythonApplication.java |

---

## 🔧 **ЕСЛИ ПРОБЛЕМЫ С ПОРТАМИ:**

### Найти что занимает порт:
```bash
lsof -i :8081
lsof -i :8088
```

### Убить процессы:
```bash
kill -9 <PID>
```

### Или убить все Java:
```bash
pkill -f java
```

---

## 🚀 **ИТОГОВАЯ ПРОВЕРКА:**

После правильного запуска:
```bash
curl http://localhost:8081/actuator/health  # Notification
curl http://localhost:8088/actuator/health  # OKX
```

**Должны вернуть:** `{"status":"UP"}`

---

## 💡 **ГЛАВНОЕ:**
**Всегда запускайте из проекта `microservices/`, а НЕ из основного проекта `statarbitrage/`!**