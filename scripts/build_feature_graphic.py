# -*- coding: utf-8 -*-
"""
СБОРКА БАННЕРА ДЛЯ GOOGLE PLAY.

Баннер 1024×500 без подписи на каком-либо языке: вместо строки
«Быстрый набор. Всё локально.» — шесть пиктограмм основных функций.

Причина: карточка приложения теперь на четырёх языках, а баннер
в Google Play один на все. Русская строка на нём означала, что три
четверти посетителей видят текст, которого не понимают.

Название «Ogneliss Dialer» оставлено: это имя, оно не переводится.

Пиктограммы — из набора Material Symbols (Apache 2.0, Google), того же,
что рисует значки внутри приложения: на баннере и в интерфейсе человек
видит одни и те же фигуры.

Запуск:
    python3 scripts/build_feature_graphic.py
Результат:
    store/play_feature_1024x500.png
"""

import io
import os
import sys

import cairosvg
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from feature_icons import ICONS  # noqa: E402

# ── Размеры и цвета ───────────────────────────────────────────────────────
W, H = 1024, 500

BG = (46, 46, 46)          # тот же тёмный фон, что был у прежнего баннера
WHITE = (255, 255, 255)
ACCENT = (253, 191, 3)     # FDBF03 — фирменный жёлтый из темы приложения
MUTED = (176, 176, 176)

# ── Пиктограммы ───────────────────────────────────────────────────────────
#
# ПЯТЬ, А НЕ ШЕСТЬ.
#
# Шестой (псевдозвонок) убран сознательно. Причины две:
#
# • в списке приложений баннер показывается втрое мельче, и шесть фигур
#   в ряд превращаются в серую кашу. Пять помещаются крупнее;
# • сама фигура «входящий вызов» читается хуже прочих: стрелка рядом
#   с трубкой на мелком масштабе выглядит как обрезанный значок.
#
# Псевдозвонок остаётся в описании — там он объясняется словами,
# а пиктограмма без подписи всё равно не расскажет, что это.
ICON_BOX = 72          # место под фигуру
ICON_HEIGHT = 56       # высота самой фигуры внутри места
ICON_ORDER = ["call", "history", "person", "alarm", "note"]


