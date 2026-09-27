#!/usr/bin/env python3
"""
СБОРКА БАЗЫ НУМЕРАЦИИ ИЗ ФАЙЛОВ РОССВЯЗИ.

Вход: выгрузки российской системы и плана нумерации (csv, разделитель «;»)
       ABC-3xx, ABC-4xx, ABC-8xx, DEF-9xx.
Выход: app/src/main/assets/numbering_plan.bin

Запускать при обновлении выгрузок; результат коммитить вместе с ними.

══════════════════════════════════════════════════════════════════════════
ПОЧЕМУ СВОЙ ФОРМАТ, А НЕ SQLITE
══════════════════════════════════════════════════════════════════════════
Оба варианта были собраны и измерены на этих же данных:

    SQLite с индексом      15.9 МБ файл, ~5.8 МБ в архиве приложения
    свой двоичный формат    4.0 МБ файл, ~1.5 МБ в архиве

Разница в архиве — четыре мегабайта на приложение, которое целиком
весит меньше. При этом поиск в своём формате не медленнее: записи
фиксированной длины, внутри кода идёт двоичный поиск по смещению,
без разбора строк и без SQL.

Ещё соображение: база read-only и меняется только вместе с выпуском.
Room дал бы миграции, транзакции и запросы — всё то, что здесь не нужно.

══════════════════════════════════════════════════════════════════════════
ФОРМАТ ФАЙЛА
══════════════════════════════════════════════════════════════════════════
Всё big-endian.

    "OGNP"                     сигнатура, 4 байта
    версия                     1 байт  (сейчас 1)
    кодов                      2 байта
    ── оглавление, по 10 байт на код, по возрастанию кода ──
    код                        2 байта   (АВС/DEF: 301, 495, 900 …)
    смещение блока             4 байта   (от начала файла)
    записей в блоке            4 байта
    ── таблицы строк ──
    операторов                 4 байта, далее на каждого: длина 2 + UTF-8
    регионов                   4 байта, далее так же
    ── блоки диапазонов, по 10 байт на запись, по возрастанию начала ──
    начало                     3 байта   (последние 7 цифр номера)
    длина - 1                  3 байта   (конец = начало + длина - 1)
    оператор                   2 байта   (номер в таблице)
    регион                     2 байта

Почему «длина», а не «конец»: длина почти всегда мала и лучше
сжимается в архиве, а конец восстанавливается сложением.

══════════════════════════════════════════════════════════════════════════
ЧТО БЕРЁТСЯ ИЗ КОЛОНОК
══════════════════════════════════════════════════════════════════════════
«Оператор» — как есть.

Регион — «Регион», а если там прочерк (так у всех мобильных DEF-9xx),
то «Территория ГАР». Внутри значения разделитель «|» меняется
на запятую: «г. Улан-Удэ|Республика Бурятия» → «г. Улан-Удэ, Республика
Бурятия».

⚠️ Для мобильного номера это регион ВЫДАЧИ, а не место, откуда звонят:
с переносом номера человек уезжает вместе с ним. Подпись в приложении
должна говорить «МТС, Краснодарский край», без слова «звонит из».
"""

import csv
import struct
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIR = ROOT / "store" / "source" / "numbering"
OUT = ROOT / "app" / "src" / "main" / "assets" / "numbering_plan.bin"

SIGNATURE = b"OGNP"
VERSION = 1


def read_rows():
    """Все диапазоны из всех выгрузок, без дублей полей, которые нам не нужны."""
    rows = []
    files = sorted(SOURCE_DIR.glob("*.csv"))
    if not files:
        sys.exit(f"Нет выгрузок в {SOURCE_DIR}")

    for path in files:
        # utf-8-sig: у выгрузок Россвязи в начале файла стоит BOM,
        # и без этого первая колонка первой строки читается с мусором
        with open(path, encoding="utf-8-sig", newline="") as fh:
            reader = csv.reader(fh, delimiter=";")
            next(reader, None)
            for row in reader:
                if len(row) < 7:
                    continue
                code, begin, end = int(row[0]), int(row[1]), int(row[2])
                operator = row[4].strip()

                region = row[5].strip()
                if region in ("-", ""):
                    region = row[6].strip()
                # «|» — разделитель уровней (город|область). Дубли убираем:
                # у части строк город и область совпадают
                parts = [p.strip() for p in region.split("|") if p.strip()]
                region = ", ".join(dict.fromkeys(parts))

                rows.append((code, begin, end, operator, region))
        print(f"  {path.name}: прочитано")
    return rows


def merge(rows):
    """
    Склейка соседних диапазонов одного оператора и региона.

    Россвязь выдаёт номера кусками по тысяче, и подряд идущие куски одного
    оператора в одном регионе для нас неразличимы. Склейка убирает
    около 30 тысяч записей и ничего не меняет в ответах.
    """
    rows.sort(key=lambda r: (r[0], r[1]))
    out = []
    for row in rows:
        if out:
            prev = out[-1]
            same = prev[0] == row[0] and prev[3] == row[3] and prev[4] == row[4]
            if same and prev[2] + 1 >= row[1]:
                out[-1] = (row[0], prev[1], max(prev[2], row[2]), row[3], row[4])
                continue
        out.append(row)
    return out


def build(rows):
    operators, regions = {}, {}

    def intern(table, value):
        if value not in table:
            table[value] = len(table)
        return table[value]

    by_code = defaultdict(list)
    for code, begin, end, operator, region in rows:
        by_code[code].append(
            (begin, end, intern(operators, operator), intern(regions, region))
        )

    strings = bytearray()
    for table in (operators, regions):
        strings += struct.pack(">I", len(table))
        for name, _ in sorted(table.items(), key=lambda kv: kv[1]):
            encoded = name.encode("utf-8")
            strings += struct.pack(">H", len(encoded)) + encoded

    codes = sorted(by_code)
    header_size = len(SIGNATURE) + 1 + 2 + len(codes) * 10 + len(strings)

    body = bytearray()
    directory = bytearray()
    for code in codes:
        items = sorted(by_code[code])
        directory += struct.pack(">HII", code, header_size + len(body), len(items))
        for begin, end, operator, region in items:
            body += begin.to_bytes(3, "big")
            body += (end - begin).to_bytes(3, "big")
            body += struct.pack(">HH", operator, region)

    blob = (
        SIGNATURE
        + bytes([VERSION])
        + struct.pack(">H", len(codes))
        + bytes(directory)
        + bytes(strings)
        + bytes(body)
    )
    return blob, len(codes), len(operators), len(regions)


def main():
    print("Чтение выгрузок…")
    rows = read_rows()
    print(f"  диапазонов: {len(rows)}")

    rows = merge(rows)
    print(f"  после склейки: {len(rows)}")

    blob, codes, operators, regions = build(rows)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(blob)

    print(
        f"Готово: {OUT.relative_to(ROOT)}\n"
        f"  кодов {codes}, операторов {operators}, регионов {regions}\n"
        f"  размер {len(blob) / 1048576:.2f} МБ"
    )


if __name__ == "__main__":
    main()
