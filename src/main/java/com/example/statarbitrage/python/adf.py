# adf.py

import itertools
import json
import numpy as np
import sys
import traceback
from statsmodels.tsa.stattools import adfuller


def analyze_pairs_adf(pairs, candles_dict, window=20, pvalue_threshold=0.01, stat_threshold=-2.5):
    results = []
    for a, b in pairs:
        s1 = candles_dict.get(a, [])
        s2 = candles_dict.get(b, [])
        if len(s1) != len(s2) or len(s1) < window:
            continue

        spread = np.array(s1[-window:]) - np.array(s2[-window:])
        try:
            stat, pvalue, *_ = adfuller(spread)
            if pvalue < pvalue_threshold and stat < stat_threshold:
                results.append({
                    "a": a,
                    "b": b,
                    "adf_pvalue": pvalue,
                    "adf_stat": stat
                })
        except Exception:
            continue

    return results


def main():
    with open("all_closes.json") as f:
        candles_dict = json.load(f)

    pairs = list(itertools.combinations(candles_dict.keys(), 2))
    results = analyze_pairs_adf(pairs, candles_dict)
    with open("adf.json", "w") as f:
        json.dump(results, f, indent=2)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
