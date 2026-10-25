#!/usr/bin/env python3
"""
Проверка контраста палитры (1.4.0).

    python3 scripts/palette_check.py

Читает цвета из app/src/main/java/com/ogneliss/dialer/ui/theme/Color.kt
и считает контраст по формуле WCAG для пар, которые реально встречаются
на экране. Падает, если пара ниже своей нормы:

  текст          ≥ 4,5 : 1  (WCAG AA)
  подсказки      ≥ 4,5 : 1  (outline — «Номер», «Имя» в пустых полях)
  зоны           ≥ 1,08 : 1 (карточка на странице, панель на странице,
                             клавиша на панели — глаз должен различать край)

Значения «было» — для сравнения в выводе; на результат не влияют.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COLOR_KT = ROOT / 'app/src/main/java/com/ogneliss/dialer/ui/theme/Color.kt'

tokens = {name: hexv for name, hexv in re.findall(
    r'internal val (\w+) = (?:androidx\.compose\.ui\.graphics\.)?Color\(0xFF([0-9A-Fa-f]{6})\)',
    COLOR_KT.read_text(encoding='utf-8'))}
tokens['White'] = 'FFFFFF'


def lum(h):
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    c = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def ratio(a, b):
    hi, lo = sorted([lum(tokens[a]), lum(tokens[b])], reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def blend(fg, bg, alpha):
    """Цвет fg с прозрачностью alpha поверх bg — как compositeOver в Compose."""
    f = [int(tokens[fg][i:i + 2], 16) for i in (0, 2, 4)]
    b = [int(tokens[bg][i:i + 2], 16) for i in (0, 2, 4)]
    return ''.join(f'{round(x * alpha + y * (1 - alpha)):02X}' for x, y in zip(f, b))


# 1.7.0, «воздушный» вид: заливка аватара с буквами — контейнер на 60 %
# поверх цвета строки (CallLogCard, AVATAR_FILL_ALPHA); буквы — primary
tokens['AvatarSoftLight'] = blend('Blue90', 'LightCanvas', 0.6)
tokens['AvatarSoftDark'] = blend('Blue30', 'DarkPage', 0.6)

TEXT, ZONE = 4.5, 1.08
PAIRS = [
    # светлая
    ('LightText', 'LightPage', TEXT), ('LightTextVariant', 'LightPage', TEXT),
    ('LightTextVariant', 'LightControl', TEXT), ('LightOutline', 'LightRaised', TEXT),
    ('Blue40', 'LightRaised', TEXT), ('Blue40', 'LightPage', TEXT), ('White', 'Blue40', TEXT),
    ('Blue20', 'Blue90', TEXT), ('LightText', 'LightSelected', TEXT),
    ('Error40', 'LightRaised', TEXT), ('CallGreen', 'LightRaised', TEXT), ('White', 'CallGreen', TEXT),
    # 1.5.5: страница и строки — LightCanvas, полоса раздела — LightPage (без линий)
    ('LightText', 'LightCanvas', TEXT), ('LightTextVariant', 'LightCanvas', TEXT),
    ('LightOutline', 'LightCanvas', TEXT), ('Blue40', 'LightCanvas', TEXT),
    ('LightPage', 'LightCanvas', ZONE), ('LightControl', 'LightCanvas', ZONE),
    ('LightRaised', 'LightControl', ZONE),
    # 1.5.6, как Ogneliss Mail: подпись раздела на полосе настроек, карточка на странице
    ('LightTextVariant', 'LightBand', TEXT), ('LightBand', 'LightPage', 1.05),
    ('LightRaised', 'LightPage', ZONE),
    # тёмная
    ('DarkText', 'DarkCard', TEXT), ('DarkTextVariant', 'DarkCard', TEXT),
    ('DarkTextVariant', 'DarkHighest', TEXT), ('DarkOutline', 'DarkCard', TEXT),
    ('Blue80', 'DarkCard', TEXT), ('Blue20', 'Blue80', TEXT), ('Blue95', 'Blue30', TEXT),
    ('Error80', 'DarkCard', TEXT),
    ('DarkCard', 'DarkPage', ZONE), ('DarkContainer', 'DarkPage', ZONE),
    ('DarkHighest', 'DarkContainer', ZONE),
    # 1.7.0: подпись раздела синим на странице, буквы аватара на светлой заливке,
    # белые клавиши и поля на светлой панели (#F2F5FA)
    ('Blue80', 'DarkPage', TEXT), ('Blue40', 'AvatarSoftLight', TEXT), ('Blue80', 'AvatarSoftDark', TEXT),
]

bad = 0
for a, b, need in PAIRS:
    r = ratio(a, b)
    ok = r >= need
    bad += not ok
    print(f'{"✓" if ok else "✗"} {a:>18} на {b:<14} {r:5.2f} : 1  (норма {need})')

print(f'\nНиже нормы: {bad}')
sys.exit(1 if bad else 0)
