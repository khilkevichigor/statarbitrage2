# Statistical Arbitrage Trading System

> **Система статистического арбитража на основе микросервисной архитектуры Spring Cloud**

Полнофункциональная торговая система для поиска и использования арбитражных возможностей между коррелированными криптовалютными парами.

## 🌟 Особенности

- **📊 Статистический анализ** - Коинтеграционный анализ для поиска коррелированных пар
- **🤖 Автоматическая торговля** - Интеграция с OKX API для исполнения сделок
- **🏗️ Микросервисная архитектура** - Модульная система на основе Spring Cloud
- **📈 Real-time мониторинг** - Vaadin UI для отслеживания позиций и производительности
- **🔔 Уведомления** - Telegram бот для уведомлений о сделках
- **⚡ Event-driven** - Асинхронная коммуникация через RabbitMQ
- **🐘 PostgreSQL** - Надежное хранение данных с Flyway миграциями
- **🐳 Docker Compose** - Простое развертывание инфраструктуры

## 🏗️ Архитектура

### Основные микросервисы:
- **Core** (8181) - Торговая логика, UI, управление позициями
- **Cointegration** (8182) - Статистический анализ и поиск пар  
- **Candles** (8183) - Сбор и кэширование рыночных данных
- **Notification** (8184) - Telegram уведомления
- **Analytics** (8185) - Аналитика и отчетность

### Инфраструктура:
- **PostgreSQL** (5432) - Основная база данных
- **RabbitMQ** (5672, 15672) - Брокер сообщений
- **pgAdmin** (8080) - Управление базой данных

## 🚀 Быстрый запуск

### Предварительные требования
- Java 17+
- Maven 3.6+
- Docker и Docker Compose
- 4GB+ RAM

### 1. Клонирование репозитория
```bash
git clone https://github.com/khilkevichigor/statarbitrage2.git
cd statarbitrage2
```

### 2. Запуск инфраструктуры
```bash
docker-compose up -d
```
Это запустит:
- PostgreSQL (порт 5432)
- RabbitMQ (порты 5672, 15672)
- pgAdmin (порт 8080)

### 3. Сборка проекта
```bash
mvn clean install
```

### 4. Запуск микросервисов

#### Через IntelliJ IDEA (рекомендуемый способ):
1. **Настроить переменные окружения** в Run Configuration `2. Core Service`
2. **Запуск инфраструктуры**: Run Configuration `🐳 Start Infrastructure`
3. **Сборка и запуск всех сервисов**: Run Configuration `🏗️ BUILD AND RUN ALL`
4. **Остановка инфраструктуры**: Run Configuration `🛑 Stop Infrastructure`

> ⚠️ **Важно**: Настрой свои API ключи в Environment Variables для Core Service или скопируй `.env.example` в `.env`

#### Через командную строку:
```bash
# Core service (главный)
cd core && mvn spring-boot:run

# В отдельных терминалах:
cd cointegration && mvn spring-boot:run
cd candles && mvn spring-boot:run  
cd notification && mvn spring-boot:run
cd analytics && mvn spring-boot:run
```

### 5. Доступ к системе
- **Главное приложение**: http://localhost:8181
- **pgAdmin**: http://localhost:8080 (admin@statarbitrage.com / admin123)
- **RabbitMQ Management**: http://localhost:15672 (admin / admin123)

### 6. Остановка
```bash
docker-compose down
```

## 📨 Межсервисная коммуникация

### Event-Driven Architecture
Система использует RabbitMQ для асинхронного взаимодействия:

**Типы событий:**
- `CoreEvent` - события от основного сервиса
- `CointegrationEvent` - события анализа коинтеграции  
- `TradingEvent` - торговые события
- `UpdateUiEvent` - обновления UI

### Synchronous Communication
OpenFeign клиенты для синхронных вызовов:
- `CandlesFeignClient` - получение рыночных данных
- `OkxFeignClient` - интеграция с биржей

### Конфигурация событий
```yaml
spring:
  cloud:
    stream:
      bindings:
        core-events-out-0:
          destination: core.events
        cointegrationEventsConsumer-in-0:
          destination: cointegration.events
          group: core-group
```

## 💼 Основные компоненты

### Core Service (port 8181)
- **Trading Logic**: Процессоры торговых операций
- **Risk Management**: Стратегии выхода и управление рисками
- **Portfolio Management**: Управление портфелем
- **Vaadin UI**: Веб-интерфейс для мониторинга
- **Schedulers**: Автоматизированные торговые задачи

### Cointegration Service (port 8182)
- **Statistical Analysis**: Анализ коинтеграции пар
- **Pair Discovery**: Поиск коррелированных пар
- **Z-Score Calculations**: Расчет статистических показателей
- **Python Integration**: Интеграция с аналитическими скриптами

### Candles Service (port 8183) 
- **Market Data Collection**: Сбор данных с биржи OKX
- **Data Caching**: Кэширование свечных данных
- **Data Validation**: Проверка качества данных
- **Performance Optimization**: Оптимизация запросов к API

### Notification Service (port 8184)
- **Telegram Bot**: Уведомления в Telegram
- **Event Processing**: Обработка событий уведомлений
- **Message Formatting**: Форматирование сообщений

### Analytics Service (port 8185)
- **Performance Metrics**: Метрики производительности
- **Reporting**: Генерация отчетов
- **Statistical Analysis**: Дополнительная аналитика

## 🗂️ Структура проекта

