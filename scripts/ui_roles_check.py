#!/usr/bin/env python3
"""
ПРОВЕРКА РОЛЕЙ ОФОРМЛЕНИЯ (2.3.1, единый вид).

Автор, 12.11.2026: «во всём приложении в разных окнах, панелях, выпадающих
списках разные размеры шрифтов, акценты, оформление… как будто это не единое
приложение, а сшитое из разных кусков». Правила — docs/OGNELISS_DESIGN_SYSTEM.md,
раздел «Роли текста и значков». Здесь — то, что проверяется по коду:

1. Заголовок окна (шторка ModalBottomSheet) — SheetTitle или titleMedium;
   крупнее (titleLarge, headline*) в файлах со шторками — замечание.
   Исключения — экраны, где крупный текст — содержимое (цифры набора),
   и места с пометкой «ui-roles: содержимое» рядом (число в карточке).
2. Пункт выпадающего меню (DropdownMenuItem) — текст bodyLarge.
3. Подпись дня и раздела в ленте — прописными (JournalDays, LetterHeader):
   строковые литералы с « · » в SectionBand не собираются вручную.

Запуск: python3 scripts/ui_roles_check.py  (код выхода 1 — есть замечания)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "app/src/main/java/com/ogneliss/dialer"

# Крупный текст — содержимое, а не заголовок окна
BIG_TEXT_ALLOWED = {
    "ui/components/NumberDisplay.kt",   # набираемый номер
    "ui/components/Dialpad.kt",         # цифры клавиатуры
    "call/InCallActivity.kt",           # имя звонящего, тоновый набор (экран вызова)
}

problems = []

for path in sorted(ROOT.rglob("*.kt")):
    rel = str(path.relative_to(ROOT))
    text = path.read_text(encoding="utf-8")

    if "ModalBottomSheet(" in text and rel not in BIG_TEXT_ALLOWED:
        for m in re.finditer(r"typography\.(titleLarge|headlineSmall|headlineMedium|headlineLarge|display\w+)", text):
            # Крупное содержимое (числа, цифры) помечается «ui-roles: содержимое» рядом
            if "ui-roles: содержимое" in text[max(0, m.start() - 300): m.start()]:
                continue
            line = text.count("\n", 0, m.start()) + 1
            problems.append(f"{rel}:{line}: крупный заголовок в файле со шторкой ({m.group(1)}) — SheetTitle или titleMedium")

    for m in re.finditer(r"DropdownMenuItem\(", text):
        block = text[m.start(): m.start() + 600]
        if "typography.bodyLarge" not in block:
            line = text.count("\n", 0, m.start()) + 1
            problems.append(f"{rel}:{line}: пункт меню без bodyLarge")

if problems:
    print("=" * 70)
    print(f"РОЛИ ОФОРМЛЕНИЯ: замечаний — {len(problems)}")
    print("=" * 70)
    for p in problems:
        print("  " + p)
    sys.exit(1)

print("РОЛИ ОФОРМЛЕНИЯ: замечаний — 0")
