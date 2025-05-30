# create_z_score_file.py

import itertools
import json
import numpy as np
import sys
import traceback
from concurrent.futures import ThreadPoolExecutor, as_completed
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

        # Дополнительные проверки
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


def main():
    print("📥 Загрузка all_candles.json...")
    with open("all_candles.json") as f:
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
    total = len(pairs)
    print(f"🔍 Анализируем {total} пар с многопоточностью...")

    results = []
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = {
            executor.submit(analyze_pair, a, b, candles_dict, chat_config): (a, b)
            for a, b in pairs
        }

        for idx, future in enumerate(as_completed(futures), 1):
            res = future.result()
            if res:
                results.append(res)

            if idx % 100 == 0 or idx == total:
                print(f"⏳ Обработано пар: {idx}/{total}")

    if results:
        print(f"✅ Найдено {len(results)} подходящих пар")
        with open("z_score.json", "w") as f:
            json.dump(results, f, indent=2)
        print("💾 Файл z_score.json успешно создан.")
    else:
        print("⚠️ Нет подходящих пар — файл не создан.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("❌ Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
