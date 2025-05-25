# z_score.py

import itertools
import json
import numpy as np
import sys
import traceback
from statsmodels.tsa.stattools import coint


def is_cointegrated(s1, s2, significance):
    score, pvalue, _ = coint(s1, s2)
    return pvalue < significance, pvalue


def analyze_pairs(pairs, candles_dict, chat_config):
    results = []
    window = chat_config["windowSize"]
    zscore_entry = chat_config["zscoreEntry"]
    significance = chat_config["significanceLevel"]

    total_pairs = len(pairs)
    print(f"🔍 Анализируем {total_pairs} пар...")

    for idx, (a, b) in enumerate(pairs, 1):
        # if idx % 100 == 0 or idx == total_pairs:
        # print(f"  [{idx}/{total_pairs}] {a}/{b}")

        s1 = candles_dict.get(a, [])
        s2 = candles_dict.get(b, [])
        if len(s1) != len(s2) or len(s1) <= window:
            continue

        is_coint, pvalue = is_cointegrated(s1, s2, significance)
        if not is_coint:
            continue

        i = len(s1) - 1
        spread = s1[i] - s2[i]
        window_spread = [s1[j] - s2[j] for j in range(i - window, i)]
        mean = np.mean(window_spread)
        std = np.std(window_spread)
        z = (spread - mean) / std if std > 0 else 0

        if abs(z) < zscore_entry:
            continue

        results.append({
            "a": a,
            "b": b,
            "zscore": z,
            "pvalue": pvalue,
            "direction": f"SHORT/{a} LONG/{b}" if z > 0 else f"LONG/{a} SHORT/{b}"
        })

    print(f"✅ Найдено {len(results)} подходящих пар из {total_pairs}")
    return results


def main():
    with open("all_closes.json") as f:
        candles_dict = json.load(f)

    with open("settings.json") as f:
        config = json.load(f)

    chat_id = "159178617"
    chat_config = config[chat_id]

    # Сгенерировать все возможные комбинации без ограничения
    pairs = list(itertools.combinations(candles_dict.keys(), 2))

    results = analyze_pairs(pairs, candles_dict, chat_config)

    # Сохранить результат
    with open("z_score.json", "w") as f:
        json.dump(results, f, indent=2)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
