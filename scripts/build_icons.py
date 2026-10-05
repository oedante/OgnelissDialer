#!/usr/bin/env python3
"""
Пересборка ui/theme/OgnelissIcons.kt из Material Symbols (1.3.4, 04.10.2026).

  python scripts/build_icons.py [--style rounded|outlined|sharp]

Список иконок — scripts/icons_manifest.json:
  "ИмяВКоде": {"symbol": "имя_в_symbols", "filled": false, "autoMirror": false}
Новая иконка: добавить строку в манифест и запустить скрипт. Пути берутся
из github.com/google/material-design-icons (Apache 2.0), нужен интернет.
Залитые варианты в старом формате (24 × 24 без viewBox) учтены.
"""
import json, re, sys, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts" / "icons_manifest.json"
OUT = ROOT / "app/src/main/java/com/ogneliss/dialer/ui/theme/OgnelissIcons.kt"
BASE = ("https://raw.githubusercontent.com/google/material-design-icons/master/"
        "symbols/web/{s}/materialsymbols{style}/{s}{fill}_24px.svg")

style = sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "rounded"
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

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
    entries.append(f'{comment}    val {name}: ImageVector by lazy {{ {fn}("{name}", "{path}"{mirror}) }}')
    print(f"  {name:24} ← {m['symbol']}")

text = OUT.read_text(encoding="utf-8")
head = text[: text.index("object OgnelissIcons {") + len("object OgnelissIcons {")]
tail = text[text.index("\n}\n\n/** Material Symbols"):]
OUT.write_text(head + "\n\n" + "\n\n".join(entries) + tail, encoding="utf-8")
print(f"Готово: {len(entries)} иконок, стиль {style} → {OUT.relative_to(ROOT)}")
