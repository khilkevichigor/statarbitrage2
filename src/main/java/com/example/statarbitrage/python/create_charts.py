# create_charts.py

import gc
import json
import matplotlib.pyplot as plt
import numpy as np
import os


def load_entry_data(entry_path):
    try:
        with open(entry_path, "r") as f:
            data = json.load(f)
            return data[0] if data else None
    except Exception as e:
        print(f"Ошибка загрузки entry_data.json: {e}")
        return None


def load_settings(path):
    try:
        with open(path, "r") as f:
            return json.load(f)
    except Exception as e:
        print(f"Ошибка загрузки настроек: {e}")
        return None


def normalize(series):
    arr = np.array(series)
    return (arr - arr.mean()) / arr.std()


def find_entry_index(prices_long, prices_short, entry_price_long, entry_price_short, spread, mean, window):
    norm_long = normalize(prices_long)
    norm_short = normalize(prices_short)

    # Нормализуем входные цены для сравнения с нормализованными ценами
    entry_price_long_norm = (entry_price_long - np.mean(prices_long)) / np.std(prices_long)
    entry_price_short_norm = (entry_price_short - np.mean(prices_short)) / np.std(prices_short)
    entry_spread = entry_price_short - entry_price_long

    # Нормализуем среднюю, чтобы сравнивать одинаково
    mean_full = np.concatenate([np.full(window - 1, np.nan), mean])  # чтобы выровнять индексы
    mean_norm = normalize(mean_full[~np.isnan(mean_full)])

    min_score = float("inf")
    idx_entry = None

    for i in range(len(prices_long)):
        # Проверяем, есть ли в mean значение для i
        if i < window - 1:
            continue

        # Оценим расстояние по трем критериям

        diff_prices = abs(norm_long[i] - entry_price_long_norm) + abs(norm_short[i] - entry_price_short_norm)
        diff_spread = abs(spread[i] - entry_spread)
        diff_mean = abs(mean[i - (window - 1)] - entry_spread)  # сравниваем mean с entry_spread

        # Взвешиваем критерии, можно экспериментировать с весами
        score = diff_prices * 0.5 + diff_spread * 0.3 + diff_mean * 0.2

        if score < min_score:
            min_score = score
            idx_entry = i

    return idx_entry


def plot_chart(
        prices_long, prices_short, window,
        longticker, shortticker, output_dir="charts",
        spread_val=None, mean_val=None, zscore=None, pvalue=None,
        long_price=None, short_price=None,
        entry_data=None):  # 👈 добавлено

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

    # Нормализация цен
    norm_long = normalize(prices_long)
    norm_short = normalize(prices_short)

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 8), sharex=True)
    fig.suptitle(f"Нормированные цены и спред: SHORT/{shortticker} LONG/{longticker}")

    # Верхний график: нормализованные цены
    ax1.plot(norm_short, label=f"{shortticker} (norm)", color="red")
    ax1.plot(norm_long, label=f"{longticker} (norm)", color="green")
    ax1.set_ylabel("Норм. цена")
    ax1.legend()
    ax1.grid(True)

    # Добавим текущие цены (в обычном виде)
    price_text = (
        f"{longticker} = {long_price:.6f}, "
        f"{shortticker} = {short_price:.6f}"
    )
    ax1.text(
        0.01, 0.95, price_text,
        transform=ax1.transAxes,
        fontsize=10,
        verticalalignment='top',
        bbox=dict(boxstyle='round', facecolor='white', alpha=0.7)
    )

    # Нижний график: spread и границы
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

    # === ENTRY и PROFIT линии ===
    if entry_data:
        entry_long = entry_data.get("longticker")
    entry_short = entry_data.get("shortticker")

    if longticker == entry_long and shortticker == entry_short:
        entry_price_long = entry_data.get("longTickerEntryPrice")
        entry_price_short = entry_data.get("shortTickerEntryPrice")

        try:
            idx_entry = find_entry_index(
                prices_long, prices_short,
                entry_price_long, entry_price_short,
                spread, mean, window
            )

            for ax in [ax1, ax2]:
                ax.axvline(idx_entry, color="purple", linestyle="--", label="ENTRY")

            ax1.scatter(idx_entry, normalize(prices_long)[idx_entry], color="purple", zorder=5)
            ax1.scatter(idx_entry, normalize(prices_short)[idx_entry], color="purple", zorder=5)
            ax2.scatter(idx_entry, spread[idx_entry], color="purple", zorder=5)

            profit = entry_data.get("profit")
            if profit:
                ax1.text(
                    idx_entry + 2, ax1.get_ylim()[1] * 0.95,
                    f"Profit: {profit}",
                    color="purple", fontsize=9,
                    bbox=dict(boxstyle='round', facecolor='lavender', alpha=0.6)
                )
        except Exception as e:
            print(f"⚠️ Не удалось отобразить ENTRY линию: {e}")


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
    entry_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/entry_data.json"

    entry_data = load_entry_data(entry_path)

    settings = load_settings(settings_path)
    if not settings:
        print("❌ Не удалось загрузить entry_data.json")
        return

    window = settings.get("windowSize", 20)

    if not os.path.exists(zscore_path) or not os.path.exists(closes_path):
        print("❌ z_score.json или all_closes.json не найден")
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
            short_price=entry.get("shorttickercurrentprice"),
            entry_data=entry_data  # 👈 добавь это
        )
        gc.collect()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
