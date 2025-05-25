# rolling_correlation.py

import itertools
import json
import numpy as np
import sys
import traceback


def analyze_pairs_rolling_corr(pairs, candles_dict, window=20, min_corr=0.95, std_threshold=0.01):
    results = []
    for a, b in pairs:
        s1 = candles_dict.get(a, [])
        s2 = candles_dict.get(b, [])
        if len(s1) != len(s2) or len(s1) < window:
            continue

        s1_window = s1[-window:]
        s2_window = s2[-window:]
        corr = np.corrcoef(s1_window, s2_window)[0, 1]
        spread = np.array(s1_window) - np.array(s2_window)
        spread_std = np.std(spread)

        if abs(corr) >= min_corr and spread_std < std_threshold:
            results.append({
                "a": a,
                "b": b,
                "correlation": corr,
                "spread_std": spread_std
            })
    return results


def main():
    with open("all_closes.json") as f:
        candles_dict = json.load(f)

    pairs = list(itertools.combinations(candles_dict.keys(), 2))
    results = analyze_pairs_rolling_corr(pairs, candles_dict)
    with open("rolling_correlation.json", "w") as f:
        json.dump(results, f, indent=2)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
