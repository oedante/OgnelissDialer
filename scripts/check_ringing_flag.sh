#!/usr/bin/env bash
# ПРОВЕРКА ФЛАГА IN_CALL_SERVICE_RINGING.
#
# Флаг означает, что приложение берёт воспроизведение мелодии на себя,
# а система свой звонок подавляет. Флаг БЕЗ собственного проигрывателя
# даёт беззвучные входящие вызовы — и обнаружить это на эмуляторе
# невозможно, там входящих не бывает.
#
# Скрипт валит сборку, если флаг появился, а проигрывателя нет.
set -euo pipefail

MANIFEST="app/src/main/AndroidManifest.xml"
HAS_FLAG=$(grep -c 'IN_CALL_SERVICE_RINGING" android:value="true"' "$MANIFEST" || true)
HAS_PLAYER=$(find app/src/main/java -name "RingtoneController.kt" | wc -l)

if [ "$HAS_FLAG" -gt 0 ] && [ "$HAS_PLAYER" -eq 0 ]; then
  echo "ПРОВАЛ: объявлен IN_CALL_SERVICE_RINGING, но проигрывателя мелодии нет."
  echo "Входящие вызовы будут БЕЗЗВУЧНЫМИ: система свой рингтон подавит,"
  echo "а наш запускать некому."
  echo "Либо уберите флаг, либо реализуйте RingtoneController."
  exit 1
fi

echo "OK: рингтон проигрывает система, флаг не объявлен."
