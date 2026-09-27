# Ogneliss Dialer

Современный номеронабиратель для Android. Kotlin + Jetpack Compose.
**Работает полностью офлайн: у приложения нет разрешения на доступ в интернет.**

## Структура репозитория

```
app/            Исходный код приложения
docs/           Архитектура, производительность, доступность,
                чек-листы соответствия Play, заметки о выпусках,
                дорожная карта
docs/www/       Сайт с документами (ogneliss.ru/dialer): четыре языка
legal/          Политика конфиденциальности, условия, лицензия (RU + EN)
store/          Иконка 512×512, feature graphic, тексты для карточки Play
scripts/        Служебные скрипты (проверка приватности, сборка базы
                нумерации, проверки перед сборкой)
.github/        CI: сборка, тесты, проверки
```

## Сборка
Подробная инструкция: **`docs/BUILD_GUIDE.md`**

## Открыть в Android Studio
1. Android Studio Ladybug (2024.2) или новее
2. `File → Open` → корневая папка проекта
3. Gradle JDK 17 (Settings → Build Tools → Gradle)
4. Запускать на **реальном устройстве**: эмулятор не позволяет полноценно
   проверить роль диалера и телефонию

Gradle wrapper в архив не входит — Android Studio предложит создать его сама,
либо `gradle wrapper --gradle-version 8.9`.

## Проверки перед релизом
```bash
python3 scripts/prebuild_check.py   # статические ошибки сборки
python3 scripts/audit.py            # локализация, мёртвый код, комментарии
./scripts/check_no_internet.sh      # отсутствие разрешения INTERNET
./scripts/check_ringing_flag.sh     # флаг рингтона без проигрывателя
./gradlew testDebugUnitTest         # 229 юнит-тестов
./gradlew lintRelease               # статический анализ Android
```

Полный порядок выпуска: `docs/RELEASE_CHECKLIST.md`.

## Сборка релиза
```bash
# Один раз: создать ключ и заполнить keystore.properties по шаблону
keytool -genkey -v -keystore ogneliss-release.jks \
        -keyalg RSA -keysize 4096 -validity 10000 -alias ogneliss

./gradlew bundleRelease   # App Bundle для Google Play
```

Ключ подписи в репозиторий не попадает. Потеря ключа означает
невозможность выпустить обновление — сохраните его надёжно
и включите Play App Signing при первой публикации.

## Ключевые принципы
- Данные не покидают устройство. Проверяется в CI отсутствием `INTERNET`
- Персональные данные в логе маскируются. Проверяется автотестом
- Ни одной строки интерфейса в коде — только ресурсы
- Ошибка на пути входящего вызова = пропустить звонок, а не заблокировать
