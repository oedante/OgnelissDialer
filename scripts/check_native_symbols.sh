#!/usr/bin/env bash
#
# ПОПАЛИ ЛИ ОТЛАДОЧНЫЕ СИМВОЛЫ НАТИВНЫХ БИБЛИОТЕК В БАНДЛ
#
# Google Play предупреждает: «App Bundle содержит нативный код,
# рекомендуем загрузить отладочные символы». Настройка для этого в проекте
# есть давно — debugSymbolLevel = "SYMBOL_TABLE" в release. Но работает
# она только при установленном NDK: символы вынимает его инструмент
# objcopy. Без NDK Gradle молча пакует библиотеки как есть, а символов
# в бандле нет — и предупреждение остаётся.
#
# Признак, что NDK не нашёлся, виден в выводе сборки:
#   «Unable to strip the following libraries, packaging them as they are»
#
# Скрипт смотрит в готовый бандл: есть ли там папка с символами.
#
# Использование:
#   ./gradlew bundleRelease
#   ./scripts/check_native_symbols.sh

set -euo pipefail

AAB="${1:-app/build/outputs/bundle/release/app-release.aab}"

if [ ! -f "$AAB" ]; then
    echo "Нет файла $AAB — сначала ./gradlew bundleRelease" >&2
    exit 2
fi

echo "Нативные библиотеки в бандле:"
unzip -l "$AAB" | awk '/\/lib\/.*\.so$/ {print "  " $4}'

SYMBOLS=$(unzip -l "$AAB" | awk '/BUNDLE-METADATA\/com.android.tools.build.debugsymbols\/.*\.(sym|dbg)$/ {print $4}')

if [ -n "$SYMBOLS" ]; then
    echo
    echo "✅ Символы в бандле есть:"
    echo "$SYMBOLS" | sed 's/^/  /'
    echo
    echo "Google Play возьмёт их сам при загрузке — отдельно загружать не нужно."
    exit 0
fi

cat >&2 << 'EOF'

❌ Символов в бандле нет. Две возможные причины:

1. Не установлен NDK — самая частая.
   Android Studio → Settings → Languages & Frameworks → Android SDK →
   вкладка SDK Tools → «NDK (Side by side)» → Apply.
   Если сборка после этого попросит конкретную версию NDK, установите
   именно её: флажок «Show Package Details» внизу той же вкладки.

2. Библиотеки пришли из зависимостей уже без символов.
   Проверить: ./gradlew bundleRelease --info | grep "native debug metadata"
   Строка «...because the native debug metadata has already been stripped»
   значит, что вынимать нечего. Тогда предупреждение Google Play останется,
   и это не ошибка: библиотеки не наши, чинить их мы бы всё равно не стали.
EOF
exit 1
