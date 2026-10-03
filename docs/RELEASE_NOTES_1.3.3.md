# Ogneliss Dialer 1.3.3 — в работе (внутренний тест)

```
versionName  1.3.3
versionCode  31
Предыдущая версия: 1.3.2
```

Фаза «Новые инструменты» плана аудита. Публичного выпуска не будет.
Поведение для пользователя не меняется. Каждый шаг — отдельная сборка
и замер против базовой точки `docs/BASELINE_1.3.2.md`; изменение хуже
порога (5%) без записанной причины — откат шага.

Почему отдельная версия: 1.3.2 ещё не проверена на телефоне. Если
смешать её правки с переходом на новые инструменты, при сбое будет
непонятно, что его вызвало.

## Шаг 1. AGP 9.4.1 и compileSdk 37

**Было:** AGP 9.3.1, compileSdk 36 (app и benchmark).

**Стало:** AGP 9.4.1, compileSdk 37. targetSdk остаётся 36 — это
поведение приложения на телефоне, его повышаем отдельно.

**Зачем:** Compose 1.12 (шаг 3) требует компиляции против API 37;
без этого нельзя убрать принудительный откат Compose до 1.7.6
(`resolutionStrategy` в `app/build.gradle.kts`).

**Что не трогали:** Kotlin 2.2.10, KSP, Hilt, Compose, плагин Baseline
Profile 1.3.3 и `android.newDsl=false`. Плагин стабильных версий 1.4.x
ещё требует `newDsl=false`; освобождение от этого флага появилось
только в 1.5.0-alpha — переходить на альфу не будем, вернёмся к нему
перед AGP 10.

**Возможные сюрпризы при сборке:**
- Android Studio может попросить установить SDK Platform 37 (SDK
  Manager → Android 17, API 37) — это нормально;
- новые предупреждения Lint об устаревших API: сборка их не прерывает
  (`warningsAsErrors = false`), список — в отчёт для шага «Lint».

## Проверка шага 1

```powershell
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew bundleRelease
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ogneliss.benchmark.ScreenBenchmark" "-Pandroid.testInstrumentationRunnerArguments.cardNumber=+7XXXXXXXXXX"
```

На телефоне — короткий прогон: запуск, журнал, карточка, звонок
(входящий и исходящий). Замеры — в таблицу `BASELINE_1.3.2.md`,
колонка «После».
