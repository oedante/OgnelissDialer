#!/usr/bin/env python3
"""
Пересборка ui/theme/OgnelissIcons.kt из Material Symbols (1.3.4, 04.10.2026).

  python scripts/build_icons.py [--style rounded|outlined|sharp]

Список иконок — scripts/icons_manifest.json:
  "ИмяВКоде": {"symbol": "имя_в_symbols", "filled": false, "autoMirror": false}
Новая иконка: добавить строку в манифест и запустить скрипт. Пути берутся
из github.com/google/material-design-icons (Apache 2.0), нужен интернет.
Залитые варианты в старом формате (24 × 24 без viewBox) учтены.

Пути записываются вызовами PathBuilder (moveTo/lineTo/curveTo…), а не
строкой SVG (05.10.2026): строка разбиралась при первом показе иконки
и замедляла холодный запуск. Перевод в абсолютные координаты — библиотекой
svgpathtools:  pip install svgpathtools
"""
import json, re, sys, urllib.request
from svgpathtools import parse_path, Line, CubicBezier, QuadraticBezier, Arc
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts" / "icons_manifest.json"
OUT = ROOT / "app/src/main/java/com/ogneliss/dialer/ui/theme/OgnelissIcons.kt"
BASE = ("https://raw.githubusercontent.com/google/material-design-icons/master/"
        "symbols/web/{s}/materialsymbols{style}/{s}{fill}_24px.svg")

style = sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "rounded"
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

def num(x):
    s = f"{x:.3f}".rstrip("0").rstrip(".")
    return ("0" if s in ("-0", "") else s) + "f"


def builder_calls(d):
    calls = []
    for sub in parse_path(d).continuous_subpaths():
        st = sub[0].start
        calls.append(f"moveTo({num(st.real)}, {num(st.imag)})")
        for seg in sub:
            e = seg.end
            if isinstance(seg, Line):
                calls.append(f"lineTo({num(e.real)}, {num(e.imag)})")
            elif isinstance(seg, CubicBezier):
                calls.append(f"curveTo({num(seg.control1.real)}, {num(seg.control1.imag)}, "
                             f"{num(seg.control2.real)}, {num(seg.control2.imag)}, {num(e.real)}, {num(e.imag)})")
            elif isinstance(seg, QuadraticBezier):
                calls.append(f"quadTo({num(seg.control.real)}, {num(seg.control.imag)}, {num(e.real)}, {num(e.imag)})")
            elif isinstance(seg, Arc):
                calls.append(f"arcTo({num(seg.radius.real)}, {num(seg.radius.imag)}, {num(seg.rotation)}, "
                             f"{str(seg.large_arc).lower()}, {str(seg.sweep).lower()}, {num(e.real)}, {num(e.imag)})")
        if abs(sub[-1].end - sub[0].start) < 1e-6:
            calls.append("close()")
    return calls


entries = []
for name, m in sorted(manifest.items()):
    url = BASE.format(s=m["symbol"], style=style, fill="_fill1" if m["filled"] else "")
    svg = urllib.request.urlopen(url).read().decode("utf-8")
    path = " ".join(re.findall(r' d="([^"]+)"', svg))
    vb = re.search(r'viewBox="([^"]+)"', svg)
    fn = "rounded" if vb and vb.group(1) == "0 -960 960 960" else "legacy24"
    mirror = ", autoMirror = true" if m["autoMirror"] else ""
    notes = (["залитая"] if m["filled"] else []) + \
        ([f"Symbols: {m['symbol']}"] if re.sub(r"(?<=[a-z])(?=[A-Z0-9])|(?<=[0-9])(?=[A-Z])", "_", name).lower() != m["symbol"] else [])
    comment = f"    // {', '.join(notes)}\n" if notes else ""
    body = "\n".join("            " + c for c in builder_calls(path))
    entries.append(f'{comment}    val {name}: ImageVector by lazy {{\n        {fn}("{name}"{mirror}) {{\n{body}\n        }}\n    }}')
    print(f"  {name:24} ← {m['symbol']}")

text = OUT.read_text(encoding="utf-8")
head = text[: text.index("object OgnelissIcons {") + len("object OgnelissIcons {")]
tail = text[text.index("\n}\n\n/*\n * Пути — вызовами PathBuilder"):]
OUT.write_text(head + "\n\n" + "\n\n".join(entries) + tail, encoding="utf-8")
print(f"Готово: {len(entries)} иконок, стиль {style} → {OUT.relative_to(ROOT)}")
