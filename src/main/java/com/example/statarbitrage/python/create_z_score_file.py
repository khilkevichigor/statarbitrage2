import itertools
import json
import numpy as np
import os
import sys
import traceback
import uuid
from multiprocessing import cpu_count, Process
from statsmodels.tsa.stattools import coint


def is_cointegrated(s1, s2, significance):
    try:
        score, pvalue, _ = coint(s1, s2)
        return pvalue < significance, pvalue
    except Exception as e:
        print(f"❌ Ошибка в cointegration: {e}")
        return False, 1.0


def analyze_pair(a, b, candles_dict, chat_config):
    try:
        window = chat_config["windowSize"]
        zscore_entry = chat_config["zscoreEntry"]
        significance = chat_config["significanceLevel"]

        if not a or not b:
            return None

        candles_a = candles_dict.get(a)
        candles_b = candles_dict.get(b)

        if not isinstance(candles_a, list) or not isinstance(candles_b, list):
            return None
        if len(candles_a) != len(candles_b) or len(candles_a) <= window + 1:
            return None

        closes_a = [candle["close"] for candle in candles_a]
        closes_b = [candle["close"] for candle in candles_b]

        if np.std(closes_a) == 0 or np.std(closes_b) == 0:
            return None
        if np.allclose(closes_a, closes_b):
            return None

        is_coint, pvalue = is_cointegrated(closes_a, closes_b, significance)
        if not is_coint:
            return None

        i = len(closes_a) - 1
        spread = closes_a[i] - closes_b[i]
        window_spread = [closes_a[j] - closes_b[j] for j in range(i - window, i)]
        mean = np.mean(window_spread)
        std = np.std(window_spread)
        z = (spread - mean) / std if std > 0 else 0

        if abs(z) < zscore_entry:
            return None

        longticker = b if z > 0 else a
        shortticker = a if z > 0 else b

        long_price = candles_dict[longticker][-1]["close"]
        short_price = candles_dict[shortticker][-1]["close"]
        timestamp_of_signal = candles_dict[longticker][-1]["timestamp"]

        return {
            "zscore": z,
            "pvalue": pvalue,
            "spread": spread,
            "mean": mean,
            "longticker": longticker,
            "shortticker": shortticker,
            "longtickercurrentprice": long_price,
            "shorttickercurrentprice": short_price,
            "timestamp": timestamp_of_signal
        }

    except Exception as e:
        print(f"❌ Ошибка при анализе пары {a}-{b}: {e}")
        return None


def split_into_chunks(pairs, n):
    k, m = divmod(len(pairs), n)
    return [pairs[i * k + min(i, m):(i + 1) * k + min(i + 1, m)] for i in range(n)]


def process_chunk(input_path, output_path):
    with open(input_path) as f:
        data = json.load(f)

    candles = data["candles"]
    config = data["config"]
    pairs = data["pairs"]

    results = []
    for a, b in pairs:
        result = analyze_pair(a, b, candles, config)
        if result:
            results.append(result)

    with open(output_path, "w") as f:
        json.dump(results, f)


def main():
    print("📥 Загрузка candles.json...")
    with open("candles.json") as f:
        candles_dict = json.load(f)

    print("⚙️ Загрузка settings.json...")
    with open("settings.json") as f:
        config = json.load(f)

    chat_id = "159178617"
    chat_config = config.get(chat_id)
    if not chat_config:
        print(f"❌ Не найден конфиг для chat_id {chat_id}")
        sys.exit(1)

    pairs = list(itertools.combinations(candles_dict.keys(), 2))
    print(f"🔍 Всего пар для анализа: {len(pairs)}")

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
                "candles": candles_dict,
                "config": chat_config,
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
                all_results.extend(json.load(f))

    if all_results:
        with open("z_score.json", "w") as f:
            json.dump(all_results, f, indent=2)
        print(f"✅ Найдено {len(all_results)} пар. 💾 z_score.json создан.")
    else:
        print("⚠️ Подходящих пар не найдено.")

    # 🔥 Удаление временных файлов
    for file in temp_files:
        try:
            os.remove(file)
        except Exception:
            pass


if __name__ == "__main__":
    if len(sys.argv) == 3:
        process_chunk(sys.argv[1], sys.argv[2])
    else:
        try:
            main()
        except Exception:
            print("❌ Ошибка во время выполнения скрипта:", file=sys.stderr)
            traceback.print_exc(file=sys.stderr)
            sys.exit(1)
