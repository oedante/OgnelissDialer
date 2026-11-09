#!/usr/bin/env python3
"""
Значки разделов «SMS» и «Контакты» для главного экрана — в стиле значка
приложения (2.1.0).

    python3 scripts/make_light_master.py    # он же кладёт эмблему store/source/emblem_1024.png
    python3 scripts/make_section_icons.py

Автор, 08.11.2026: «значки SMS и контактов не соответствуют основному
значку». Были: глиф Material на подложке значка — чужие рядом с трубкой
с пламенем и лисой. Стали: тот же приём, что у значков Dialer и Mail —
крупная графитовая фигура и эмблема бренда (пламя с лисой):
  • «SMS» — облачко сообщения, эмблема внутри;
  • «Контакты» — силуэт человека: голова — круг с эмблемой (портрет
    контакта), под ней плечи.
Фон — тот же, что у значка приложения (res/values/ic_launcher_background.xml).

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

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'store' / 'source'
RES = ROOT / 'app' / 'src' / 'main' / 'res'

BG = (242, 245, 250)          # #F2F5FA — фон значка приложения
INK = (58, 68, 80)            # #3A4450 — графит, как трубка
SS = 4                        # сверхвыборка: края фигур без лесенки
SAFE_R = 1024 / 2 * 66 / 72   # безопасный круг адаптивного значка, px кадра
DENS = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
VISIBLE = 72 / 108


def emblem():
    """Пламя с лисой из светлого значка: RGBA, обрезано по рисунку."""
    im = Image.open(SRC / 'emblem_1024.png').convert('RGBA')
    a = np.asarray(im)[..., 3]
    ys, xs = np.nonzero(a >= 13)              # одиночные пылинки края — не рисунок
    return im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def place(canvas, em, cx, cy, height):
    """Эмблема высотой height с центром (cx, cy) — в кадре 1024."""
    k = height / em.height
    e = em.resize((round(em.width * k * SS), round(height * SS)), Image.LANCZOS)
    canvas.alpha_composite(e, (round(cx * SS - e.width / 2), round(cy * SS - e.height / 2)))


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
    place(c, em, 512, 462, 400)
    return c


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
    place(c, em, 512, 366, 318)
    return c


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
