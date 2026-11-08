#!/usr/bin/env python3
"""
Все размеры значка Ogneliss Dialer из одного исходника.

    python3 scripts/make_light_master.py    # светлый исходник из тёмного (2.1.0)
    python3 scripts/make_icons.py
    python3 scripts/make_monochrome.py      # монохромный слой (Android 13+)
    python3 scripts/build_feature_graphic.py

Исходник — store/source/icon_master_1024.png: квадрат 1024 × 1024 без
прозрачности — светлый фон #F2F5FA, графитовая трубка #3A4450, золотое
пламя #CCAA6D со светлой лисой (значок 2.1.0, 08.11.2026, автор: «в светлых
тонах, но лиса и пламя должны остаться»). Его собирает make_light_master.py
из тёмного значка 1.5.5 (store/source/icon_master_dark_1024.png: фон
#3A4450, светло-серая трубка; композиция 1.4.0). Новый значок — новый файл
на место исходника и команды выше.

Что получается:
  app/src/main/res/mipmap-*/ic_launcher_foreground.png   слой рисунка (адаптивный значок)
  app/src/main/res/mipmap-*/ic_launcher.png              квадрат со скруглением (до API 26)
  app/src/main/res/mipmap-*/ic_launcher_round.png        круг (до API 26)
  store/play_icon_512.png                                Google Play
  docs/www/app-icon.png                                  сайт, 256 px со скруглением
Фон адаптивного значка и заставки — цвет BG в res/values (сверяется ниже).

══════════════════════════════════════════════════════════════════════════
ПОЧЕМУ РИСУНОК ОТДЕЛЯЕТСЯ ОТ ФОНА
══════════════════════════════════════════════════════════════════════════
Адаптивный значок Android — два слоя: фон и рисунок. Лаунчер обрезает
их своей маской (круг, капля, скруглённый квадрат) и двигает рисунок
относительно фона при анимации. Картинка целиком в слое рисунка дала бы
обрезанные углы трубки и «плавающий» квадрат.

Фон исходника ровный (#F2F5FA), рисунок — три чистых цвета: графитовая
трубка #3A4450, золотое пламя #CCAA6D и светлая лиса #D3D8DC. Каждый пиксель
края — смесь фона с одним из них. Для пикселя P и цвета F ищем долю a, при которой
P ≈ a·F + (1 − a)·BG (проекция на отрезок BG → F; выбираем F с меньшей
невязкой). Цвет слоя — BG + (P − BG) / a. Поверх фона BG такой слой
даёт исходный пиксель в точности; поверх другого — чистый край без серой
каймы. Пиксели не с этого отрезка — смесь белого с оранжевым (лиса
в пламени) и тёмно-коричневая кайма пламени — получают долю по
удалённости от фона; поверх BG они тоже восстанавливаются точно.

══════════════════════════════════════════════════════════════════════════
ГЕОМЕТРИЯ
══════════════════════════════════════════════════════════════════════════
Слой 108 dp, видна центральная часть 72 dp, безопасная зона — круг
радиусом 33 dp. Исходник целиком ложится в видимые 72 dp — композиция
та же, что у автора. Самая дальняя точка рисунка (кончик трубки) —
455 px от центра из 512, то есть 32,0 dp: внутри безопасной зоны.
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'store' / 'source' / 'icon_master_1024.png'
RES = ROOT / 'app' / 'src' / 'main' / 'res'
STORE = ROOT / 'store'
WWW = ROOT / 'docs' / 'www'

BG = np.array([242, 245, 250], float)       # #F2F5FA — фон исходника (2.1.0; в 1.5.5 — #3A4450)
INKS = np.array([[58, 68, 80],               # #3A4450 — трубка
                 [204, 170, 109],            # #CCAA6D — пламя
                 [211, 216, 220]], float)    # #D3D8DC — лиса
NOISE = 8.0                                  # шум фона (у светлого исходника фон ровный — запас)

DENS = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
VISIBLE = 72 / 108                           # видимая доля слоя адаптивного значка
CORNER = 0.225                               # скругление — как у значка Mail на сайте


def hexcolor(c):
    return '#%02X%02X%02X' % tuple(int(round(v)) for v in c)


# ── 1. исходник → рисунок с прозрачным фоном ──────────────────────────────
P = np.asarray(Image.open(SRC).convert('RGB'), float)
assert P.shape[:2] == (1024, 1024), f'исходник должен быть 1024 × 1024, а он {P.shape[1]} × {P.shape[0]}'
D = P - BG
best_a = np.zeros(P.shape[:2])
best_r = np.full(P.shape[:2], np.inf)
for ink in INKS:
    v = ink - BG
    a = np.clip((D @ v) / (v @ v), 0, 1)
    r = np.linalg.norm(D - a[..., None] * v, axis=-1)
    take = r < best_r
    best_a[take], best_r[take] = a[take], r[take]

dist = np.linalg.norm(D, axis=-1)
# Пиксель не на отрезке «фон → белый / оранжевый» (невязка > ON_LINE):
# смесь белого с оранжевым внутри рисунка или тёмно-коричневая кайма
# по краю пламени, например (43, 37, 18). Для них доля — по удалённости
# от фона: до FULL — частично, дальше — непрозрачно. Поверх BG пиксель
# восстанавливается точно и так.
ON_LINE, FULL = 12.0, 60.0
general = np.clip((dist - NOISE) / (FULL - NOISE), 0, 1)
alpha = np.where(best_r <= ON_LINE, best_a, general)
# Наименьшая доля, при которой цвет BG + D / a не выходит за 0…255:
# малая доля при оттенке чуть в сторону от линии дала бы обрезку цвета
# и расхождение с исходником
need = np.max(np.where(D < 0, -D / BG, D / (255 - BG)), axis=-1)
alpha = np.maximum(alpha, need)
alpha[dist < NOISE] = 0.0                    # шум фона — фон
alpha[alpha > 0.985] = 1.0
color = np.where(alpha[..., None] > 0, BG + D / np.maximum(alpha, 1e-6)[..., None], 0)
color = np.clip(color, 0, 255)

# Сверка: рисунок поверх BG обязан дать исходник
back = alpha[..., None] * color + (1 - alpha[..., None]) * BG
err = np.abs(back - P).max()
assert err < 8, f'рисунок поверх фона расходится с исходником на {err:.1f}'


def art(size):
    """Рисунок нужного размера. Уменьшение в домноженных на альфу
    каналах: иначе на краю цвет и альфа «разъезжаются» и появляется кайма."""
    pm = Image.fromarray(np.uint8(np.round(color * alpha[..., None])))
    al = Image.fromarray(np.uint8(np.round(alpha * 255)))
    pm = np.asarray(pm.resize((size, size), Image.LANCZOS), float)
    al = np.asarray(al.resize((size, size), Image.LANCZOS), float)
    al = np.clip(al, 0, 255)
    rgb = np.clip(pm / np.maximum(al / 255, 1e-6)[..., None], 0, 255)
    rgb[al == 0] = 0
    return Image.fromarray(np.dstack([rgb, al]).astype(np.uint8), 'RGBA')


def flat(size):
    """Значок целиком — рисунок на фоне BG."""
    out = Image.new('RGBA', (size, size), tuple(int(c) for c in BG) + (255,))
    out.alpha_composite(art(size))
    return out


def mask(size, shape, ss=4):
    m = Image.new('L', (size * ss, size * ss), 0)
    d = ImageDraw.Draw(m)
    if shape == 'circle':
        d.ellipse([0, 0, size * ss - 1, size * ss - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, size * ss - 1, size * ss - 1], radius=CORNER * size * ss, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def masked(size, shape):
    im = flat(size)
    im.putalpha(mask(size, shape))
    return im


# ── 2. слой рисунка адаптивного значка ────────────────────────────────────
for d, k in DENS.items():
    S = int(round(108 * k))
    inner = int(round(S * VISIBLE))
    canvas = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    canvas.alpha_composite(art(inner), ((S - inner) // 2, (S - inner) // 2))
    canvas.save(RES / f'mipmap-{d}' / 'ic_launcher_foreground.png', optimize=True)

# ── 3. запасные растровые значки (до API 26; minSdk 26 — для лаунчеров,
#       которые адаптивные значки не умеют) ─────────────────────────────────
for d, k in DENS.items():
    S = int(round(48 * k))
    masked(S, 'square').save(RES / f'mipmap-{d}' / 'ic_launcher.png', optimize=True)
    # Круг по всему квадрату: кончики трубки — на 0,89 радиуса, не срезаются
    masked(S, 'circle').save(RES / f'mipmap-{d}' / 'ic_launcher_round.png', optimize=True)

# ── 4. Google Play: квадрат целиком, углы скругляет сам магазин ───────────
flat(512).convert('RGB').save(STORE / 'play_icon_512.png', optimize=True)

# ── 5. сайт: 256 px со скруглением (страница показывает 88 и 64 px) ───────
masked(256, 'square').save(WWW / 'app-icon.png', optimize=True)

# ── 6. сверка цветов в ресурсах ───────────────────────────────────────────
want = hexcolor(BG)
for xml, name in [(RES / 'values' / 'ic_launcher_background.xml', 'ic_launcher_background'),
                  (RES / 'values' / 'splash_colors.xml', 'splash_background')]:
    text = xml.read_text(encoding='utf-8')
    assert f'<color name="{name}">{want}</color>' in text, f'{xml.name}: {name} должен быть {want}'

print(f'растр готов: фон {want}, сверка с исходником — расхождение {err:.1f} из 255')