def render_icon(path_data: str, box: int, height: int, color: tuple) -> Image.Image:
    """
    Пиктограмма из пути Material Symbols, выровненная по оптике.

    ── Зачем обрезка и масштабирование ────────────────────────────────────
    Фигуры набора занимают разную долю своего квадрата: у будильника
    сверху звонки, у человека — воздух по краям. Нарисованные «как есть»
    в одинаковых квадратах, они выглядят разного размера и стоят
    на разной высоте — ряд читается неровным.

    Поэтому каждая фигура обрезается по своим настоящим границам,
    приводится к общей высоте и ставится по центру места. Размер
    сравнивается глазом, а не по невидимому квадрату.

    Рисуется вчетверо крупнее и уменьшается: у cairosvg нет сглаживания
    краёв при малом размере, и без этого шага фигуры получаются рваными.
    """
    rgb = "#%02x%02x%02x" % color
    big = box * 4
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" '
        'viewBox="0 -960 960 960" width="{s}" height="{s}">'
        '<path d="{d}" fill="{c}"/></svg>'
    ).format(s=big, d=path_data, c=rgb)

    png = cairosvg.svg2png(bytestring=svg.encode("utf-8"),
                           output_width=big, output_height=big)
    glyph = Image.open(io.BytesIO(png)).convert("RGBA")

    bbox = glyph.getbbox()
    if bbox:
        glyph = glyph.crop(bbox)

    scale = height / glyph.height
    width = max(1, round(glyph.width * scale))
    glyph = glyph.resize((width, height), Image.LANCZOS)

    canvas = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    canvas.paste(glyph, ((box - width) // 2, (box - height) // 2), glyph)
    return canvas


def load_font(size: int, bold: bool = False):
    """
    Шрифт для названия.

    Берём DejaVu: он есть в системе всегда. Если исчезнет — подпись
    важнее шрифта, поэтому падаем на встроенный, а не роняем сборку.
    """
    names = (
        ["DejaVuSans-Bold.ttf", "DejaVuSans.ttf"] if bold else ["DejaVuSans.ttf"]
    )
    for base in ("/usr/share/fonts/truetype/dejavu/", "/usr/share/fonts/"):
        for name in names:
            path = os.path.join(base, name)
            if os.path.exists(path):
                return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def build(icon_path: str, out_path: str) -> None:
    canvas = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(canvas)

    # ── Значок приложения слева ───────────────────────────────────────────
    #
    # Углы скругляются здесь: исходник 512×512 — полное квадратное
    # изображение, потому что Google Play скругляет его сам на своей
    # странице. На баннере такого скругления не происходит, и квадрат
    # с прямыми углами рядом с круглыми значками остального магазина
    # выглядит чужеродно.
    icon = Image.open(icon_path).convert("RGBA").resize((260, 260), Image.LANCZOS)

    mask = Image.new("L", (260, 260), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 259, 259], radius=58, fill=255)
    icon.putalpha(mask)

    icon_x, icon_y = 64, (H - 260) // 2
    canvas.paste(icon, (icon_x, icon_y), icon)

    text_x = icon_x + 260 + 56

    # ── Название ──────────────────────────────────────────────────────────
    #
    # Кегль подбирается под ширину, а не задаётся числом.
    #
    # Google Play обрезает баннер по краям на части экранов, поэтому
    # до правого края оставляем те же 64 px, что и слева. Прежний
    # заголовок доходил до 995-го пикселя — двадцать девять точек
    # до обрыва.
    title = "Ogneliss Dialer"
    right_margin = 64
    available = W - text_x - right_margin

    title_size = 72
    title_font = load_font(title_size, bold=True)
    while title_size > 40 and draw.textlength(title, font=title_font) > available:
        title_size -= 2
        title_font = load_font(title_size, bold=True)

    title_y = 142
    draw.text((text_x, title_y), title, font=title_font, fill=WHITE)

    # ── Жёлтая черта: та же, что была, — связь с прежним баннером ─────────
    line_y = title_y + 104
    draw.rectangle([text_x, line_y, text_x + 160, line_y + 6], fill=ACCENT)

    # ── Ряд пиктограмм ────────────────────────────────────────────────────
    #
    # Шесть штук в один ряд под чертой. Без подписей: подпись — это снова
    # текст на одном языке, а фигуры читаются всеми.
    #
    # Первая (трубка) жёлтая: это главное, что делает приложение,
    # и глазу нужна точка входа в ряд.
    # Промежуток считается из оставшейся ширины, а не задаётся числом:
    # иначе при смене набора фигур ряд молча уезжает за край
    row_y = line_y + 44
    count = len(ICON_ORDER)
    gap = (available - count * ICON_BOX) // max(1, count - 1)
    gap = max(16, min(gap, 56))

    x = text_x
    for index, key in enumerate(ICON_ORDER):
        color = ACCENT if index == 0 else MUTED
        glyph = render_icon(ICONS[key], ICON_BOX, ICON_HEIGHT, color)
        canvas.paste(glyph, (x, row_y), glyph)
        x += ICON_BOX + gap

    # Проверка на месте, а не в голове: ряд обязан кончиться
    # до правого поля
    last_edge = x - gap
    assert last_edge <= W - right_margin, (
        f"Ряд пиктограмм заходит за поле: {last_edge} > {W - right_margin}"
    )

    canvas.save(out_path, "PNG", optimize=True)
    print("Готово:", out_path, canvas.size)


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    build(
        icon_path=os.path.join(root, "store", "play_icon_512.png"),
        out_path=os.path.join(root, "store", "play_feature_1024x500.png"),
    )
