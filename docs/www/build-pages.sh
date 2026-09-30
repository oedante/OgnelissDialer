#!/usr/bin/env bash
#
# СБОРКА СТРАНИЦ САЙТА.
#
# ══════════════════════════════════════════════════════════════════════
# ЧТО ИЗМЕНИЛОСЬ И ПОЧЕМУ
# ══════════════════════════════════════════════════════════════════════
# Раньше скрипт собирал страницы из legal/*.md через pandoc: документы
# живут вместе с приложением, и генерация гарантировала, что копия
# на сайте от них не отстанет.
#
# С появлением четырёх языков это перестало работать. Из одного
# русского markdown нельзя получить страницу с русским, английским,
# немецким и французским блоками и переключателем между ними —
# генератору неоткуда взять три остальных языка.
#
# Поэтому страницы теперь ведутся вручную и лежат рядом с этим
# скриптом, а он только собирает их в каталог для выкладки.
#
# ⚠️ ИЗ ЭТОГО СЛЕДУЕТ ОБЯЗАННОСТЬ. Правя документ в legal/*.md,
# те же изменения нужно внести в соответствующий .html здесь —
# во все языковые блоки. Автоматической сверки больше нет.
#
# ── Использование ──────────────────────────────────────────────────────
#   ./docs/www/build-pages.sh                     # собрать в docs/www/dist
#   ./docs/www/build-pages.sh /var/www/ogneliss   # собрать сразу на сервер

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="${1:-$HERE/dist}"
DIALER="$OUT/dialer"

mkdir -p "$DIALER"

echo "Сборка страниц в $DIALER"
for page in index privacy terms eula licenses; do
    cp "$HERE/$page.html" "$DIALER/$page.html"
    echo "  $page.html"
done

cp "$HERE/style.css" "$DIALER/style.css"
echo "  style.css"

# Переключатель языка, логотип и значки сайта — общие файлы страниц.
# Забыть их значит выложить страницы, которые ссылаются в пустоту:
# вместо списка языков — ничего, вместо логотипа — крестик.
for asset in lang.js lang.css logo.svg favicon.svg favicon-32.png apple-touch-icon.png app-icon.png home.html qr-google-play.svg robots.txt sitemap.xml; do
    cp "$HERE/$asset" "$DIALER/$asset"
    echo "  $asset"
done

# Проверка: у каждой страницы должны быть все четыре языковых блока.
# Забыть один при правке легко, а заметит это только посетитель,
# у которого браузер именно на этом языке.
missing=0
for page in index privacy terms eula licenses; do
    for lang in ru en de fr; do
        if ! grep -q "id=\"lang-$lang\"" "$DIALER/$page.html"; then
            echo "  ⚠️  $page.html: нет блока $lang" >&2
            missing=1
        fi
    done
done

for page in index privacy terms eula licenses; do
    for need in "/dialer/lang.js" "/dialer/favicon.svg"; do
        if ! grep -q "$need" "$DIALER/$page.html"; then
            echo "  ⚠️  $page.html: нет ссылки на $need" >&2
            missing=1
        fi
    done
done

if [ "$missing" -ne 0 ]; then
    echo "Собрано с замечаниями: не все языки на месте." >&2
    exit 1
fi

echo "Готово. Проверить локально:"
echo "  python3 -m http.server 8000 --directory $OUT"
