#!/usr/bin/env python3
"""
Светлый значок 2.1.0 из тёмного исходника 1.5.5.

СЕЙЧАС НЕ ИСПОЛЬЗУЕТСЯ. Светлый значок был в 2.1.0–2.1.1; 09.11.2026 автор
вернул тёмный («давай вернёмся к предыдущему варианту иконки»), и
store/source/icon_master_1024.png — снова тёмный 1.5.5. Скрипт оставлен,
чтобы светлый вариант можно было собрать заново: он пишет
store/source/icon_master_light_1024.png и эмблему emblem_light_1024.png
и ничего в приложении не трогает. Вернуть светлый в приложение —
скопировать его на место icon_master_1024.png, поменять цвета в
make_icons.py, ic_launcher_background.xml и заставке (как в коммите 2.1.0
83f4e1c) и пересобрать значки разделов в светлом стиле (коммит 1648ab2).

    python3 scripts/make_light_master.py    # store/source/icon_master_light_1024.png

Автор, 08.11.2026: «может, ещё иконку сделаем в светлых тонах? Но лиса
и пламя должны остаться».

══════════════════════════════════════════════════════════════════════════
ЧТО МЕНЯЕТСЯ
══════════════════════════════════════════════════════════════════════════
Было (1.5.5): сине-графитовый фон #3A4450, светло-серая трубка #D3D8DC,
золотое пламя #CCAA6D со светло-серой лисой.
Стало: светлый фон #F2F5FA (страница приложения в светлой теме — заставка
переходит в приложение без шва), трубка — прежним графитом #3A4450.
Пламя и лиса — пиксель в пиксель из исходника: их цвет не трогаем.

══════════════════════════════════════════════════════════════════════════
КАК
══════════════════════════════════════════════════════════════════════════
1. Тёмный исходник раскладывается на долю рисунка и его цвет — тем же
   разбором, что в make_icons.py (поверх фона пиксель восстанавливается
   точно, края не теряют сглаживания).
2. Рисунок — четыре связные области: три части трубки и эмблема (пламя
   с лисой). Эмблема — та, где золото; полупрозрачные края отходят
   к ближайшей области.
3. Трубка перекрашивается в графит; эмблема остаётся как была, кроме двух
   мест, которые на тёмном фоне были не видны, а на светлом видны бы были:
   • тёмная кайма по краю пламени → золото пламени (каждый пиксель эмблемы
     ставится на отрезок «золото → лиса»: других цветов в ней и не было);
   • узкая щель между грудью лисы и низом пламени (на тёмном фоне —
     продолжение тёмного зазора) → закрыта золотом: иначе светлая лиса
     сливалась бы со светлым фоном. Щель закрывается морфологическим
     замыканием в 4× разрешении — край остаётся сглаженным.
4. Всё — поверх нового фона.
"""

from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'store' / 'source' / 'icon_master_1024.png'          # тёмный 1.5.5
OUT = ROOT / 'store' / 'source' / 'icon_master_light_1024.png'
EMBLEM = ROOT / 'store' / 'source' / 'emblem_light_1024.png'

DARK_BG = np.array([58, 68, 80], float)       # #3A4450 — фон тёмного исходника
INKS = np.array([[211, 216, 220],             # #D3D8DC — трубка, лиса
                 [204, 170, 109]], float)     # #CCAA6D — пламя
NOISE = 8.0

LIGHT_BG = np.array([242, 245, 250], float)   # #F2F5FA — новый фон
HANDSET = np.array([58, 68, 80], float)       # #3A4450 — трубка (бывший фон)
GOLD = np.array([204, 170, 109], float)       # #CCAA6D — пламя

# Щель под грудью лисы: где ищем и чем закрываем (пиксели исходника 1024)
GAP_BOX = (440, 520, 560, 660)                # x0, y0, x1, y1
GAP_RADIUS = 20                               # закрывает щель целиком, до низа пламени
SS = 4                                        # сверхвыборка для сглаженного края


def disk(r):
    y, x = np.ogrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


# ── 1. доля рисунка и его цвет (как в make_icons.py) ───────────────────────
P = np.asarray(Image.open(SRC).convert('RGB'), float)
assert P.shape[:2] == (1024, 1024)
D = P - DARK_BG
best_a = np.zeros(P.shape[:2])
best_r = np.full(P.shape[:2], np.inf)
for ink in INKS:
    v = ink - DARK_BG
    a = np.clip((D @ v) / (v @ v), 0, 1)
    r = np.linalg.norm(D - a[..., None] * v, axis=-1)
    take = r < best_r
    best_a[take], best_r[take] = a[take], r[take]
dist = np.linalg.norm(D, axis=-1)
general = np.clip((dist - NOISE) / (60.0 - NOISE), 0, 1)
alpha = np.where(best_r <= 12.0, best_a, general)
need = np.max(np.where(D < 0, -D / DARK_BG, D / (255 - DARK_BG)), axis=-1)
alpha = np.maximum(alpha, need)
alpha[dist < NOISE] = 0.0
alpha[alpha > 0.985] = 1.0
color = np.clip(np.where(alpha[..., None] > 0, DARK_BG + D / np.maximum(alpha, 1e-6)[..., None], 0), 0, 255)

