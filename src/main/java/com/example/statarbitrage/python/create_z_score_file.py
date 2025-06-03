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


def analyze_pair(a, b, candles_dict, chat_config):
    try:
        window = chat_config["windowSize"]
        zscore_entry = chat_config["zscoreEntry"]
        significance = chat_config["significanceLevel"]
        adf_significance = chat_config.get("adfSignificanceLevel")
        min_corr = chat_config.get("minCorrelation")

        if not a or not b:
            print(f"⚠️ Пропуск: пустые тикеры {a}, {b}")
            return None

        candles_a = candles_dict.get(a)
        candles_b = candles_dict.get(b)

        if not isinstance(candles_a, list) or not isinstance(candles_b, list):
            print(f"⚠️ Пропуск: неверный формат данных для {a}, {b}")
            return None
        if len(candles_a) != len(candles_b) or len(candles_a) <= window + 1:
            print(f"⚠️ Пропуск: недостаточно данных для {a}, {b}")
            return None

        closes_a = [candle["close"] for candle in candles_a]
        closes_b = [candle["close"] for candle in candles_b]

        if np.std(closes_a) == 0 or np.std(closes_b) == 0:
            print(f"⚠️ Пропуск: нулевая волатильность у {a} или {b}")
            return None
        if np.allclose(closes_a, closes_b):
            print(f"⚠️ Пропуск: {a} и {b} почти идентичны")
            return None

        corr = np.corrcoef(closes_a, closes_b)[0, 1]
        if abs(corr) < min_corr:
            # print(f"⛔ {a}-{b} отклонена: корреляция {corr:.2f} < {min_corr}")
            return None

        is_coint, pvalue = is_cointegrated(closes_a, closes_b, significance)
        if not is_coint:
            # print(f"⛔ {a}-{b} отклонена: p-value={pvalue:.4f} > {significance}")
            return None

        i = window
        spread_series = [closes_a[j] - closes_b[j] for j in range(i - window, i)]

        adf_pvalue = adfuller(spread_series)[1]
        if adf_pvalue > adf_significance:
            # print(f"⛔ {a}-{b} отклонена ADF: adf_pvalue={adf_pvalue:.4f} > {adf_significance}")
            return None

        spread = closes_a[i] - closes_b[i]
        mean = np.mean(spread_series)
        std = np.std(spread_series)
        z = (spread - mean) / std if std > 0 else 0

        if abs(z) < zscore_entry:
            # print(f"⛔ {a}-{b} отклонена: z-score={z:.2f} < {zscore_entry}")
            return None

        longticker = b if z > 0 else a
        shortticker = a if z > 0 else b

        long_price = candles_dict[longticker][-1]["close"]
        short_price = candles_dict[shortticker][-1]["close"]
        timestamp_of_signal = candles_dict[longticker][-1]["timestamp"]

        # print(f"✅ Подходящая пара: {a}-{b} | z={z:.2f} | p={pvalue:.4f} | adf={adf_pvalue:.4f} | corr={corr:.2f}")
        return {
            "zscore": z,
            "pvalue": pvalue,
            "adfpvalue": adf_pvalue,
            "correlation": corr,
            "spread": spread,
            "mean": mean,
            "std": std,
            "longticker": longticker,
            "shortticker": shortticker,
            "longtickercurrentprice": long_price,
            "shorttickercurrentprice": short_price,
            "timestamp": timestamp_of_signal
        }

    except Exception as e:
        print(f"❌ Ошибка при анализе пары {a}-{b}: {e}")
        return None


