# create_chart.py

import gc
import json
import matplotlib.pyplot as plt
import numpy as np
import os
import sys
import traceback


def extract_closes_and_timestamps(candles):
    closes = []
    timestamps = []
    for i, c in enumerate(candles):
        try:
            close = float(c["close"])
            if "timestamp" in c:
                timestamp = int(c["timestamp"])
            else:
                raise KeyError("нет поля 'timestamp'")
            closes.append(close)
            timestamps.append(timestamp)
        except Exception as e:
            print(f"[{i}] ERROR: {e} | data={c}")
            continue
    return closes, timestamps


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
    return (arr - arr.mean()) / arr.std() if arr.std() != 0 else arr - arr.mean()


def plot_chart(
        candles_long, candles_short, window,
        longticker, shortticker, output_dir="charts",
        spread_val=None, mean_val=None, zscore=None, pvalue=None,
        long_price=None, short_price=None,
        entry_data=None):
    closes_long, timestamps_long = extract_closes_and_timestamps(candles_long)
    closes_short, timestamps_short = extract_closes_and_timestamps(candles_short)

    if timestamps_long != timestamps_short:
        print(f"⚠️ Несовпадение таймстемпов у {longticker}/{shortticker}")
        return

    print(f"{shortticker} candles: {len(closes_short)}, {longticker} candles: {len(closes_long)}, window: {window}")
    if len(closes_long) < window or len(closes_short) < window:
        print(f"⚠️ Недостаточно данных для {shortticker}/{longticker}")
        return

    os.makedirs(output_dir, exist_ok=True)

    spread = np.array(closes_long) - np.array(closes_short)
    mean = np.convolve(spread, np.ones(window) / window, mode='valid')
    std = np.std(spread[-window:])

    upper1 = mean + std
    lower1 = mean - std
    upper2 = mean + 2 * std
    lower2 = mean - 2 * std

    norm_long = normalize(closes_long)
    norm_short = normalize(closes_short)

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 8), sharex=True)
    fig.suptitle(f"Нормализованные цены и спред: SHORT/{shortticker} LONG/{longticker}")

    # Верхний график — нормализованные цены
    ax1.plot(norm_short, label=f"{shortticker} = {short_price:.6f}", color="red")
    ax1.plot(norm_long, label=f"{longticker} = {long_price:.6f}", color="green")
    ax1.set_yticks([])
    ax1.set_ylabel("")
    ax1.legend()
    ax1.grid(True)

    # Нижний график — спред
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

    # Отметка ENTRY
    if entry_data:
        entry_long = entry_data.get("longticker")
        entry_short = entry_data.get("shortticker")
        entry_ts = entry_data.get("entryTime")

        if longticker == entry_long and shortticker == entry_short and entry_ts:
            try:
                if entry_ts in timestamps_long:
                    idx_entry = timestamps_long.index(entry_ts)
                    for ax in [ax1, ax2]:
                        ax.axvline(idx_entry, color="purple", linestyle="--", label="ENTRY")

                    ax1.scatter(idx_entry, norm_long[idx_entry], color="purple", zorder=5)
                    ax1.scatter(idx_entry, norm_short[idx_entry], color="purple", zorder=5)
                    ax2.scatter(idx_entry, spread[idx_entry], color="purple", zorder=5)

                    profit = entry_data.get("profit")
                    if profit:
                        ax1.text(
                            idx_entry + 2, ax1.get_ylim()[1] * 0.95,
                            f"Profit: {profit}",
                            color="purple", fontsize=9,
                            bbox=dict(boxstyle='round', facecolor='lavender', alpha=0.6)
                        )
                else:
                    print("⚠️ Таймстемп точки входа не найден в свечах")
            except Exception as e:
                print(f"⚠️ Ошибка отображения ENTRY линии: {e}")

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
    candles_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/all_candles.json"
    output_dir = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/charts"
    entry_path = "/Users/igorkhilkevich/IdeaProjects/statarbitrage/entry_data.json"

    entry_data = load_entry_data(entry_path)
    settings = load_settings(settings_path)
    if not settings:
        print("❌ Не удалось загрузить settings.json")
        return

    window = settings.get("windowSize", 20)

    if not os.path.exists(zscore_path) or not os.path.exists(candles_path):
        print("❌ z_score.json или all_candles.json не найден")
        return

    with open(zscore_path, "r") as f:
        z_scores = json.load(f)

    with open(candles_path, "r") as f:
        candles = json.load(f)

    for entry in z_scores:
        longticker = entry.get("longticker")
        shortticker = entry.get("shortticker")

        if not longticker or not shortticker:
            print("⚠️ Пропущена пара — нет longticker или shortticker")
            continue

        candles_long = candles.get(longticker)
        candles_short = candles.get(shortticker)

        if not candles_long or not candles_short:
            print(f"⚠️ Пропущена пара {shortticker}/{longticker} — нет данных о свечах")
            continue

        print(f"📊 Обрабатываем пару: {shortticker}/{longticker}")
        plot_chart(
            candles_long, candles_short, window,
            longticker, shortticker, output_dir,
            spread_val=entry.get("spread"),
            mean_val=entry.get("mean"),
            zscore=entry.get("zscore"),
            pvalue=entry.get("pvalue"),
            long_price=entry.get("longtickercurrentprice"),
            short_price=entry.get("shorttickercurrentprice"),
            entry_data=entry_data
        )
        gc.collect()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
