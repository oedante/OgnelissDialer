#!/usr/bin/env python3
"""
Разбор холодного запуска по трассе Perfetto (1.3.5, 05.10.2026).

  pip install perfetto
  python scripts/analyze_startup_trace.py <трасса.perfetto-trace> [ещё трассы…]

Трассы холодного запуска — в docs/baseline/traces/<метка>/ (их сохраняет
phone_check.ps1) или в benchmark/build/outputs/connected_android_test_
additional_output/…/StartupBenchmark_startupNoCompilation_iter*.perfetto-trace.

Что печатает: время до первого кадра и на что его тратит главный поток
приложения — суммарно по именам участков (bindApplication — создание
Application и Hilt; activityStart/Resume; Choreographer#doFrame — первый
кадр Compose; VerifyClass/OpenDexFiles — загрузка классов). Несколько трасс —
усреднение. Две папки трасс («до» и «после») сравнивать так: запустить
по каждой и сопоставить строки.

Зачем: холодный запуск в 1.3.4 +13% (271 мс без компиляции), перевод иконок
дал 2% — причину надо видеть в трассе, а не угадывать.
"""
import sys
from collections import defaultdict
from perfetto.trace_processor import TraceProcessor

PKG = "com.ogneliss.dialer"

QUERY_MAIN = f"""
SELECT s.ts, s.dur, s.name, s.depth
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid)
JOIN process p USING (upid)
WHERE p.name LIKE '{PKG}%' AND t.is_main_thread = 1
ORDER BY s.ts
"""


def analyze(path):
    tp = TraceProcessor(trace=path)
    rows = list(tp.query(QUERY_MAIN))
    if not rows:
        print(f"{path}: нет участков главного потока {PKG}")
        return None
    start = rows[0].ts
    # Конец первого кадра: первый doFrame после начала активности
    frame_end = None
    seen_activity = False
    for r in rows:
        if r.name.startswith("activityStart") or r.name.startswith("activityResume"):
            seen_activity = True
        if seen_activity and r.name.startswith("Choreographer#doFrame"):
            frame_end = r.ts + r.dur
            break
    if frame_end is None:
        frame_end = max(r.ts + r.dur for r in rows)
    totals = defaultdict(int)
    for r in rows:
        if r.ts >= frame_end or r.depth > 3:
            continue
        name = r.name.split(" ")[0].split("(")[0][:60]
        totals[(r.depth, name)] += r.dur
    tp.close()
    return (frame_end - start) / 1e6, totals


def main(paths):
    results = [x for x in (analyze(p) for p in paths) if x]
    if not results:
        return
    n = len(results)
    total = sum(r[0] for r in results) / n
    agg = defaultdict(int)
    for _, totals in results:
        for k, v in totals.items():
            agg[k] += v
    print(f"Трасс: {n} · от старта процесса до конца первого кадра: {total:.1f} мс (среднее)\n")
    print(f"{'уровень':>7}  {'мс':>7}  участок")
    for (depth, name), dur in sorted(agg.items(), key=lambda kv: -kv[1])[:30]:
        print(f"{depth:>7}  {dur / n / 1e6:7.1f}  {'  ' * depth}{name}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
