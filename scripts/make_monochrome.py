#!/usr/bin/env python3
# Сборка монохромного слоя значка (тематические иконки Android 13+)
# из того же исходника, что и остальные размеры.
#
# Идея: система заливает слой одним цветом. Если взять весь силуэт рисунка,
# получится бесформенное пятно — лиса пропадает. Поэтому заливаем только
# оранжевое и тёмно-синее, а кремовое (морда лисы, блик на трубке)
# остаётся вырезом. За счёт выреза лиса читается и в одном цвете.

import re
import numpy as np
from PIL import Image
from scipy import ndimage
from skimage import measure
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'store' / 'source' / 'icon_master_1254.png'
OUT = ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable' / 'ic_launcher_monochrome.xml'

SIDE_DP = 55.0          # тот же размер, что у слоя foreground
GRID = 108.0
TRACE = 360             # разрешение, на котором обводится контур

# Допуск упрощения контура и точность координат.
#
# Android Studio предупреждает о длинных путях: разбор такого пути при
# каждой отрисовке стоит времени, а значок рисуется на каждом кадре
# списка приложений. Первый вариант дал 3844 символа при допуске 0.7
# и двух знаках после запятой.
#
# 1.4 и один знак дают втрое короче. Разница видна только при увеличении
# в несколько раз: значок рисуется в 48 dp, и полдесятой доли dp там
# меньше десятой доли пикселя.
TOLERANCE = 1.4

a = np.array(Image.open(SRC).convert('RGBA'))
rgb, al = a[..., :3].astype(np.int16), a[..., 3]
opaque = al > 200

navy = opaque & (rgb[..., 2] > rgb[..., 0]) & (rgb.sum(axis=2) < 400)
orange = opaque & (rgb[..., 0] > 200) & (rgb[..., 2] < 150)
ink = ndimage.binary_closing(navy | orange, np.ones((5, 5)))

# мусор после классификации: одиночные пятна и точечные дырки
lab, n = ndimage.label(ink)
sizes = ndimage.sum(ink, lab, range(1, n + 1))
ink = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s > 2000])
holes = ndimage.binary_fill_holes(ink) & ~ink
lab, n = ndimage.label(holes)
sizes = ndimage.sum(holes, lab, range(1, n + 1))
ink |= np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s < 1500])

# уменьшение с полутонами перед обводкой сглаживает ступеньки контура
small = Image.fromarray((ink * 255).astype('uint8'), 'L').resize((TRACE, TRACE), Image.LANCZOS)
mask = np.pad(np.array(small) > 127, 1)

OFF = (GRID - SIDE_DP) / 2


def to_dp(v):
    return (v - 1) / TRACE * SIDE_DP + OFF


paths = []
for c in measure.find_contours(mask.astype(float), 0.5):
    if len(c) < 30:
        continue
    poly = measure.approximate_polygon(c, tolerance=TOLERANCE)
    if len(poly) < 4:
        continue
    pts = [(to_dp(x), to_dp(y)) for y, x in poly]      # контур идёт как (строка, столбец)
    paths.append('M%.1f,%.1f ' % pts[0] + ' '.join('L%.1f,%.1f' % p for p in pts[1:]) + ' Z')

data = ' '.join(paths)

# Комментарий в готовом файле сохраняем: он объясняет evenOdd и геометрию.
head = OUT.read_text(encoding='utf-8')
m = re.search(r'^(.*?)(    <path)', head, re.S) if OUT.exists() else None
prefix = m.group(1) if m else ''
OUT.write_text(prefix + '''    <path
        android:fillColor="#FFFFFF"
        android:fillType="evenOdd"
        android:pathData="%s" />
</vector>
''' % data, encoding='utf-8')

print('контуров:', len(paths), 'символов в pathData:', len(data))
