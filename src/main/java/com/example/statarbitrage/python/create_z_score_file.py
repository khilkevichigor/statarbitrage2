import itertools
import json
import numpy as np
import os
import sys
import traceback
import uuid
from multiprocessing import cpu_count, Process
from statsmodels.tsa.stattools import adfuller
from statsmodels.tsa.stattools import coint

# Глобальный словарь статистики отказов
rejection_stats = {
    "empty_tickers": 0,
    "invalid_format": 0,
    "not_enough_data": 0,
    "zero_volatility": 0,
    "almost_identical": 0,
    "low_correlation": 0,
    "high_pvalue": 0,
    "high_adf_pvalue": 0,
    "low_zscore": 0
}


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
        adf_significance = chat_config.get("adfSignificanceLevel")
        min_corr = chat_config.get("minCorrelation")  # Добавлен порог корреляции

        if not a or not b:
            print(f"⚠️ Пропуск: пустые тикеры {a}, {b}")
            rejection_stats["empty_tickers"] += 1
            return None

        candles_a = candles_dict.get(a)
        candles_b = candles_dict.get(b)

        if not isinstance(candles_a, list) or not isinstance(candles_b, list):
            print(f"⚠️ Пропуск: неверный формат данных для {a}, {b}")
            rejection_stats["invalid_format"] += 1
            return None
        if len(candles_a) != len(candles_b) or len(candles_a) <= window + 1:
            print(f"⚠️ Пропуск: недостаточно данных для {a}, {b}")
            rejection_stats["not_enough_data"] += 1
            return None

        closes_a = [candle["close"] for candle in candles_a]
        closes_b = [candle["close"] for candle in candles_b]

        if np.std(closes_a) == 0 or np.std(closes_b) == 0:
            print(f"⚠️ Пропуск: нулевая волатильность у {a} или {b}")
            rejection_stats["zero_volatility"] += 1
            return None
        if np.allclose(closes_a, closes_b):
            print(f"⚠️ Пропуск: {a} и {b} почти идентичны")
            rejection_stats["almost_identical"] += 1
            return None

        # 💡 Корреляция
        corr = np.corrcoef(closes_a, closes_b)[0, 1]
        if abs(corr) < min_corr:
            print(f"⛔ {a}-{b} отклонена: корреляция {corr:.2f} < {min_corr}")
            rejection_stats["low_correlation"] += 1
            return None

        # 💡 Коинтеграция
        is_coint, pvalue = is_cointegrated(closes_a, closes_b, significance)
        if not is_coint:
            print(f"⛔ {a}-{b} отклонена: p-value={pvalue:.4f} > {significance}")
            rejection_stats["high_pvalue"] += 1
            return None

        i = window
        spread_series = [closes_a[j] - closes_b[j] for j in range(i - window, i)]

        adf_pvalue = adfuller(spread_series)[1]
        if adf_pvalue > adf_significance:
            print(f"⛔ {a}-{b} отклонена ADF: adf_pvalue={adf_pvalue:.4f} > {adf_significance}")
            rejection_stats["high_adf_pvalue"] += 1
            return None

        spread = closes_a[i] - closes_b[i]
        mean = np.mean(spread_series)
        std = np.std(spread_series)
        z = (spread - mean) / std if std > 0 else 0

        if abs(z) < zscore_entry:
            print(f"⛔ {a}-{b} отклонена: z-score={z:.2f} < {zscore_entry}")
            rejection_stats["low_zscore"] += 1
            return None

        longticker = b if z > 0 else a
        shortticker = a if z > 0 else b

        long_price = candles_dict[longticker][-1]["close"]
        short_price = candles_dict[shortticker][-1]["close"]
        timestamp_of_signal = candles_dict[longticker][-1]["timestamp"]

        print(f"✅ Подходящая пара: {a}-{b} | z={z:.2f} | p={pvalue:.4f} | adf={adf_pvalue:.4f} | corr={corr:.2f}")
        return {
            "zscore": z,
            "pvalue": pvalue,
            "adfpvalue": adf_pvalue,
            "correlation": corr,
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
        json.dump({
            "results": results,
            "rejection_stats": rejection_stats
        }, f)


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
    total_rejections = {key: 0 for key in rejection_stats}

    for p, output_file in processes:
        p.join()
        if os.path.exists(output_file):
            with open(output_file) as f:
                output_data = json.load(f)
                all_results.extend(output_data["results"])
                for key in total_rejections:
                    total_rejections[key] += output_data["rejection_stats"].get(key, 0)

    if all_results:
        with open("z_score.json", "w") as f:
            json.dump(all_results, f, indent=2)
        print(f"✅ Найдено {len(all_results)} пар. 💾 z_score.json создан.")
    else:
        print("⚠️ Подходящих пар не найдено.")

    print("\n📉 Статистика отказов:")
    for k, v in total_rejections.items():
        print(f"{k}: {v}")



if __name__ == "__main__":
    if len(sys.argv) == 3:
        process_chunk(sys.argv[1], sys.argv[2])
    else:
        try:
            main()
            print("\n📉 Статистика отказов:")
            for p, output_file in processes:
                p.join()
                if os.path.exists(output_file):
                    with open(output_file) as f:
                        output_data = json.load(f)
                        all_results.extend(output_data["results"])
                        # агрегируем статистику
                        for key in rejection_stats:
                            rejection_stats[key] += output_data["rejection_stats"].get(key, 0)

        except Exception:
            print("❌ Ошибка во время выполнения скрипта:", file=sys.stderr)
            traceback.print_exc(file=sys.stderr)
            sys.exit(1)
