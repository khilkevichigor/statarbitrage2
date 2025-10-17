# Инструкция по тестированию OKX позиций

## Настройка TestOkxPositions.java

1. **Откройте файл**: `/Users/igorkhilkevich/IdeaProjects/statarbitrage/src/main/java/com/example/statarbitrage/TestOkxPositions.java`

2. **Замените хардкод на строках 25-28**:
```java
private static final String API_KEY = "ваш-реальный-api-key";
private static final String API_SECRET = "ваш-реальный-secret";  
private static final String PASSPHRASE = "ваш-реальный-passphrase";
private static final boolean IS_SANDBOX = false; // false для реальной торговли
```

## Запуск теста

```bash
# В корне проекта
mvn compile exec:java -Dexec.mainClass="com.example.statarbitrage.TestOkxPositions"
```

## Что тестируется

1. **Получение активных позиций** - все открытые позиции на OKX
2. **Получение истории позиций** - последние 10 закрытых позиций  
3. **Поиск конкретной позиции** - ищет активную позицию в истории

## Ожидаемый вывод

```
🔄 Тест получения позиций с OKX...

📊 Получение активных позиций:
OKX Response: {"code":"0","data":[...]}
Найдено 4 активных позиций:
  OP-USDT-SWAP LONG size=12.8 PnL=0.465
  ORDI-USDT-SWAP SHORT size=29 PnL=0.6757
  ...

📚 Получение истории позиций:
OKX History Response: {"code":"0","data":[...]}
Найдено 8 позиций в истории:
  BTC-USDT-SWAP LONG status=OPEN PnL=150.25
  ...

🔍 Поиск позиции OP-USDT-SWAP (ID: 2713585759382085632) в истории:
  ❌ Не найдена в истории
```

## Диагностика проблем

### Если позиции не найдены в истории:
- Возможно, позиции ещё не попали в историю OKX (нужно время)
- Проверьте правильность API ключей
- Убедитесь что используете правильное окружение (sandbox/prod)

### При ошибках авторизации:
- Проверьте API_KEY, API_SECRET, PASSPHRASE
- Убедитесь что IP-адрес разрешён в настройках OKX API

### При пустом ответе:
- На аккаунте нет открытых позиций
- Неправильные права API ключа (нужен доступ к торговле)