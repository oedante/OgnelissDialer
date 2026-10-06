#!/usr/bin/env python3
"""
Новая база замеров из одного или нескольких прогонов (1.3.4, 05.10.2026).

  python scripts/set_baseline.py docs/baseline/benchmarkData-<метка>-cold.json docs/baseline/benchmarkData-<метка>.json

Пишет docs/baseline/benchmarkData-base.json: замеры из перечисленных файлов
(при совпадении имени — из последнего), остальные — из прежней базы.
Прежняя база сохраняется рядом как benchmarkData-base-<дата>.json.

Когда менять базу: после ПРИНЯТОГО шага, если замеры стали лучше или
изменение объяснено и принято, — чтобы дальше сравнивать с тем, что есть,
а не с давно пройденным. Не менять, чтобы «спрятать» регрессию.
"""
import json, shutil, sys, datetime
from pathlib import Path

base = Path("docs/baseline/benchmarkData-base.json")
if len(sys.argv) < 2:
    sys.exit(__doc__)

merged = json.loads(base.read_text(encoding="utf-8")) if base.exists() else {"benchmarks": []}
by_name = {b["name"]: b for b in merged.get("benchmarks", [])}
context = merged.get("context")
for f in sys.argv[1:]:
    data = json.loads(Path(f).read_text(encoding="utf-8"))
    context = data.get("context", context)
    for b in data.get("benchmarks", []):
        by_name[b["name"]] = b
        print(f"  {b['name']:24} ← {Path(f).name}")

if base.exists():
    backup = base.with_name(f"benchmarkData-base-{datetime.date.today():%Y%m%d}.json")
    shutil.copy(base, backup)
    print(f"Прежняя база: {backup}")
base.write_text(json.dumps({"context": context, "benchmarks": list(by_name.values())},
                           ensure_ascii=False, indent=4), encoding="utf-8")
print(f"Новая база: {base} — {len(by_name)} замеров")
