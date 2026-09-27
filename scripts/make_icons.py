#!/usr/bin/env python3
# Генерация всех размеров значка Ogneliss Dialer из одного исходника.
# Исходник: квадрат 1254x1254 со скруглёнными углами, кремовый фон внутри,
# прозрачные углы.

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage
from pathlib import Path

# Исходник лежит в репозитории: если значок снова поменяется, кладём новый
# файл на его место и запускаем скрипт — все 17 файлов пересобираются разом.
SRC = Path(__file__).resolve().parent.parent / 'store' / 'source' / 'icon_master_1254.png'
ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'app' / 'src' / 'main' / 'res'
STORE = ROOT / 'store'

CREAM = (249, 245, 233)

# ── подготовка исходника ───────────────────────────────────────────────────
src = Image.open(SRC).convert('RGBA')
a = np.array(src)
rgb, al = a[..., :3], a[..., 3]
# внутри картинки альфа 251-252 — доводим до 255, иначе на тёмной заставке
# кремовый выглядит грязным
# Внутри картинки альфа 251-254, по краю скругления — плавный спад
# к нулю, и RGB у этих пикселей тёмный (сглаживание рисовалось по чёрному).
# Если оставить как есть, слой foreground ляжет на фон того же цвета
# с тёмной каймой по краю квадрата — на 48 dp она видна.
# Поэтому: край делаем жёстким (уменьшение в 20 раз всё равно его сгладит),
# а RGB полупрозрачных пикселей заливаем кремовым.
rgb = rgb.copy()
# Фон исходника не идеально ровный: к верхнему краю он светлее кремового
# примерно на 7%. На адаптивном значке фон задан плоским цветом, и этот
# перепад проявился бы светлой каймой по краю слоя foreground.
# Поэтому весь внешний фон (всё, что связано с краем картинки) выравниваем
# в точный #F9F5E9. Лису и блик на трубке это не затрагивает:
# они не связаны с краем и в маску не попадают.
bg_like = (al < 40) | (np.abs(rgb - np.array(CREAM)).max(axis=2) <= 20)
lab, _ = ndimage.label(bg_like)
edge_labels = set(lab[0]) | set(lab[-1]) | set(lab[:, 0]) | set(lab[:, -1])
edge_labels.discard(0)
outer = np.isin(lab, list(edge_labels))
rgb[outer] = CREAM
rgb[al < 250] = CREAM
al = np.where(al >= 128, 255, 0).astype(np.uint8)
src = Image.fromarray(np.dstack([rgb, al]))

DENS = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}


def scaled(size):
    # Каналы уменьшаем раздельно. Pillow при уменьшении RGBA домножает цвет
    # на альфу и делит обратно, и на границе скругления это даёт кольцо
    # звона: цвет там отходит от кремового примерно на 20 единиц,
    # и на значке видна светлая дуга по углам. Отдельно RGB (он в фоне
    # постоянный) и отдельно альфа такого эффекта не дают.
    rgb_i = Image.fromarray(np.array(src)[..., :3]).resize((size, size), Image.LANCZOS)
    a_i = Image.fromarray(np.array(src)[..., 3]).resize((size, size), Image.LANCZOS)
    out = rgb_i.convert('RGBA')
    out.putalpha(a_i)
    return out


def circle_mask(size, ss=4):
    m = Image.new('L', (size * ss, size * ss), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size * ss - 1, size * ss - 1], fill=255)
    return m.resize((size, size), Image.LANCZOS)


# ── 1. foreground для адаптивного значка ───────────────────────────────────
# Сетка 108 dp, видно только центральные 72 dp, любая штатная маска
# вписана в круг радиусом 36 dp. Самая дальняя точка рисунка лежит
# на 0.614 стороны от центра, значит сторона 55 dp даёт радиус 33.8 dp —
# внутри круга с запасом.
SIDE_DP = 55.0
for d, k in DENS.items():
    S = int(round(108 * k))
    inner = int(round(S * SIDE_DP / 108))
    canvas = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    canvas.paste(scaled(inner), ((S - inner) // 2, (S - inner) // 2))
    canvas.save(RES / f'mipmap-{d}' / 'ic_launcher_foreground.png')

# ── 2. запасные растровые значки (используются до API 26) ──────────────────
for d, k in DENS.items():
    S = int(round(48 * k))
    scaled(S).save(RES / f'mipmap-{d}' / 'ic_launcher.png')

    # круглый: кремовый круг, рисунок вписан так, чтобы кончики трубки
    # не срезались краем круга
    inner = int(round(S * 0.765))
    base = Image.new('RGBA', (S, S), CREAM + (255,))
    base.paste(scaled(inner), ((S - inner) // 2, (S - inner) // 2), scaled(inner))
    base.putalpha(circle_mask(S))
    base.save(RES / f'mipmap-{d}' / 'ic_launcher_round.png')

# ── 3. значок для Google Play ──────────────────────────────────────────────
# Play сам скругляет углы, поэтому квадрат заливаем целиком,
# а рисунок держим внутри безопасной зоны.
S = 512
inner = 448
play = Image.new('RGBA', (S, S), CREAM + (255,))
sc = scaled(inner)
play.paste(sc, ((S - inner) // 2, (S - inner) // 2), sc)
play.save(STORE / 'play_icon_512.png')

# ── 4. Feature graphic: меняем только логотип, фон и текст не трогаем ───────
# ВНИМАНИЕ: шаг не идемпотентен — он рисует поверх текущего файла.
# Повторный запуск на уже обновлённом файле безопасен (область логотипа
# заново затирается фоном), но если менялась вёрстка баннера,
# координаты придётся поправить.
fg = Image.open(STORE / 'play_feature_1024x500.png').convert('RGBA')
draw = ImageDraw.Draw(fg)
draw.rectangle([60, 80, 340, 420], fill=(46, 46, 48, 255))  # стираем старый логотип
badge = scaled(260)
fg.paste(badge, (190 - 130, 250 - 130), badge)
fg.convert('RGB').save(STORE / 'play_feature_1024x500.png')

print('растр готов')
