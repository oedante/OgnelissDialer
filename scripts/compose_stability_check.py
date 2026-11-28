#!/usr/bin/env python3
"""
СТАБИЛЬНОСТЬ COMPOSE — НЕ ХУЖЕ, ЧЕМ БЫЛО (2.9.13, техдолг, этап 1.5).

Аудит 28.11.2026: «метрики Compose включаются флагом — сделать их регулярными
и ронять сборку при деградации». Метрики пишет компилятор при
-Pcompose.metrics=true (блок composeCompiler в app/build.gradle.kts) в
app/build/compose_metrics/.

Что считаем (по итогам модуля, *-module.json):
  • неустойчивые классы — их параметры заставляют Compose перерисовывать
    экран без нужды;
  • перезапускаемые функции, которые нельзя пропустить;
  • неустойчивые аргументы в вызовах.

Порог — scripts/compose_stability_baseline.json. Стало больше — проверка
падает: новый неустойчивый класс в параметре экрана — повод разобраться
(@Immutable/@Stable, неизменяемые коллекции). Стало меньше — проверка
проходит и подсказывает опустить порог: так он только снижается.

Метрики пишутся только по файлам, которые компилятор собрал в этот раз: после
инкрементной сборки они неполные. В CI сборка всегда с нуля; у себя — с --rerun.

Запуск:
  ./gradlew :app:compileDebugKotlin -Pcompose.metrics=true --rerun
  python3 scripts/compose_stability_check.py            # сверить с порогом
  python3 scripts/compose_stability_check.py --update   # записать текущие цифры в порог
"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
METRICS = os.path.join(ROOT, 'app', 'build', 'compose_metrics')
BASELINE = os.path.join(ROOT, 'scripts', 'compose_stability_baseline.json')


def count() -> dict:
    """
    Итоги модуля — *-module.json (полные: и лямбды, и закрытые функции).
    В *-composables.txt только именованные функции — там почти всё
    пропускаемое (strong skipping), а 499 непропускаемых живут в лямбдах.
    """
    modules = glob.glob(os.path.join(METRICS, '**', '*-module.json'), recursive=True)
    if not modules:
        print(f'Нет метрик в {METRICS}: сначала ./gradlew :app:compileDebugKotlin -Pcompose.metrics=true --rerun')
        sys.exit(2)
    total = {'unstable_classes': 0, 'restartable_not_skippable': 0, 'unstable_arguments': 0}
    for f in modules:
        m = json.load(open(f, encoding='utf-8'))
        total['unstable_classes'] += m['inferredUnstableClasses']
        total['restartable_not_skippable'] += m['restartableComposables'] - m['skippableComposables']
        total['unstable_arguments'] += m['knownUnstableArguments']
    return total


def main() -> None:
    now = count()
    if '--update' in sys.argv:
        with open(BASELINE, 'w', encoding='utf-8') as f:
            json.dump(now, f, ensure_ascii=False, indent=2)
            f.write('\n')
        print(f'Порог записан: {now}')
        return
    base = json.load(open(BASELINE, encoding='utf-8'))
    worse = []
    for key, value in now.items():
        limit = base.get(key, 0)
        mark = 'больше порога' if value > limit else ('меньше порога — можно опустить (--update)' if value < limit else 'как было')
        print(f'{key}: {value} (порог {limit}) — {mark}')
        if value > limit:
            worse.append(key)
    if worse:
        print('\nСтабильность Compose ухудшилась. Списки — app/build/compose_metrics/*-classes.txt '
              'и *-composables.csv. Если так и задумано — python3 scripts/compose_stability_check.py --update.')
        sys.exit(1)


if __name__ == '__main__':
    main()
