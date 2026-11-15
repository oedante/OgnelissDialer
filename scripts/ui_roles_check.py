#!/usr/bin/env python3
"""
ПРОВЕРКА РОЛЕЙ ОФОРМЛЕНИЯ (2.4.0, единый вид).

Автор, 12.11.2026: «во всём приложении в разных окнах, панелях, выпадающих
списках разные размеры шрифтов, акценты, оформление… как будто это не единое
приложение, а сшитое из разных кусков». Правила — docs/OGNELISS_DESIGN_SYSTEM.md,
раздел «Роли текста и значков». Здесь — то, что проверяется по коду:

1. Заголовок окна (шторка ModalBottomSheet) — SheetTitle или titleMedium;
   крупнее (titleLarge, headline*) в файлах со шторками — замечание.
   Исключения — экраны, где крупный текст — содержимое (цифры набора),
   и места с пометкой «ui-roles: содержимое» рядом (число в карточке).
2. Пункт выпадающего меню (DropdownMenuItem) — текст bodyLarge.
3. Отдельное окно — шторка (ModalBottomSheet), диалог (Dialog), Popup,
   выпадающее меню (DropdownMenu) — оборачивает содержимое в ScaledWindow
   или ScaledSheetContent: иначе масштаб интерфейса и «Размер шрифта» в нём
   не действуют (корень окна заново задаёт системную плотность).
4. Готовые окна, в которые масштаб снаружи не передать, — DatePickerDialog,
   TimePickerDialog (Material и системные Android), AlertDialog,
   BasicAlertDialog: рамку и кнопки они рисуют сами, в системной плотности.
   Календарь и часы — PickerDialog, сообщение — OgnelissDialog (2.4.0).
5. Подпись раздела (SectionCaption) — обычным регистром: прописными её
   делает только высококонтрастная тема, внутри самой SectionCaption.
   Вызов с .uppercase() — замечание (2.5.0: «ПАПКИ» и «ИНСТРУМЕНТЫ»
   в меню выглядели жирнее остального приложения — автор заметил).
6. Цвет числом (Color(0x…)) и размер шрифта числом (N.sp) — только в теме
   (ui/theme/): иначе экран выпадает из тем, акцента и «Размера шрифта»
   (2.6.0, обзор вида; было в плане после обзора надёжности 13.11.2026).
   Осознанное исключение помечается рядом «ui-roles: своё» с причиной.
7. Шапка — TopAppBar( (и CenterAligned/Medium/Large) только в ScreenScaffold.kt,
   SectionTopBar.kt, SelectionTopBar.kt; остальные экраны берут ScreenTopBar
   или ScreenScaffold (2.7.0, обзор вида 15.11.2026: шапки были у каждого
   экрана своя).
8. Поле ввода — OutlinedTextField( и TextField( только в OgnelissTextField.kt,
   строке SMS (ConversationScreen.kt) и поиске в шапке настроек
   (SettingsHomeScreen.kt); остальные берут OgnelissTextField (2.7.0: поля
   были двух видов — с тёмной рамкой и без).
9. Кружок выбора — RadioButton( только в строке шторки выбора
   (ui/components/SheetParts.kt, SheetChoiceRow): выбор одного варианта —
   шторкой снизу (2.7.0, решение владельца: «шторка снизу не всегда удобна;
   где используется выбор по долгому нажатию и всплывающая форма (журнал,
   контакты, SMS) — оставить, остальное поменять на шторку»).

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

WINDOW = re.compile(r"(?<![\w.])(ModalBottomSheet|DropdownMenu|Dialog|Popup)\(|androidx\.compose\.ui\.window\.Dialog\(")


def closing_paren(text, start):
    depth = 0
    i = start
    while i < len(text):
        c = text[i]
        if c == '"':
            j = i + 1
            while j < len(text) and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            i = j + 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


for path in sorted(ROOT.rglob("*.kt")):
    rel = str(path.relative_to(ROOT))
    text = path.read_text(encoding="utf-8")
    for m in WINDOW.finditer(text):
        line_start = text.rfind("\n", 0, m.start()) + 1
        prefix = text[line_start:m.start()]
        if "fun " in prefix or "import" in prefix or prefix.strip().startswith(("*", "//")):
            continue
        close = closing_paren(text, text.index("(", m.start()))
        if close < 0:
            continue
        after = text[close + 1: close + 260]
        if "ScaledWindow" not in after and "ScaledSheetContent" not in after:
            line = text.count("\n", 0, m.start()) + 1
            problems.append(f"{rel}:{line}: окно без ScaledWindow/ScaledSheetContent — масштаб в нём не действует")

# Готовые окна со своей рамкой: масштаб в них не действует (правило 4)
FOREIGN_WINDOW = re.compile(r"(?<![\w])(DatePickerDialog|TimePickerDialog|AlertDialog|BasicAlertDialog)\(")

for path in sorted(ROOT.rglob("*.kt")):
    rel = str(path.relative_to(ROOT))
    text = path.read_text(encoding="utf-8")
    for m in FOREIGN_WINDOW.finditer(text):
        line_start = text.rfind("\n", 0, m.start()) + 1
        prefix = text[line_start:m.start()]
        if "fun " in prefix or "import" in prefix or prefix.strip().startswith(("*", "//")):
            continue
        line = text.count("\n", 0, m.start()) + 1
        problems.append(f"{rel}:{line}: {m.group(1)} — масштаб в нём не действует; PickerDialog или OgnelissDialog")

# Подпись раздела — обычным регистром (правило 5)
for path in sorted(ROOT.rglob("*.kt")):
    rel = str(path.relative_to(ROOT))
    text = path.read_text(encoding="utf-8")
    for m in re.finditer(r"(?<![\w])SectionCaption\(", text):
        line_start = text.rfind("\n", 0, m.start()) + 1
        prefix = text[line_start:m.start()]
        if "fun " in prefix or "import" in prefix or prefix.strip().startswith(("*", "//")):
            continue
        close = closing_paren(text, text.index("(", m.start()))
        if close > 0 and ".uppercase(" in text[m.start():close]:
            line = text.count("\n", 0, m.start()) + 1
            problems.append(f"{rel}:{line}: SectionCaption прописными — подпись раздела обычным регистром (прописные — только высококонтрастная тема)")

# Цвета и размеры шрифта числом — только в теме (правило 6)
LITERAL = re.compile(r"Color\(0x[0-9A-Fa-f]+\)|(?<![\w.])\d+(?:\.\d+)?\.sp\b")
for path in sorted(ROOT.rglob("*.kt")):
    rel = str(path.relative_to(ROOT))
    if rel.startswith("ui/theme/"):
        continue
    text = path.read_text(encoding="utf-8")
    for m in LITERAL.finditer(text):
        line_start = text.rfind("\n", 0, m.start()) + 1
        line_end = text.find("\n", m.start())
        line_text = text[line_start:line_end if line_end >= 0 else len(text)]
        if line_text.strip().startswith(("*", "//")) or "lineHeight" in line_text or "letterSpacing" in line_text:
            continue
        if "ui-roles: своё" in text[max(0, m.start() - 400): m.start()]:
            continue
        line = text.count("\n", 0, m.start()) + 1
        problems.append(f"{rel}:{line}: {m.group(0)} — цвет и размер шрифта числом только в теме (ui/theme/) или с пометкой «ui-roles: своё»")

def code_matches(text, pattern):
    """Вхождения pattern вне строк-комментариев, import и объявлений fun."""
    for m in pattern.finditer(text):
        line_start = text.rfind("\n", 0, m.start()) + 1
        prefix = text[line_start:m.start()]
        if "fun " in prefix or "import" in prefix or prefix.strip().startswith(("*", "//", "/*")):
            continue
        yield m


# Шапка — только в общих компонентах (правило 7)
TOP_BAR_ALLOWED = {
    "ui/components/ScreenScaffold.kt",
    "ui/components/SectionTopBar.kt",
    "ui/components/SelectionTopBar.kt",
}
TOP_BAR = re.compile(r"(?<![\w])(?:CenterAligned|Medium|Large)?TopAppBar\(")
# Поле ввода — одно (правило 8)
TEXT_FIELD_ALLOWED = {
    "ui/components/OgnelissTextField.kt",
    "ui/conversation/ConversationScreen.kt",
    "ui/screens/SettingsHomeScreen.kt",
}
TEXT_FIELD = re.compile(r"(?<![\w])(OutlinedTextField|TextField)\(")
# Кружок выбора — только в шторке выбора (правило 9)
RADIO_ALLOWED = {"ui/components/SheetParts.kt"}
RADIO = re.compile(r"(?<![\w])RadioButton\(")

for path in sorted(ROOT.rglob("*.kt")):
    rel = str(path.relative_to(ROOT))
    text = path.read_text(encoding="utf-8")
    if rel not in TOP_BAR_ALLOWED:
        for m in code_matches(text, TOP_BAR):
            line = text.count("\n", 0, m.start()) + 1
            problems.append(f"{rel}:{line}: TopAppBar — шапка только через ScreenTopBar/ScreenScaffold (или SectionTopBar, SelectionTopBar)")
    if rel not in TEXT_FIELD_ALLOWED:
        for m in code_matches(text, TEXT_FIELD):
            line = text.count("\n", 0, m.start()) + 1
            problems.append(f"{rel}:{line}: {m.group(1)} — поле ввода только через OgnelissTextField")
    if rel not in RADIO_ALLOWED:
        for m in code_matches(text, RADIO):
            line = text.count("\n", 0, m.start()) + 1
            problems.append(f"{rel}:{line}: RadioButton — выбор одного варианта шторкой (SingleChoiceSheet, ChoiceSheet + SheetChoiceRow)")

if problems:
    print("=" * 70)
    print(f"РОЛИ ОФОРМЛЕНИЯ: замечаний — {len(problems)}")
    print("=" * 70)
    for p in problems:
        print("  " + p)
    sys.exit(1)

print("РОЛИ ОФОРМЛЕНИЯ: замечаний — 0")
