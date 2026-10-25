#!/usr/bin/env bash
#
# ПРОВЕРКА СИНТАКСИСА KOTLIN БЕЗ GRADLE.
#
# prebuild_check.py ловит пропущенные импорты, несуществующие строки
# и баланс скобок — но не пропущенную запятую: скобок столько же,
# а компилятор споткнётся. Такая запятая однажды почти ушла в архив
# (строка модификаторов нижней панели, 1.2.0).
#
# Скрипт прогоняет все исходники через kotlinc и оставляет только
# СИНТАКСИЧЕСКИЕ ошибки. Ошибки «не найден символ» здесь ожидаемы —
# библиотек Android у kotlinc нет — и отбрасываются.
#
# Нужен kotlinc: переменная KOTLINC или команда в PATH.
# Скачать: github.com/JetBrains/kotlin/releases → kotlin-compiler-*.zip

set -euo pipefail
cd "$(dirname "$0")/.."

KC="${KOTLINC:-$(command -v kotlinc || true)}"
if [ -z "$KC" ]; then
    echo "kotlinc не найден — задайте KOTLINC=/путь/к/kotlinc" >&2
    exit 2
fi

OUT=$(mktemp -d)
FILES=$(find app/src benchmark/src -name "*.kt")
ERRORS=$("$KC" -nowarn $FILES -d "$OUT" 2>&1 | grep -i "syntax error" || true)
rm -rf "$OUT"

if [ -n "$ERRORS" ]; then
    echo "$ERRORS"
    echo
    echo "❌ Синтаксические ошибки: $(echo "$ERRORS" | wc -l)"
    exit 1
fi
echo "✅ Синтаксис Kotlin в порядке ($(echo "$FILES" | wc -l) файлов)"
