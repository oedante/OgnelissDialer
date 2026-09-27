#!/usr/bin/env bash
# Локальная версия проверки приватности из CI.
# Запуск: ./scripts/check_no_internet.sh
set -euo pipefail

echo "Собираю merged manifest…"
./gradlew processDebugMainManifest -q

MANIFEST=$(find app/build -name AndroidManifest.xml -path "*merged*" | head -1)
if [ -z "$MANIFEST" ]; then
  echo "ОШИБКА: merged manifest не найден"; exit 1
fi

if grep -q 'android.permission.INTERNET' "$MANIFEST"; then
  echo "ПРОВАЛ: в манифест просочилось разрешение INTERNET."
  echo "Скорее всего его добавила новая зависимость. Найти виновника:"
  echo "  ./gradlew app:dependencies"
  echo "Убрать можно так, добавив в AndroidManifest.xml приложения:"
  echo '  <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />'
  exit 1
fi

echo "OK: разрешения INTERNET нет. Обещание приватности в силе."
