# ⚠️ РЕШЕНИЕ ПРОБЛЕМ ЗАПУСКА

## ❌ Ошибка: "package org.springframework.cloud.stream.function does not exist"

### ✅ **РЕШЕНИЕ (сделано):**
EventPublisher упрощен и больше не использует сложные зависимости Spring Cloud Stream.

### 🔄 **Что нужно сделать:**

#### 1. Пересобрать проект:
```bash
# В папке microservices/
mvn clean install -DskipTests
```

#### 2. Обновить проект в IDE:
- **IntelliJ IDEA:** `File` → `Reload Maven Projects`
- **VS Code:** `Ctrl+Shift+P` → `Java: Reload Projects`

#### 3. Запустить микросервис:
- Найти `CoreApplication.java` в `core/src/main/java/com/example/core/`
- **Правый клик** → `Run 'CoreApplication'`

### 🚀 **Если всё ещё не работает:**

#### Вариант 1: Запуск через командную строку
```bash
# В папке microservices/
docker-compose up -d rabbitmq
cd core
mvn spring-boot:run
```

#### Вариант 2: Проверить что RabbitMQ запущен
```bash
# Запустить RabbitMQ отдельно
docker-compose up -d rabbitmq

# Проверить что работает
curl http://localhost:15672
```

## 🎯 **Простая проверка что всё работает:**

### 1. Компиляция:
```bash
mvn clean compile -DskipTests
# Должно пройти без ошибок
```

### 2. Запуск Core:
```bash
cd core
mvn spring-boot:run
# Должен запуститься на http://localhost:8080
```

### 3. Проверка здоровья:
```bash
curl http://localhost:8080/actuator/health
# Должен вернуть: {"status":"UP"}
```

## 🔧 **Если ничего не помогает - минимальный запуск:**

### Запустить только Core без событий:
1. Открыть `CoreApplication.java`
2. Временно закомментировать все импорты связанные с EventPublisher
3. Запустить через IDE
4. Проверить что базовый Spring Boot работает

### Пример минимального CoreApplication.java:
```java
@SpringBootApplication
public class CoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
        System.out.println("✅ Core запущен!");
    }
}
```

## 💡 **Контакты для помощи:**
- Проверьте логи в папке `logs/`
- Откройте Issue в проекте с описанием ошибки
- Приложите полный текст ошибки

---

**В 99% случаев помогает простая пересборка: `mvn clean install -DskipTests`** 🎯