# ── 2. трубка и эмблема ────────────────────────────────────────────────────
R, G, B = P[..., 0], P[..., 1], P[..., 2]
gold_px = (R > 170) & (G > 120) & (G < 205) & (B < 150) & (R - B > 55)
lab, n = ndimage.label(alpha > 0.5, structure=np.ones((3, 3)))
ids = np.arange(1, n + 1)
sizes = ndimage.sum(np.ones_like(alpha), lab, ids)
golds = ndimage.sum(gold_px, lab, ids)
emblem_ids = ids[golds > 500]
big_ids = ids[sizes > 200]
assert len(emblem_ids) == 1 and len(big_ids) == 4, f'ждали трубку из трёх частей и эмблему: {len(big_ids)} областей'
big = np.where(np.isin(lab, big_ids), lab, 0)
_, (iy, ix) = ndimage.distance_transform_edt(big == 0, return_indices=True)
nearest = big[iy, ix]
emblem = (nearest == emblem_ids[0]) & (alpha > 0)
handset = (nearest != emblem_ids[0]) & (alpha > 0)

# ── 3. перекраска ──────────────────────────────────────────────────────────
new = color.copy()
new[handset] = HANDSET
# Эмблема — два цвета исходника: золото пламени и светлый цвет лисы.
# Каждый пиксель — на отрезок «золото → лиса» (доля сохраняется): тёмная
# кайма и тёмные кромки щели уходят в золото, сглаженная граница лисы
# и пламени остаётся
FOX = INKS[0]
v = FOX - GOLD
t = np.clip(((color - GOLD) @ v) / (v @ v), 0, 1)
new[emblem] = (GOLD + t[..., None] * v)[emblem]

# щель под грудью лисы — замыкание силуэта эмблемы в 4× разрешении
x0, y0, x1, y1 = GAP_BOX
pad = GAP_RADIUS * 2
bx0, by0, bx1, by1 = x0 - pad, y0 - pad, x1 + pad, y1 + pad
emb_alpha = np.where(emblem, alpha, 0.0)[by0:by1, bx0:bx1]
up = np.asarray(Image.fromarray(np.uint8(np.round(emb_alpha * 255))).resize(
    ((bx1 - bx0) * SS, (by1 - by0) * SS), Image.BILINEAR), float) / 255 > 0.5
closed = ndimage.binary_closing(up, structure=disk(GAP_RADIUS * SS))
# Покрытие замкнутого силуэта (не только добавки): полупрозрачные края
# бывшей щели оказываются внутри и тоже закрываются — без светлого пунктира
cover = np.asarray(Image.fromarray(np.uint8(closed * 255)).resize(
    (bx1 - bx0, by1 - by0), Image.BOX), float) / 255
added = np.asarray(Image.fromarray(np.uint8((closed & ~up) * 255)).resize(
    (bx1 - bx0, by1 - by0), Image.BOX), float) / 255
box = np.zeros_like(cover)
box[pad:pad + (y1 - y0), pad:pad + (x1 - x0)] = 1
inside = ndimage.binary_dilation(added > 0, iterations=3) * box   # только около щели
sub_a = alpha[by0:by1, bx0:bx1]
sub_c = new[by0:by1, bx0:bx1]
tot = np.where(inside > 0, np.maximum(sub_a, cover), sub_a)
# закрытая щель — часть эмблемы (её отдельный слой — шаг 5)
emblem[by0:by1, bx0:bx1] |= (tot - sub_a) > 0
# Золото — под имеющимся рисунком: лиса и пламя поверх него не меняются
gain = tot - sub_a
sub_c[:] = np.where(tot[..., None] > 0,
                    (sub_c * sub_a[..., None] + GOLD * gain[..., None]) / np.maximum(tot, 1e-6)[..., None], sub_c)
alpha[by0:by1, bx0:bx1] = tot
print(f'щель под лисой закрыта: {gain.sum():.0f} px золота')

# ── 4. на новый фон ────────────────────────────────────────────────────────
out = alpha[..., None] * new + (1 - alpha[..., None]) * LIGHT_BG
Image.fromarray(np.uint8(np.round(np.clip(out, 0, 255)))).save(OUT, optimize=True)
print(f'светлый исходник: {OUT.relative_to(ROOT)}')

# ── 5. эмблема отдельным слоем — для светлых значков «SMS» и «Контакты» ───
# (make_section_icons.py 2.1.1, коммит 1648ab2): пламя с лисой без фона,
# на своём месте в кадре 1024 × 1024
em_a = np.where(emblem, alpha, 0.0)
rgba = np.dstack([np.clip(np.where(em_a[..., None] > 0, new, 0), 0, 255), em_a * 255])
Image.fromarray(np.uint8(np.round(rgba)), 'RGBA').save(EMBLEM, optimize=True)
print(f'эмблема: {EMBLEM.relative_to(ROOT)}')
