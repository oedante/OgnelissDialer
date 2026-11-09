#!/usr/bin/env python3
"""
Монохромный слой значка (тематические иконки Android 13+) из того же
исходника, что и остальные размеры (store/source/icon_master_1024.png).

    python3 scripts/make_monochrome.py

Система заливает слой одним цветом под обои. Весь силуэт рисунка дал бы
бесформенное пятно — лиса пропала бы. Поэтому заливаем белую трубку
и оранжевое пламя, а белую лису в пламени оставляем вырезом: за счёт
выреза она читается и в одном цвете. Тёмные зазоры между пламенем
и трубкой — как в исходнике: они и отделяют одно от другого.

Значок 1.4.0 (11.10.2026): тёмный фон, белая трубка, оранжевое пламя
с белой лисой. Прежний значок был кремовым с тёмно-синей трубкой —
скрипт переписан под новые цвета.

Геометрия — та же, что у слоя foreground (make_icons.py): исходник
целиком ложится в видимые 72 dp из 108.
"""

import re
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage
from skimage import measure
from skimage.morphology import convex_hull_image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'store' / 'source' / 'icon_master_1024.png'
OUT = ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable' / 'ic_launcher_monochrome.xml'

GRID = 108.0
SIDE_DP = 72.0          # исходник целиком — видимая часть слоя, как у foreground
TRACE = 360             # разрешение, на котором обводится контур

# Допуск упрощения контура и точность координат: длинный путь разбирается
# при каждой отрисовке значка, а значок рисуется в 48 dp — десятая доля dp
# там меньше десятой доли пикселя. 0.9 (было 1.4): рисунок теперь занимает
# 72 dp вместо 55, и с прежним допуском изгибы трубки шли гранями.
TOLERANCE = 0.9

rgb = np.asarray(Image.open(SRC).convert('RGB')).astype(np.int16)
R, G, B = rgb[..., 0], rgb[..., 1], rgb[..., 2]

# Значок 1.5.5: трубка и лиса светло-серые #D3D8DC, пламя золотое #CCAA6D
# (в 1.4.0 — белые и оранжевое #F7A901, порог синего был < 110)
white = rgb.min(axis=2) > 185
orange = (R > 170) & (G > 120) & (G < 205) & (B < 150) & (R - B > 55)

# Лиса — белая область внутри пламени. Внизу пламени она открыта в тёмный
# зазор, поэтому «заливка дыр» в оранжевом её не находит; признак — попадание
# в выпуклую оболочку пламени. Трубка — остальные крупные белые области:
# середина и два конца, разделённые тёмными линиями.
hull = convex_hull_image(orange)
lab, n = ndimage.label(white)
inside = ndimage.mean(hull, lab, range(1, n + 1))
sizes = ndimage.sum(white, lab, range(1, n + 1))
fox = np.isin(lab, [i + 1 for i in range(n) if inside[i] > 0.9])
handset = np.isin(lab, [i + 1 for i in range(n) if inside[i] <= 0.9 and sizes[i] > 2000])
assert fox.sum() > 5000 and handset.sum() > 50000, (
    f'не нашёл лису ({fox.sum()} px) или трубку ({handset.sum()} px) — другой исходник?')

# Залито: трубка и пламя (с глазом лисы — он оранжевый). Лиса — вырез.
# Полутона на стыке белого с оранжевым ни туда ни сюда — отходят вырезу,
# край выреза ровный
ink = handset | ndimage.binary_closing(orange, np.ones((3, 3)))
ink &= ~fox

# мусор после классификации: одиночные пятна и точечные дырки
lab, n = ndimage.label(ink)
sizes = ndimage.sum(ink, lab, range(1, n + 1))
ink = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s > 60])   # глаз лисы — около 200 px
holes = ndimage.binary_fill_holes(ink) & ~ink
lab, n = ndimage.label(holes)
sizes = ndimage.sum(holes, lab, range(1, n + 1))
ink |= np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s < 400])

# уменьшение с полутонами перед обводкой сглаживает ступеньки контура
small = Image.fromarray((ink * 255).astype('uint8'), 'L').resize((TRACE, TRACE), Image.LANCZOS)
mask = np.pad(np.array(small) > 127, 1)

OFF = (GRID - SIDE_DP) / 2


def to_dp(v):
    return (v - 1) / TRACE * SIDE_DP + OFF


paths = []
for c in measure.find_contours(mask.astype(float), 0.5):
    if len(c) < 12:
        continue
    poly = measure.approximate_polygon(c, tolerance=TOLERANCE)
    if len(poly) < 4:
        continue
    pts = [(to_dp(x), to_dp(y)) for y, x in poly]      # контур идёт как (строка, столбец)
    paths.append('M%.1f,%.1f ' % pts[0] + ' '.join('L%.1f,%.1f' % p for p in pts[1:]) + ' Z')

data = ' '.join(paths)

HEAD = '''<?xml version="1.0" encoding="utf-8"?>
<!--
  Монохромная версия значка для тематических иконок Android 13+.
  Система заливает её одним цветом под обои, поэтому здесь нет ни цветов,
  ни градиентов — только форма.

  ── Что нарисовано (значок 1.4.0, 11.10.2026) ─────────────────────────────
  Залиты трубка и пламя; лиса в пламени — вырез, глаз лисы — точка внутри
  выреза. Тёмные зазоры между пламенем и трубкой сохранены, как в цветном
  значке: без них пламя и трубка слились бы в одно пятно.

  ── evenOdd ────────────────────────────────────────────────────────────────
  Контуры вложены: пламя → вырез лисы → глаз. При заливке evenOdd каждый
  следующий вложенный контур меняет «залито / пусто», и вырез получается
  без отдельных путей.

  Файл собирает scripts/make_monochrome.py из store/source/icon_master_1024.png —
  вручную не править. Геометрия — как у слоя foreground: исходник целиком
  в видимых 72 dp сетки 108 dp.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
'''

OUT.write_text(HEAD + '''    <path
        android:fillColor="#FFFFFF"
        android:fillType="evenOdd"
        android:pathData="%s" />
</vector>
''' % data, encoding='utf-8')

print('контуров:', len(paths), 'символов в pathData:', len(data),
      '| лиса', int(fox.sum()), 'px, трубка', int(handset.sum()), 'px')