```
statarbitrage2/
├── README.md
├── CLAUDE.md              # Документация для Claude AI
├── docker-compose.yml     # PostgreSQL, RabbitMQ, pgAdmin
├── application-global.yml # Глобальная конфигурация
├── shared/               # Общие компоненты
│   ├── dto/             # Data Transfer Objects
│   ├── events/          # Определения событий
│   ├── models/          # Доменные модели
│   ├── enums/           # Перечисления
│   └── utils/           # Утилиты
├── core/                # Основной торговый сервис
│   ├── src/main/java/com/example/core/
│   │   ├── processors/  # Торговые процессоры
│   │   ├── services/    # Бизнес-сервисы
│   │   ├── trading/     # Торговая система
│   │   ├── ui/          # Vaadin UI
│   │   └── schedulers/  # Планировщики задач
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/ # Flyway миграции
├── cointegration/       # Сервис анализа
├── candles/            # Сервис рыночных данных  
├── notification/       # Сервис уведомлений
├── analytics/          # Сервис аналитики
└── ...                 # Дополнительные сервисы
```

## 📋 Полезные команды

### Maven
```bash
# Сборка всех модулей
mvn clean install

# Запуск отдельного сервиса
cd core && mvn spring-boot:run

# Тесты
mvn test
```

### Мониторинг
```bash
# Логи отдельного сервиса
tail -f logs/core.log

# Проверка портов
netstat -an | grep LISTEN

# Процессы Java
ps aux | grep java
```

### RabbitMQ
```bash
# Статус очередей
docker exec -it statarbitrage-rabbitmq rabbitmqctl list_queues

# Подключения
docker exec -it statarbitrage-rabbitmq rabbitmqctl list_connections
```

## ⚙️ Конфигурация

### Основные настройки в application.yml:
```yaml
spring:
  cloud:
    stream:
      bindings:
        # Исходящие события
        trading-events-out-0:
          destination: trading.events
        # Входящие события  
        tradingEventsConsumer-in-0:
          destination: trading.events
          group: service-group
```

### Database Configuration
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/statarbitrage
    username: statuser
    password: statpass123
  jpa:
    hibernate:
      ddl-auto: update
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

### Environment Variables
```bash
export TELEGRAM_BOT_TOKEN=your_token
export OKX_API_KEY=your_key
export OKX_API_SECRET=your_secret
export OKX_API_PASSPHRASE=your_passphrase
export OKX_API_SANDBOX=true
```

## 🔧 Troubleshooting

### Проблемы с портами
```bash
# Найти процесс на порту
lsof -i :8080

# Убить процесс
kill -9 <PID>
```

### Проблемы с RabbitMQ
```bash
# Перезапуск RabbitMQ
docker-compose restart rabbitmq

# Логи RabbitMQ
docker logs statarbitrage-rabbitmq
```

### Проблемы с памятью
```bash
# Увеличить память для Maven
export MAVEN_OPTS="-Xmx2g"
```

## 🔐 Безопасность

### GeolocationService
Встроенная защита от торговли с US IP адресов:
Встроенная защита автоматически блокирует торговые операции при обнаружении US IP адреса для соответствия требованиям регулирования.

### API Key Management
- Все ключи API хранятся в переменных окружения
- Sandbox режим по умолчанию для тестирования
- Валидация подключений на старте приложения

## 🧪 Тестирование

```bash
# Запуск тестов
mvn test

# Тестирование отдельного сервиса
cd core && mvn test

# Проверка подключения к базе
curl http://localhost:8181/actuator/health
```

## 🚧 Roadmap

- [ ] **Service Discovery** (Eureka/Consul)
- [ ] **API Gateway** для маршрутизации  
- [ ] **Distributed Tracing** (Zipkin/Jaeger)
- [ ] **Configuration Server** централизованная конфигурация
- [ ] **Docker Images** для каждого сервиса
- [ ] **Kubernetes Manifests** для оркестрации
- [ ] **Monitoring Stack** (Prometheus/Grafana)
- [ ] **CI/CD Pipeline** автоматизация развертывания

## 📞 Поддержка

- **GitHub Issues**: [Создать issue](https://github.com/khilkevichigor/statarbitrage2/issues)
- **Документация**: [CLAUDE.md](CLAUDE.md) для разработчиков
- **Health Monitoring**: `/actuator/health` на каждом сервисе
- **Metrics**: `/actuator/metrics` для мониторинга
- **Logs**: Логи каждого сервиса в `core/logs/`

## 📈 Возможности системы

### 🎯 Торговые стратегии
- **Статистический арбитраж** на основе коинтеграции
- **Mean reversion** стратегии
- **Z-score** анализ для входа/выхода
- **Risk management** с автоматическими стоп-лоссами
- **Position sizing** адаптивное управление размером позиций

### 📊 Аналитика
- **Real-time мониторинг** производительности
- **Drawdown analysis** анализ просадок
- **Sharpe ratio** и другие метрики риска
- **P&L tracking** отслеживание прибыли/убытков
- **Export в CSV** для дальнейшего анализа

### 🔄 Автоматизация
- **Автоматический поиск пар** на основе коинтеграции
- **Scheduled tasks** для регулярного анализа рынка
- **Event-driven execution** быстрое реагирование на сигналы
- **Failover mechanisms** обработка ошибок и восстановление

---

## 🎯 Getting Started

1. **Клонировать репозиторий**
2. **Запустить инфраструктуру** `docker-compose up -d`
3. **Собрать проект** `mvn clean install`
4. **Запустить core service** `cd core && mvn spring-boot:run`
5. **Открыть UI** http://localhost:8181
6. **Настроить API ключи** в переменных окружения
7. **Начать торговлю!** 🚀

---

**🏆 Готовая production-ready система для статистического арбитража!**

> Полная документация доступна в [CLAUDE.md](CLAUDE.md)