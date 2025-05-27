# create_charts.py

import gc
import json
import matplotlib.pyplot as plt
import numpy as np
import os


def load_settings(path):
    try:
        with open(path, "r") as f:
            return json.load(f)
    except Exception as e:
        print(f"Ошибка загрузки настроек: {e}")
        return None


def plot_chart(
        prices_long, prices_short, window,
        longticker, shortticker, output_dir="charts",
        spread_val=None, mean_val=None, zscore=None, pvalue=None,
        long_price=None, short_price=None
):
    if len(prices_long) < window or len(prices_short) < window:
        print(f"⚠️ Недостаточно данных для {shortticker}/{longticker}")
        return

    os.makedirs(output_dir, exist_ok=True)

    spread = np.array(prices_short) - np.array(prices_long)
    mean = np.convolve(spread, np.ones(window) / window, mode='valid')
    std = np.std(spread[-window:])

    upper1 = mean + std
    lower1 = mean - std
    upper2 = mean + 2 * std
    lower2 = mean - 2 * std

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 8), sharex=True)
    fig.suptitle(f"Цены монет и спред: SHORT/{shortticker} LONG/{longticker}")

    ax1.plot(prices_short, label=f"{shortticker}", color="red")
    ax1.plot(prices_long, label=f"{longticker}", color="green")
    ax1.set_ylabel("Цена")
    ax1.legend()
    ax1.grid(True)

    # ⬅️ Добавим текущие цены long и short
    price_text = (
        f"{longticker} = {long_price:.6f}\n"
        f"{shortticker} = {short_price:.6f}"
    )
    ax1.text(
        0.01, 0.95, price_text,
        transform=ax1.transAxes,
        fontsize=10,
        verticalalignment='top',
        bbox=dict(boxstyle='round', facecolor='white', alpha=0.7)
    )

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

    info_text = (
        f"mean={mean_val:.4f}, spread={spread_val:.4f}, "
        f"zscore={zscore:.4f}, pvalue={pvalue:.4f}"
    )
    ax2.text(
        0.01, 0.95, info_text,
        transform=ax2.transAxes,
        fontsize=10,
        verticalalignment='top',
        bbox=dict(boxstyle='round', facecolor='white', alpha=0.7)
    )

    plt.tight_layout()

    filename = f"{shortticker}_{longticker}.png".replace("/", "-")
    filepath = os.path.join(output_dir, filename)
    plt.savefig(filepath)
    plt.clf()
    plt.close('all')

    print(f"✅ График сохранён: {filepath}")


def main():
    settings_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/settings.json"
    zscore_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/z_score.json"
    closes_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/all_closes.json"
    output_dir = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/charts"

    settings = load_settings(settings_path)
    if not settings:
        print("❌ Не удалось загрузить настройки")
        return

    window = settings.get("windowSize", 20)

    if not os.path.exists(zscore_path) or not os.path.exists(closes_path):
        print("❌ z_score.json или closes.json не найден")
        return

    with open(zscore_path, "r") as f:
        z_scores = json.load(f)

    with open(closes_path, "r") as f:
        closes = json.load(f)

    for entry in z_scores:
        longticker = entry.get("longticker")
        shortticker = entry.get("shortticker")

        if not longticker or not shortticker:
            print("⚠️ Пропущена пара — нет longticker или shortticker")
            continue

        prices_long = closes.get(longticker)
        prices_short = closes.get(shortticker)

        if not prices_long or not prices_short:
            print(f"⚠️ Пропущена пара {shortticker}/{longticker} — нет данных о ценах")
            continue

        plot_chart(
            prices_long, prices_short, window,
            longticker, shortticker, output_dir,
            spread_val=entry.get("spread"),
            mean_val=entry.get("mean"),
            zscore=entry.get("zscore"),
            pvalue=entry.get("pvalue"),
            long_price=entry.get("longtickercurrentprice"),
            short_price=entry.get("shorttickercurrentprice")
        )
        gc.collect()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
