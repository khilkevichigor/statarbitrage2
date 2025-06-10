import itertools
import json
import numpy as np
import os
import sys
import traceback
import uuid
from multiprocessing import Process, cpu_count
from statsmodels.api import OLS, add_constant
from statsmodels.tsa.stattools import adfuller
from statsmodels.tsa.stattools import coint


def is_cointegrated(s1, s2, significance):
    try:
        score, pvalue, _ = coint(s1, s2)
        return pvalue < significance, pvalue
    except Exception as e:
        print(f"❌ Ошибка в cointegration: {e}")
        return False, 1.0


def analyze_pair_for_best(a, b, candles_dict, settings):
    window = settings["windowSize"]
    significance = settings["significanceLevel"]
    min_corr = settings.get("minCorrelation")

    closes_a = np.array([c["close"] for c in candles_dict[a]])
    closes_b = np.array([c["close"] for c in candles_dict[b]])
    timestamps = [c["timestamp"] for c in candles_dict[a]]

    if len(closes_a) != len(closes_b):
        return None

    corr = np.corrcoef(closes_a, closes_b)[0, 1]
    if abs(corr) < min_corr:
        return None

    is_coint, pvalue = is_cointegrated(closes_a, closes_b, significance)
    if not is_coint:
        return None

    zscore_params = []

    first_zscore = None

    for i in range(window, len(closes_a)):
        a_window = closes_a[i - window:i]
        b_window = closes_b[i - window:i]

        X = add_constant(a_window)
        model = OLS(b_window, X).fit()
        alpha = model.params[0]
        beta = model.params[1]

        spread_series = b_window - (beta * a_window + alpha)
        adf_pvalue = adfuller(spread_series)[1]

        current_a = closes_a[i]
        current_b = closes_b[i]

        spread_value = current_b - (beta * current_a + alpha)
        mean = np.mean(spread_series)
        std = np.std(spread_series)
        z = (spread_value - mean) / std if std > 0 else 0

        first_zscore = z  # сетим все до последней свечи по которому и будем определять лонг или шорт

        zscore_params.append({
            "zscore": z,
            "pvalue": pvalue,
            "adfpvalue": adf_pvalue,
            "correlation": corr,
            "alpha": alpha,
            "beta": beta,
            "spread": spread_value,
            "mean": mean,
            "std": std,
            "timestamp": timestamps[i]
        })

    long_ticker = a if first_zscore > 0 else b
    short_ticker = b if first_zscore > 0 else a

    return {
        "longTicker": long_ticker,
        "shortTicker": short_ticker,
        "zscoreParams": zscore_params
    }


def analyze_pair_for_trade(long_ticker, short_ticker, candles_dict, settings):
    window = settings["windowSize"]
    significance = settings["significanceLevel"]

    closes_long = np.array([c["close"] for c in candles_dict[long_ticker]])
    closes_short = np.array([c["close"] for c in candles_dict[short_ticker]])
    timestamps = [c["timestamp"] for c in candles_dict[long_ticker]]

    corr = np.corrcoef(closes_long, closes_short)[0, 1]

    is_coint, pvalue = is_cointegrated(closes_long, closes_short, significance)

    zscore_params = []

    for i in range(window, len(closes_long)):
        a_window = closes_long[i - window:i]
        b_window = closes_short[i - window:i]

        X = add_constant(a_window)
        model = OLS(b_window, X).fit()
        alpha = model.params[0]
        beta = model.params[1]

        spread_series = b_window - (beta * a_window + alpha)
        adf_pvalue = adfuller(spread_series)[1]

        current_a = closes_long[i]
        current_b = closes_short[i]

        spread_value = current_b - (beta * current_a + alpha)
        mean = np.mean(spread_series)
        std = np.std(spread_series)
        z = (spread_value - mean) / std if std > 0 else 0

        zscore_params.append({
            "zscore": z,
            "pvalue": pvalue,
            "adfpvalue": adf_pvalue,
            "correlation": corr,
            "alpha": alpha,
            "beta": beta,
            "spread": spread_value,
            "mean": mean,
            "std": std,
            "timestamp": timestamps[i]
        })

    return {
        "longTicker": long_ticker,
        "shortTicker": short_ticker,
        "zscoreParams": zscore_params
    }


def analyze_pair_timeseries(a, b, candles_dict, settings, mode, long_ticker, short_ticker):
    try:
        if mode == "send_best_chart":
            return analyze_pair_for_best(a, b, candles_dict, settings)
        elif mode == "test_trade":
            return analyze_pair_for_trade(long_ticker, short_ticker, candles_dict, settings)
        else:
            print(f"❌ Неизвестный режим: {mode}")
            return None
    except Exception as e:
        print(f"❌ Ошибка в analyze_pair_timeseries для пары {a}-{b}: {e}", file=sys.stderr)
        return None


def split_into_chunks(pairs, n):
    k, m = divmod(len(pairs), n)
    return [pairs[i * k + min(i, m):(i + 1) * k + min(i + 1, m)] for i in range(n)]


def process_chunk(input_path, output_path):
    with open(input_path) as f:
        data = json.load(f)

    candles_map = data["candles_map"]
    settings = data["settings"]
    mode = data["mode"]
    long_ticker = data["long_ticker"]
    short_ticker = data["short_ticker"]
    pairs = data["pairs"]

    all_results = []

    for a, b in pairs:
        result = analyze_pair_timeseries(a, b, candles_map, settings, mode, long_ticker, short_ticker)
        if result:
            all_results.append(result)

    with open(output_path, "w") as f:
        json.dump({"results": all_results}, f)


def main():
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        print(f"❌ Ошибка чтения JSON: {e}", file=sys.stderr)
        sys.exit(1)

    settings = input_data.get("settings")
    candles_map = input_data.get("candles_map")
    mode = input_data.get("mode")
    long_ticker = input_data.get("long_ticker")
    short_ticker = input_data.get("short_ticker")

    if not candles_map:
        print("❌ Не хватает данных: 'candles_map'", file=sys.stderr)
        sys.exit(1)

    if not settings:
        print("❌ Не хватает данных: 'settings'", file=sys.stderr)
        sys.exit(1)

    pairs = list(itertools.combinations(candles_map.keys(), 2))
    num_workers = min(cpu_count(), 8)
    chunks = split_into_chunks(pairs, num_workers)
    processes = []
    temp_files = []

    for i, chunk in enumerate(chunks):
        uid = uuid.uuid4().hex
        input_file = f"tmp_input_{uid}.json"
        output_file = f"tmp_output_{uid}.json"
        temp_files.extend([input_file, output_file])

        with open(input_file, "w") as f:
            json.dump({
                "candles_map": candles_map,
                "settings": settings,
                "mode": mode,
                "long_ticker": long_ticker,
                "short_ticker": short_ticker,
                "pairs": chunk
            }, f)

        p = Process(target=process_chunk, args=(input_file, output_file))
        p.start()
        processes.append((p, output_file))

    all_results = []

    for p, output_file in processes:
        p.join()
        if os.path.exists(output_file):
            with open(output_file) as f:
                output_data = json.load(f)
                all_results.extend(output_data["results"])

    for temp_file in temp_files:
        try:
            os.remove(temp_file)
        except Exception as e:
            print(f"⚠️ Не удалось удалить {temp_file}: {e}", file=sys.stderr)

    json.dump(all_results, sys.stdout)


if __name__ == "__main__":
    if len(sys.argv) == 3:
        process_chunk(sys.argv[1], sys.argv[2])
    else:
        try:
            main()
        except Exception:
            print("❌ Ошибка...")
            traceback.print_exc()
