# Разбор ошибок сборки

## `KSP failed with exit code: PROCESSING_ERROR`

```
e: [ksp] The name 'default' cannot be used because it is a Java keyword.
```

**Причина.** Hilt превращает имя `@Provides`-функции в имя генерируемого
Java-метода. Если оно совпадает с ключевым словом Java, генерация падает.

Коварство в том, что **из Kotlin-кода причина не видна**: компилятор
Kotlin не возражает против `fun default()`, потому что в Kotlin это
обычный идентификатор. Ошибка приходит только на этапе генерации,
и сообщение не указывает файл.

**Решение.** Все `@Provides` и `@Binds` функции называть с префиксом
`provide` / `bind`. Это не стиль, а защита: заодно закрывает `native`,
`final`, `static`, `record` и прочие слова, ключевые в Java,
но не в Kotlin.

Проверка добавлена в `scripts/prebuild_check.py` — ловит до сборки.

## Несовпадение версий KSP и Kotlin

```
ksp-2.2.10-2.0.2 is too old for kotlin-2.4.10.
```

**Причина.** KSP выпускается отдельно под каждую версию Kotlin, и его
версия состоит из двух частей: `<версия Kotlin>-<версия KSP>`.

**Решение.** В `gradle/libs.versions.toml` первая часть версии `ksp`
обязана совпадать с версией `kotlin`:

```toml
kotlin = "2.4.10"
ksp = "2.4.10-2.0.2"   # первая часть = версия Kotlin
```

Актуальную пару смотреть на https://github.com/google/ksp/releases.

Это самая частая причина, по которой проект «вдруг перестал собираться
после обновления Kotlin»: версия KSP обновляется отдельно и молча
отстаёт.

## `kotlinOptions is deprecated`

**Причина.** В Kotlin 2.x блок `kotlinOptions` заменён на
`compilerOptions` и в новых версиях даёт ошибку компиляции скрипта.

**Решение.** Вынести за блок `android { }`:

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
```

## `managedDevices.set(...)` и `useConnectedDevices.set(...)`

**Причина.** В расширении `baselineProfile` это не `Property`, а обычные
поля: изменяемая коллекция и булево значение.

**Решение.**
```kotlin
baselineProfile {
    managedDevices += "benchmarkDevice"
    useConnectedDevices = false
}
```

## PowerShell: свойство `-P…` с точками — в кавычках

`./gradlew help -Pandroid.debug.obsoleteApi=true` в PowerShell падает
с «Task '.debug.obsoleteApi=true' not found»: PowerShell режет аргумент
на первой точке. Аргумент `-P…` с точками пишется в кавычках:
`./gradlew help "-Pandroid.debug.obsoleteApi=true"` (11.10.2026).

## Предупреждения AGP 9 про устаревший DSL

Сообщения вида «API 'applicationVariants' is obsolete», «плагин
`org.jetbrains.kotlin.android` больше не требуется», «опция
`android.newDsl=false` устарела» — **предупреждения, а не ошибки**.
Они приходят от плагинов (Hilt, Baseline Profile), а не от нашего кода.

С 1.4.0 флаг `android.newDsl=false` снят (Baseline Profile 1.5.0), и этих
предупреждений быть не должно. Если появятся — команда
`./gradlew help "-Pandroid.debug.obsoleteApi=true"` покажет, какой плагин
зовёт старый API; откат описан в `RELEASE_NOTES_1.4.0.md`, §3.

Пока сборка идёт, трогать не нужно. Миграция на встроенную поддержку
Kotlin в AGP 9 — отдельная задача, и делать её стоит после того,
как Hilt и KSP выпустят совместимые версии.

## Модуль замеров мешает сборке

`:benchmark` нужен только для Baseline Profile и Macrobenchmark.
Если плагин конфликтует с версией AGP, модуль можно временно отключить:

1. Закомментировать `include(":benchmark")` в `settings.gradle.kts`
2. Закомментировать `baselineProfile(project(":benchmark"))`
   и `alias(libs.plugins.baselineprofile)` в `app/build.gradle.kts`

На работу приложения это не влияет — только на скорость холодного старта.

## `resourceConfigurations` в AGP 9

В AGP 9 свойство переименовано. Если появится ошибка, заменить в
`app/build.gradle.kts`:

```kotlin
// Было
resourceConfigurations += listOf("en", "ru")

// Стало
androidResources {
    localeFilters += listOf("en", "ru")
}
```

Смысл тот же: какие локали попадут в сборку. Список обязан совпадать
с `res/xml/locales_config.xml`, иначе выбор языка в приложении
покажет язык, которого в APK нет.

## generateBaselineProfile: «окно другого приложения: android»

```
IllegalStateException: После подготовки вместо журнала открылся окно другого
приложения: android — замер был бы недостоверен.
> Task :benchmark:benchmarkDeviceNonMinifiedReleaseAndroidTest FAILED
```

**Причина (02.11.2026, 1.8.1):** на эмуляторе сборки профиля всплыло
окно «Process system isn't responding» — не отвечала сама система
эмулятора, а не приложение. Сразу после загрузки она ещё занята, графика
у образа `aosp-atd` программная, и на небыстром компьютере система
не успевает. Окно перекрыло журнал. Снимок экрана у такого образа
чёрный — смотреть надо дерево окон `setup-failed-*.xml`
(`benchmark/build/outputs/managed_device_android_test_additional_output/…`):
в нём заголовок окна (`android:id/alertTitle`).

**Что сделано:** подготовка (`DeviceSetup`) и генератор профиля нажимают
«Подождать» в окнах «не отвечает» и запускают приложение заново;
если приложение действительно упало, причина из буфера сбоев эмулятора
попадает в текст ошибки (`setup-failed-*-crash.txt` рядом со снимком).

**Если повторится:** запустить `generateBaselineProfile` ещё раз, ничего
тяжёлого параллельно не держа (браузер, вторая сборка). Выпуск это
не задерживает: `bundleRelease` берёт профиль, который уже лежит
в `app/src/release/generated/baselineProfiles`.

Строки `AssumptionViolatedException` в отчёте того же прогона — не ошибки:
так замеры пропускаются, пока собирается профиль.

## generateBaselineProfile: INSTALL_FAILED_UPDATE_INCOMPATIBLE

```
Existing package com.ogneliss.dialer signatures do not match newer version
> Task :benchmark:connectedNonMinifiedReleaseAndroidTest FAILED
```

**Причина:** во время сборки профиля к компьютеру был подключён телефон
с включённой отладкой по USB. Кроме эмулятора (benchmarkDevice), сборка
попыталась поставить тестовую версию и на телефон — а там стоит версия
из Google Play с другой подписью. Android отказывается ставить приложение
поверх с чужой подписью — и правильно делает.

**Что делать:** отключить телефон (или отладку по USB) на время
`generateBaselineProfile` — профиль собирается на эмуляторе.

⚠️ **Не удалять приложение с телефона**, чтобы «пропустить» установку:
вместе с ним удалятся заметки, отметки, напоминания и настройки.

В `benchmark/build.gradle.kts` стоит `useConnectedDevices = false`, но
плагин Baseline Profile 1.3.3 проверен только до AGP 9.0-alpha01 —
похоже, с AGP 9.3 он эту настройку соблюдает не полностью.
