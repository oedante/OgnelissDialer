#!/usr/bin/env python3
"""
Сравнение замеров Macrobenchmark «до / после» (1.3.3, 03.10.2026).

  python3 scripts/compare_benchmarks.py база.json новый.json [--merge-new]

--merge-new: замеры, которых нет в базе (например, впервые прошедший
холодный запуск), дописываются в базу.

Почему не по медиане: на nubia NX721J разброс тёплого запуска внутри
одного прогона — от 77 до 160 мс. При 9–10 повторах разница медиан
в 10–12% бывает просто шумом (03.10.2026: +12%, p = 0,20). Регрессией
считается изменение хуже порога И статистически значимое
(критерий Манна — Уитни, p < 0,05).
"""
import json, math, statistics as st, sys

THRESHOLD = 0.05
ALPHA = 0.05

def runs(data):
    out = {}
    for b in data["benchmarks"]:
        for metric, v in b.get("metrics", {}).items():
            if v.get("runs"):
                out[(b["name"], metric)] = v["runs"]
    return out

def mann_whitney_p(x, y):
    allv = sorted([(v, 0) for v in x] + [(v, 1) for v in y])
    ranks, i = [0.0] * len(allv), 0
    while i < len(allv):
        j = i
        while j + 1 < len(allv) and allv[j + 1][0] == allv[i][0]:
            j += 1
        for k in range(i, j + 1):
            ranks[k] = (i + j) / 2 + 1
        i = j + 1
    n1, n2 = len(x), len(y)
    u = sum(r for r, (_, g) in zip(ranks, allv) if g == 0) - n1 * (n1 + 1) / 2
    sd = math.sqrt(n1 * n2 * (n1 + n2 + 1) / 12)
    z = (u - n1 * n2 / 2) / sd if sd else 0.0
    return math.erfc(abs(z) / math.sqrt(2))

def merge_new(base_path, new_path):
    """Дописать в базу замеры, которых в ней нет (например, холодный запуск)."""
    base_doc, new_doc = json.load(open(base_path)), json.load(open(new_path))
    have = {b["name"] for b in base_doc["benchmarks"]}
    added = [b for b in new_doc["benchmarks"] if b["name"] not in have]
    if added:
        base_doc["benchmarks"] += added
        json.dump(base_doc, open(base_path, "w"), indent=4, ensure_ascii=False)
        print("В базу добавлены новые замеры: " + ", ".join(b["name"] for b in added))

def main(base_path, new_path, merge=False):
    base, new = runs(json.load(open(base_path))), runs(json.load(open(new_path)))
    print(f"{'Замер':32} {'до':>8} {'после':>8} {'изм.':>7} {'p':>6}  вывод")
    worst = 0
    for key in sorted(base.keys() & new.keys()):
        x, y = base[key], new[key]
        mx, my = st.median(x), st.median(y)
        change = my / mx - 1
        p = mann_whitney_p(x, y)
        if change > THRESHOLD and p < ALPHA:
            verdict, worst = "РЕГРЕССИЯ", 1
        elif change < -THRESHOLD and p < ALPHA:
            verdict = "лучше"
        elif abs(change) > THRESHOLD:
            verdict = "в пределах шума"
        else:
            verdict = "без изменений"
        print(f"{key[0] + '.' + key[1][:12]:32} {mx:8.1f} {my:8.1f} {change:+7.1%} {p:6.2f}  {verdict}")
    for key in sorted(new.keys() - base.keys()):
        print(f"{key[0] + '.' + key[1][:12]:32} {'—':>8} {st.median(new[key]):8.1f}  нет в базе")
    if merge:
        merge_new(base_path, new_path)
    sys.exit(worst)

if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--merge-new"]
    if len(args) != 2:
        sys.exit(__doc__)
    main(args[0], args[1], merge="--merge-new" in sys.argv)
