#!/usr/bin/env python3
"""
Значки разделов «SMS» и «Контакты» для главного экрана — в стиле значка
приложения.

    python3 scripts/make_icons.py            # значок приложения (сверяет фон)
    python3 scripts/make_section_icons.py

История:
  • 2.1.1 (09.11.2026, коммит 1648ab2) — автор: «значки SMS и контактов
    не соответствуют основному значку». Были глифы Material на подложке
    значка, стали фигуры с эмблемой бренда — для светлого значка 2.1.0:
    графитом на светлом.
  • 09.11.2026, позже — автор вернул тёмный значок 1.5.5 («давай вернёмся
    к предыдущему варианту иконки. И для SMS и контактов переделай»).
    Значки разделов — в его стиле, тем же приёмом, что у самого значка.

Как у значка приложения (store/source/icon_master_1024.png):
  • фон — сине-графитовый #3A4450 (тот же ресурс ic_launcher_background);
  • крупная фигура — светло-серая #D3D8DC, как трубка;
  • эмблема — пламя с лисой, пиксель в пиксель из значка приложения;
  • вокруг эмблемы — тёмный зазор, как между пламенем и трубкой (там он
    13–17 px при высоте эмблемы 407 px; здесь — та же доля, GAP). Золото
    на светло-сером без зазора сливалось бы. Зазор — вырез в слое рисунка:
    сквозь него виден фон значка, как у трубки.
Фигуры:
  • «SMS» — облачко сообщения, эмблема внутри;
  • «Контакты» — силуэт человека: голова — круг с эмблемой (портрет
    контакта), под ней плечи.

Что получается:
  app/src/main/res/drawable-*dpi/ic_shortcut_sms_fg.png       слой рисунка
  app/src/main/res/drawable-*dpi/ic_shortcut_contacts_fg.png  (адаптивный значок)
  store/source/icon_sms_1024.png, icon_contacts_1024.png      целиком, для проверки глазами

Геометрия — как у слоя значка приложения (make_icons.py): кадр 1024 —
видимые 72 dp слоя 108 dp; все фигуры — внутри безопасного круга
(радиус 33 dp = 469 px от центра), его проверяет assert ниже.
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'store' / 'source'
RES = ROOT / 'app' / 'src' / 'main' / 'res'

BG = (58, 68, 80)             # #3A4450 — фон значка приложения
INK = (211, 216, 220)         # #D3D8DC — светло-серый, как трубка
GOLD = (204, 170, 109)        # #CCAA6D — пламя (по нему эмблема находится в исходнике)
GAP = 15 / 407                # зазор вокруг эмблемы — доля её высоты (как у трубки)
NOISE = 8.0                   # шум фона исходника (как в make_icons.py)
EDGE = 4                      # ширина сглаженного края эмблемы, px исходника
SS = 4                        # сверхвыборка: края фигур без лесенки
SAFE_R = 1024 / 2 * 66 / 72   # безопасный круг адаптивного значка, px кадра
DENS = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
VISIBLE = 72 / 108


def emblem():
    """
    Пламя с лисой из значка приложения: RGBA, обрезано по рисунку.

    Тот же разбор, что в make_icons.py: каждый пиксель — доля рисунка
    поверх фона и цвет рисунка. Поверх фона #3A4450 эмблема даёт исходные
    пиксели в точности — с тёмной каймой пламени и щелью под грудью лисы,
    как на значке. Эмблема — связная область с золотом; полупрозрачные
    края отходят к ближайшей области (трубка рядом — её края не берём).
    """
    P = np.asarray(Image.open(SRC / 'icon_master_1024.png').convert('RGB'), float)
    bg = np.array(BG, float)
    inks = np.array([INK, GOLD], float)
    D = P - bg
    best_a = np.zeros(P.shape[:2])
    best_r = np.full(P.shape[:2], np.inf)
    for ink in inks:
        v = ink - bg
        a = np.clip((D @ v) / (v @ v), 0, 1)
        r = np.linalg.norm(D - a[..., None] * v, axis=-1)
        take = r < best_r
        best_a[take], best_r[take] = a[take], r[take]
    dist = np.linalg.norm(D, axis=-1)
    general = np.clip((dist - NOISE) / (60.0 - NOISE), 0, 1)
    alpha = np.where(best_r <= 12.0, best_a, general)
    need = np.max(np.where(D < 0, -D / bg, D / (255 - bg)), axis=-1)
    alpha = np.maximum(alpha, need)
    alpha[dist < NOISE] = 0.0
    alpha[alpha > 0.985] = 1.0
    color = np.clip(np.where(alpha[..., None] > 0, bg + D / np.maximum(alpha, 1e-6)[..., None], 0), 0, 255)

    R, G, B = P[..., 0], P[..., 1], P[..., 2]
    gold_px = (R > 170) & (G > 120) & (G < 205) & (B < 150) & (R - B > 55)
    lab, n = ndimage.label(alpha > 0.5, structure=np.ones((3, 3)))
    ids = np.arange(1, n + 1)
    sizes = ndimage.sum(np.ones_like(alpha), lab, ids)
    golds = ndimage.sum(gold_px, lab, ids)
    emblem_ids = ids[golds > 500]
    big_ids = ids[sizes > 200]
    assert len(emblem_ids) == 1, f'эмблема не найдена однозначно: {len(emblem_ids)}'
    big = np.where(np.isin(lab, big_ids), lab, 0)
    _, (iy, ix) = ndimage.distance_transform_edt(big == 0, return_indices=True)
    mask = (big[iy, ix] == emblem_ids[0]) & (alpha > 0)

    em_a = np.where(mask, alpha, 0.0)
    # Пылинки шума вокруг пламени (доля рисунка чуть больше нуля) на тёмном
    # фоне не видны, а здесь раздули бы зазор пупырышками: оставляем только
    # край не дальше EDGE px от сплошной эмблемы
    em_a[ndimage.distance_transform_edt(em_a < 0.5) > EDGE] = 0.0
    rgba = np.dstack([np.where(em_a[..., None] > 0, color, 0), em_a * 255])
    im = Image.fromarray(np.uint8(np.round(np.clip(rgba, 0, 255))), 'RGBA')
    ys, xs = np.nonzero(em_a >= 0.05)          # одиночные пылинки края — не рисунок
    return im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def place(canvas, em, cx, cy, height):
    """
    Эмблема высотой height с центром (cx, cy) — в кадре 1024: сначала
    тёмный зазор (вырез в фигуре), потом сама эмблема.
    """
    k = height / em.height
    e = em.resize((round(em.width * k * SS), round(height * SS)), Image.LANCZOS)
    x0 = round(cx * SS - e.width / 2)
    y0 = round(cy * SS - e.height / 2)
    gap = height * GAP * SS
    pad = int(gap) + 2
    shape = np.zeros((e.height + 2 * pad, e.width + 2 * pad), bool)
    # Вырез — от сплошной части эмблемы: сглаженный край (до EDGE px) ложится
    # внутрь зазора, а край выреза остаётся ровным
    shape[pad:pad + e.height, pad:pad + e.width] = np.asarray(e)[..., 3] >= 128
    cut = ndimage.distance_transform_edt(~shape) <= gap
    a = np.asarray(canvas, np.uint8).copy()
    region = a[y0 - pad:y0 - pad + cut.shape[0], x0 - pad:x0 - pad + cut.shape[1], 3]
    region[cut] = 0
    out = Image.fromarray(a, 'RGBA')
    out.alpha_composite(e, (x0, y0))
    return out


def rr(draw, box, r):
    draw.rounded_rectangle([v * SS for v in box], radius=r * SS, fill=INK + (255,))


def check_safe(points):
    for x, y in points:
        d = ((x - 512) ** 2 + (y - 512) ** 2) ** 0.5
        assert d <= SAFE_R, f'точка ({x}, {y}) вне безопасного круга: {d:.0f} > {SAFE_R:.0f}'


def sms(em):
    c = Image.new('RGBA', (1024 * SS, 1024 * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    body = (140, 200, 884, 724)
    rr(d, body, 150)
    # Хвостик облачка — короткий, вниз-влево, как у значков сообщений
    d.polygon([(v[0] * SS, v[1] * SS) for v in [(236, 660), (200, 840), (400, 716)]], fill=INK + (255,))
    # дальние точки: скругления углов и кончик хвостика
    check_safe([(140 + 150 - 106, 200 + 150 - 106), (884 - 150 + 106, 724 - 150 + 106), (200, 840)])
    return place(c, em, 512, 462, 400)


def contacts(em):
    """Человек: голова — круг с эмблемой (портрет контакта), под ней плечи."""
    c = Image.new('RGBA', (1024 * SS, 1024 * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    head = (292, 138, 732, 578)               # круг 440 px
    d.ellipse([v * SS for v in head], fill=INK + (255,))
    # Плечи — купол: верх скруглён, низ прямой; зазор до головы — фон
    shoulders = (214, 632, 810, 1100)
    d.rounded_rectangle([v * SS for v in shoulders], radius=300 * SS, fill=INK + (255,))
    # ниже 880 плечи срезаны: дальше — край безопасного круга
    d.rectangle([0, 880 * SS, 1024 * SS, 1024 * SS], fill=(0, 0, 0, 0))
    check_safe([(512, 138), (214 + 300 - 212, 632 + 300 - 212), (300, 880), (724, 880)])
    return place(c, em, 512, 366, 318)


def finish(name, art4):
    art = art4.resize((1024, 1024), Image.LANCZOS)
    full = Image.new('RGBA', (1024, 1024), BG + (255,))
    full.alpha_composite(art)
    full.convert('RGB').save(SRC / f'icon_{name}_1024.png', optimize=True)
    for dens, k in DENS.items():
        S = int(round(108 * k))
        inner = int(round(S * VISIBLE))
        layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        layer.alpha_composite(art.resize((inner, inner), Image.LANCZOS), ((S - inner) // 2, (S - inner) // 2))
        out = RES / f'drawable-{dens}' / f'ic_shortcut_{name}_fg.png'
        out.parent.mkdir(exist_ok=True)
        layer.save(out, optimize=True)


# Фон в ресурсах — тот же, что здесь (как сверяет make_icons.py)
want = '#%02X%02X%02X' % BG
assert f'<color name="ic_launcher_background">{want}</color>' in \
    (RES / 'values' / 'ic_launcher_background.xml').read_text(encoding='utf-8'), 'фон значка разошёлся с BG'

em = emblem()
finish('sms', sms(em))
finish('contacts', contacts(em))
print('значки разделов готовы: store/source/icon_sms_1024.png, icon_contacts_1024.png')
