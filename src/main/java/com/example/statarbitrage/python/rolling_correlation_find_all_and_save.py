# rolling_correlation.py
import itertools
import json
import numpy as np
import sys
import traceback


def analyze_pairs_rolling_corr(pairs, candles_dict, window=20, min_corr=0.9):
    results = []
    for a, b in pairs:
        s1 = candles_dict.get(a, [])
        s2 = candles_dict.get(b, [])
        if len(s1) != len(s2) or len(s1) < window:
            continue

        s1_window = s1[-window:]
        s2_window = s2[-window:]
        corr = np.corrcoef(s1_window, s2_window)[0, 1]

        if abs(corr) >= min_corr:
            results.append({
                "a": a,
                "b": b,
                "correlation": corr
            })
    return results


def main():
    with open("all_closes.json") as f:
        candles_dict = json.load(f)

    pairs = list(itertools.combinations(candles_dict.keys(), 2))
    results = analyze_pairs_rolling_corr(pairs, candles_dict)
    with open("results_method2.json", "w") as f:
        json.dump(results, f, indent=2)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
