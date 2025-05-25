# analyze.py
import json

# Пример результатов анализа
result = {
    "closed": True,
    "message": "✅ Сигнал можно закрывать.",
    "chart": "chart.png"
}

# Сохраняем график (для примера — просто пустой файл)
with open("chart.png", "wb") as f:
    f.write(b'\x89PNG\r\n\x1a\n')  # заголовок PNG

# Печатаем результат в stdout
print(json.dumps(result))
