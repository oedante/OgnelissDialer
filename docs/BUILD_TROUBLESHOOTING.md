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

## Предупреждения AGP 9 про устаревший DSL

Сообщения вида «API 'applicationVariants' is obsolete», «плагин
`org.jetbrains.kotlin.android` больше не требуется», «опция
`android.newDsl=false` устарела» — **предупреждения, а не ошибки**.
Они приходят от плагинов (Hilt, Baseline Profile), а не от нашего кода.

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
