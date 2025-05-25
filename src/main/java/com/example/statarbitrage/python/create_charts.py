# create_charts.py

import gc
import json
import matplotlib.pyplot as plt
import numpy as np
import os
import sys
import traceback


def plot_chart(prices_a, prices_b, window, direction, a, b, output_dir="charts"):
    if len(prices_a) < window or len(prices_b) < window:
        return

    os.makedirs(output_dir, exist_ok=True)

    spread = np.array(prices_a) - np.array(prices_b)
    mean = np.convolve(spread, np.ones(window) / window, mode='valid')
    std = np.std(spread[-window:])

    upper1 = mean + std
    lower1 = mean - std
    upper2 = mean + 2 * std
    lower2 = mean - 2 * std

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 8), sharex=True)
    fig.suptitle(f"Цены монет и спред: {a} / {b} ({direction})")

    color_a = "green" if direction.startswith("LONG") else "red"
    color_b = "red" if direction.startswith("LONG") else "green"

    ax1.plot(prices_a, label=a, color=color_a)
    ax1.plot(prices_b, label=b, color=color_b)
    ax1.set_ylabel("Цена")
    ax1.legend()
    ax1.grid(True)

    ax2.plot(spread, label="Spread", color="black")
    ax2.plot(range(window - 1, len(spread)), mean, label="Mean", color="blue")
    ax2.plot(range(window - 1, len(spread)), upper1, "--", label="+1σ", color="green")
    ax2.plot(range(window - 1, len(spread)), lower1, "--", label="-1σ", color="green")
    ax2.plot(range(window - 1, len(spread)), upper2, ":", label="+2σ", color="red")
    ax2.plot(range(window - 1, len(spread)), lower2, ":", label="-2σ", color="red")

    ax2.set_xlabel("Время")
    ax2.set_ylabel("Spread")
    ax2.legend()
    ax2.grid(True)

    plt.tight_layout()

    # Сохраняем график
    filename = f"{a}_{b}.png".replace("/", "-")
    filepath = os.path.join(output_dir, filename)
    plt.savefig(filepath)
    plt.clf()
    plt.close('all')

    print(f"✅ График сохранён: {filepath}")


def load_settings(settings_path, account_id="159178617"):
    if not os.path.exists(settings_path):
        print(f"❌ Файл настроек не найден: {settings_path}")
        return None

    with open(settings_path, "r") as f:
        all_settings = json.load(f)

    return all_settings.get(account_id)


def main():
    settings_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/settings.json"
    zscore_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/z_score.json"
    closes_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/all_closes.json"
    output_dir = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/charts"

    settings = load_settings(settings_path)
    if not settings:
        print("❌ Не удалось загрузить настройки")
        return

    window = settings.get("windowSize", 20)  # по умолчанию 20, если не указано

    if not os.path.exists(zscore_path) or not os.path.exists(closes_path):
        print("❌ z_score.json или closes.json не найден")
        return

    with open(zscore_path, "r") as f:
        z_scores = json.load(f)

    with open(closes_path, "r") as f:
        closes = json.load(f)

    for entry in z_scores:
        a = entry["a"]
        b = entry["b"]
        direction = entry.get("direction", "LONG")

        prices_a = closes.get(a)
        prices_b = closes.get(b)

        if not prices_a or not prices_b:
            print(f"⚠️ Пропущена пара {a}/{b} — нет данных о ценах")
            continue

        plot_chart(prices_a, prices_b, window, direction, a, b, output_dir)
        gc.collect()

if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