def analyze_pair_ols(a, b, candles_dict, chat_config):
    try:
        window = chat_config["windowSize"]
        zscore_entry = chat_config["zscoreEntry"]
        significance = chat_config["significanceLevel"]
        adf_significance = chat_config.get("adfSignificanceLevel")
        min_corr = chat_config.get("minCorrelation")

        if not a or not b:
            print(f"⚠️ Пропуск: пустые тикеры {a}, {b}")
            return None

        candles_a = candles_dict.get(a)
        candles_b = candles_dict.get(b)

        if not isinstance(candles_a, list) or not isinstance(candles_b, list):
            print(f"⚠️ Пропуск: неверный формат данных для {a}, {b}")
            return None
        if len(candles_a) != len(candles_b) or len(candles_a) <= window + 1:
            print(f"⚠️ Пропуск: недостаточно данных для {a}, {b}")
            return None

        closes_a = np.array([c["close"] for c in candles_a])
        closes_b = np.array([c["close"] for c in candles_b])

        if np.std(closes_a) == 0 or np.std(closes_b) == 0:
            print(f"⚠️ Пропуск: нулевая волатильность у {a} или {b}")
            return None
        if np.allclose(closes_a, closes_b):
            print(f"⚠️ Пропуск: {a} и {b} почти идентичны")
            return None

        corr = np.corrcoef(closes_a, closes_b)[0, 1]
        if abs(corr) < min_corr:
            return None

        # Коинтеграция
        is_coint, pvalue = is_cointegrated(closes_a, closes_b, significance)
        if not is_coint:
            return None

        # Берем последние window наблюдений
        a_window = closes_a[-window:]
        b_window = closes_b[-window:]

        # Линейная регрессия: b = alpha + beta * a
        X = add_constant(a_window)
        model = OLS(b_window, X).fit()
        alpha = model.params[0]
        beta = model.params[1]

        # Спред = b - (beta * a + alpha)
        spread_series = b_window - (beta * a_window + alpha)

        adf_pvalue = adfuller(spread_series)[1]
        if adf_pvalue > adf_significance:
            return None

        # Текущий спред и z-score
        current_a = closes_a[-1]
        current_b = closes_b[-1]
        spread_value = current_b - (beta * current_a + alpha)

        mean = np.mean(spread_series)
        std = np.std(spread_series)
        z = (spread_value - mean) / std if std > 0 else 0

        if abs(z) < zscore_entry:
            return None

        longticker = b if z > 0 else a
        shortticker = a if z > 0 else b

        long_price = candles_dict[longticker][-1]["close"]
        short_price = candles_dict[shortticker][-1]["close"]
        timestamp_of_signal = candles_dict[longticker][-1]["timestamp"]

        return {
            "pair": f"{a}-{b}",
            "zscore": round(z, 4),
            "pvalue": round(pvalue, 6),
            "adfpvalue": round(adf_pvalue, 6),
            "correlation": round(corr, 4),
            "alpha": round(alpha, 8),
            "beta": round(beta, 8),
            "spread": round(spread_value, 8),
            "mean": round(mean, 8),
            "std": round(std, 8),
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
        result = analyze_pair_ols(a, b, candles, config)
        if result:
            results.append(result)

    with open(output_path, "w") as f:
        json.dump({"results": results}, f)


def main():
    print("📥 Чтение входных данных из stdin...", file=sys.stderr)
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        print(f"❌ Ошибка чтения JSON: {e}", file=sys.stderr)
        sys.exit(1)

    candles_dict = input_data.get("candlesMap")
    settings_dict = input_data.get("settings")

    if not candles_dict or not settings_dict:
        print("❌ Не хватает данных: 'candles' или 'settings'", file=sys.stderr)
        sys.exit(1)

    pairs = list(itertools.combinations(candles_dict.keys(), 2))
    print(f"🔍 Всего пар для анализа: {len(pairs)}", file=sys.stderr)

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
                "config": settings_dict,
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

    if all_results:
        print(json.dumps(all_results, indent=2))
        print(f"✅ Найдено {len(all_results)} пар.", file=sys.stderr)
    else:
        print("⚠️ Подходящих пар не найдено.", file=sys.stderr)


if __name__ == "__main__":
    if len(sys.argv) == 3:
        process_chunk(sys.argv[1], sys.argv[2])
    else:
        try:
            main()
        except Exception:
            print("❌ Ошибка...")
            traceback.print_exc()
