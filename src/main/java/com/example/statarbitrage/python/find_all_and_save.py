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

    for a, b in pairs:
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
            "direction": "SHORT/{} LONG/{}".format(a, b) if z > 0 else "LONG/{} SHORT/{}".format(a, b)
        })

    return results


def main():
    with open("closes.json") as f:
        candles_dict = json.load(f)

    with open("settings.json") as f:
        config = json.load(f)

    chat_id = "159178617"
    chat_config = config[chat_id]
    max_pairs = chat_config["maxPairs"]
    pairs = list(itertools.combinations(candles_dict.keys(), 2))
    pairs_to_analyze = pairs[:max_pairs]

    results = analyze_pairs(pairs_to_analyze, candles_dict, chat_config)

    # Печать в консоль (если нужно)
    print(json.dumps(results, indent=2))

    # Сохранение в файл
    with open("find_all_and_save.json", "w") as f:
        json.dump(results, f, indent=2)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
