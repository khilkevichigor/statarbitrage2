# Управление базой данных через Flyway

В проекте StatArbitrage используется [Flyway](https://flywaydb.org/) для управления миграциями базы данных.

## Настройка

### Maven зависимость
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

### Конфигурация в application.properties
```properties
# Hibernate configuration - disable auto DDL since we use Flyway
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect

# Flyway configuration
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.validate-on-migrate=true
spring.flyway.out-of-order=false
```

## Структура миграций

Миграции размещаются в папке: `src/main/resources/db/migration/`

### Соглашения по именованию
- **V{номер}__{описание}.sql** - для версионных миграций
  - Пример: `V1__Initial_schema.sql`, `V2__Add_new_table.sql`
- **R__{описание}.sql** - для повторяемых миграций (не используются в данном проекте)

### Текущие миграции

1. **V1__Initial_schema.sql** - Начальная схема базы данных
   - Создание всех основных таблиц: settings, chart_settings, trade_history, pair_data
   - Создание таблиц для ElementCollections: pair_data_long_candles, pair_data_short_candles
   - Создание индексов

2. **V2__Fix_bigint_types.sql** - Исправление типов ID полей
   - Замена INTEGER на BIGINT для всех id полей
   - Необходимо для совместимости с JPA @GeneratedValue(strategy = GenerationType.IDENTITY)

## Как добавить новую миграцию

### 1. Создайте новый файл миграции
```bash
touch src/main/resources/db/migration/V3__Add_new_feature.sql
```

### 2. Напишите SQL команды
```sql
-- V3__Add_new_feature.sql

-- Добавить новую колонку в существующую таблицу
ALTER TABLE settings ADD COLUMN new_feature_enabled BOOLEAN DEFAULT FALSE;

-- Создать новую таблицу
CREATE TABLE new_feature_data (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    value REAL,
    created_at INTEGER
);

-- Создать индекс
CREATE INDEX idx_new_feature_name ON new_feature_data(name);
```

### 3. Запустите приложение
При запуске Spring Boot автоматически применит все новые миграции.

## Полезные команды

### Проверить статус миграций
```bash
# Через SQLite CLI
sqlite3 ./data/sa-db.sqlite "SELECT * FROM flyway_schema_history;"
```

### Проверить структуру таблицы
```bash
sqlite3 ./data/sa-db.sqlite "PRAGMA table_info(table_name);"
```

### Восстановить базу данных
```bash
# Удалить базу данных (будет пересоздана при следующем запуске)
rm ./data/sa-db.sqlite

# Запустить приложение - все миграции применятся автоматически
mvn spring-boot:run
```

## Особенности работы с SQLite

### Типы данных
- `BIGINT` - для JPA @Id полей с @GeneratedValue
- `TEXT` - для строковых данных
- `REAL` - для чисел с плавающей точкой  
- `BOOLEAN` - для булевых значений
- `INTEGER` - для временных меток и целых чисел

### Ограничения
- SQLite не поддерживает ALTER COLUMN, поэтому для изменения типа колонки нужно:
  1. Создать новую таблицу с правильной структурой
  2. Скопировать данные из старой таблицы
  3. Удалить старую таблицу
  4. Переименовать новую таблицу

## Лучшие практики

1. **Никогда не изменяйте уже примененные миграции** - создавайте новые
2. **Делайте резервные копии** базы данных перед важными изменениями
3. **Тестируйте миграции** на копии базы данных
4. **Используйте осмысленные имена** для файлов миграций
5. **Добавляйте комментарии** в SQL файлы для объяснения сложных операций

## Отладка проблем

### Проверка последней примененной миграции
```sql
SELECT * FROM flyway_schema_history ORDER BY version_rank DESC LIMIT 1;
```

### Проверка ошибок валидации Hibernate
Если Hibernate сообщает о несоответствии схемы, проверьте:
1. Соответствуют ли типы данных в миграции JPA аннотациям
2. Все ли таблицы и колонки созданы
3. Правильно ли настроены внешние ключи

### Принудительная проверка схемы
```bash
# Запустить приложение с отладкой Hibernate
mvn spring-boot:run -Dspring.jpa.show-sql=true -Dspring.jpa.hibernate.ddl-auto=validate
```