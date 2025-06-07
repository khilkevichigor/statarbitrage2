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


def analyze_pair_timeseries(a, b, candles_dict, chat_config, mode):
    try:
        window = chat_config["windowSize"]
        significance = chat_config["significanceLevel"]
        min_corr = chat_config.get("minCorrelation")

        closes_a = np.array([c["close"] for c in candles_dict[a]])
        closes_b = np.array([c["close"] for c in candles_dict[b]])
        timestamps = [c["timestamp"] for c in candles_dict[a]]  # лишнее?

        if len(closes_a) != len(closes_b):
            return None

        corr = np.corrcoef(closes_a, closes_b)[0, 1]
        if mode == "sendBestChart":
            if abs(corr) < min_corr:
                return None

        is_coint, pvalue = is_cointegrated(closes_a, closes_b, significance)
        if mode == "sendBestChart":
            if not is_coint:
                return None

        entries = []

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

            # if mode == "sendBestChart":  #говорим что лонг а что шорт только 1 раз что бы не ломать тестТрейд
            longticker = a if z > 0 else b  # пусть всегда считает - мы все равно сетим это только когда создаем pairData
            shortticker = b if z > 0 else a

            entries.append({
                "zscore": z,
                "pvalue": pvalue,
                "adfpvalue": adf_pvalue,
                "correlation": corr,
                "alpha": alpha,
                "beta": beta,
                "spread": spread_value,
                "mean": mean,
                "std": std,
                "a": a,
                "b": b,
                "longticker": longticker,
                "shortticker": shortticker,
                "atickercurrentprice": current_a,
                "btickercurrentprice": current_b,
                "timestamp": timestamps[i]
            })

        return {
            "a": a,
            "b": b,
            "entries": entries
        }

    except Exception as e:
        print(f"❌ Ошибка в timeseries анализе пары {a}-{b}: {e}")
        return None


def split_into_chunks(pairs, n):
    k, m = divmod(len(pairs), n)
    return [pairs[i * k + min(i, m):(i + 1) * k + min(i + 1, m)] for i in range(n)]


def process_chunk(input_path, output_path):
    with open(input_path) as f:
        data = json.load(f)

    candles = data["candles"]
    config = data["config"]
    mode = data["mode"]
    pairs = data["pairs"]

    all_results = []

    for a, b in pairs:
        result = analyze_pair_timeseries(a, b, candles, config, mode)
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
    candles = input_data.get("candlesMap")
    mode = input_data.get("mode")

    if not candles or not settings:
        print("❌ Не хватает данных: 'candles' или 'settings'", file=sys.stderr)
        sys.exit(1)

    pairs = list(itertools.combinations(candles.keys(), 2))
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
                "candles": candles,
                "config": settings,
                "mode": mode,
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
