import itertools
import json
import sys
import traceback
from statsmodels.tsa.stattools import adfuller


def is_adf_stationary(series, significance=0.05):
    try:
        adf_result = adfuller(series, autolag='AIC')
        pvalue = adf_result[1]
        return pvalue < significance, pvalue
    except Exception as e:
        print(f"❌ Ошибка ADF: {e}")
        return False, 1.0


def analyze_adf_only(a, b, candles_dict, config):
    try:
        window = config["windowSize"]
        adf_significance = config.get("adfSignificanceLevel", 0.05)

        candles_a = candles_dict.get(a)
        candles_b = candles_dict.get(b)

        if not candles_a or not candles_b:
            return None
        if len(candles_a) != len(candles_b) or len(candles_a) <= window + 1:
            return None

        closes_a = [c["close"] for c in candles_a]
        closes_b = [c["close"] for c in candles_b]

        i = len(closes_a) - 1
        spread_series = [closes_a[j] - closes_b[j] for j in range(i - window, i)]

        is_stationary, pvalue = is_adf_stationary(spread_series, adf_significance)
        if not is_stationary:
            print(f"⛔ {a}-{b} отклонена: adf_pvalue={pvalue:.4f}")
            return None

        print(f"✅ {a}-{b} прошла ADF: adf_pvalue={pvalue:.4f}")
        return {
            "pair": f"{a}-{b}",
            "adf_pvalue": pvalue
        }

    except Exception as e:
        print(f"❌ Ошибка при анализе пары {a}-{b}: {e}")
        return None


def main():
    with open("candles.json") as f:
        candles_dict = json.load(f)

    with open("settings.json") as f:
        config = json.load(f)

    chat_id = "159178617"
    chat_config = config.get(chat_id)
    if not chat_config:
        print(f"❌ Не найден конфиг для chat_id {chat_id}")
        sys.exit(1)

    pairs = list(itertools.combinations(candles_dict.keys(), 2))
    results = []

    for a, b in pairs:
        result = analyze_adf_only(a, b, candles_dict, chat_config)
        if result:
            results.append(result)

    with open("adf_results.json", "w") as f:
        json.dump(results, f, indent=2)

    print(f"🎯 ADF анализ завершён. Найдено: {len(results)} пар.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("❌ Ошибка во время выполнения скрипта:", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
