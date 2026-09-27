# Правки 3, 6: тема не сохраняется, язык не меняется

## Причина общая

## Файл: `ui/screens/SettingsChoiceDialog.kt`

**Найти** (около строки 84):

```kotlin
    val current = vm.choiceValue(settingKey)
```

Это **обычный вызов функции внутри композиции**, а не чтение состояния.
Compose не знает, что результат зависит от настроек, и не перерисовывает
диалог при их изменении. Значение читается один раз при открытии
и больше не обновляется — отсюда «тема меняется, но отметка стоит
на старой».

**Заменить на:**

```kotlin
    /*
     * Текущее значение читается через состояние настроек.
     *
     * Раньше здесь стоял простой вызов vm.choiceValue(): Compose
     * не видел зависимости от настроек и не перерисовывал диалог
     * после выбора. Отметка оставалась на прежнем пункте, хотя
     * настройка уже применилась.
     */
    val settings by vm.settingsState.collectAsState()
    val current = remember(settings, settingKey) { vm.choiceValue(settingKey) }
```

Добавить импорты:

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
```

---

## Правка 6: язык дополнительно

Тема после этого заработает, а язык — нет. У него вторая причина.

## Файл: `settings/Prefs.kt`

`LocaleManager.current()` читает `AppCompatDelegate`, а это не состояние
Compose: оно не участвует в перерисовке, и его изменение экраном
не отслеживается. Нужно хранить выбор у себя.

**Добавить в `Keys`:**

```kotlin
        val LANGUAGE = stringPreferencesKey("language")
```

**Добавить поток и сеттер:**

```kotlin
    /**
     * Выбранный язык интерфейса.
     *
     * Хранится у нас, а не только в AppCompatDelegate: тот не является
     * состоянием Compose, и экран настроек не узнавал бы об изменении.
     * Пустая строка означает «как в системе».
     */
    val language: Flow<String> = data.map { it[Keys.LANGUAGE].orEmpty() }

    suspend fun setLanguage(tag: String) =
        edit("language", tag) { it[Keys.LANGUAGE] = tag }
```

## Файл: `ui/SettingsState.kt`

**Добавить поле:**

```kotlin
    val language: String = "",
```

и в `switchValue`/`choiceValue` (где сопоставляются ключи) — ничего
менять не нужно, поле читается напрямую.

## Файл: `settings/SettingsStateSource.kt`

Добавить `prefs.language` в группу оформления и передать в `SettingsState`:

```kotlin
    private val appearance: Flow<Appearance> = combine(
        prefs.theme,
        prefs.dynamicColor,
        prefs.largeButtons,
        prefs.showPhoto,
        prefs.language          // ← добавить
    ) { theme, dynamic, large, photo, language ->
        Appearance(theme, dynamic, large, photo, language)
    }
```

Порядок сортировки при этом переносится в другую группу — `combine`
принимает не больше пяти источников. Проще добавить язык в группу
`notes`, где занято два места:

```kotlin
    private val notes: Flow<Notes> = combine(
        prefs.notesRetention,
        prefs.notesRetentionScope,
        prefs.language
    ) { retention, scope, language ->
        Notes(retention, scope, language)
    }
```

## Файл: `settings/SettingsCommands.kt`

**Найти:**

```kotlin
            "language" -> LocaleManager.apply(value)
```

**Заменить на:**

```kotlin
            /*
             * Язык сохраняется И применяется.
             *
             * Раньше вызывался только LocaleManager: система язык меняла,
             * но наше состояние об этом не знало, и переключатель
             * оставался на «Как в системе».
             */
            "language" -> {
                prefs.setLanguage(value)
                LocaleManager.apply(value)
            }
```

**Найти:**

```kotlin
        "language" -> LocaleManager.current()
```

**Заменить на:**

```kotlin
        "language" -> state.language
```

## Файл: `MainActivity.kt`

Подбор языка при первом запуске тоже должен сохраняться:

```kotlin
                LaunchedEffect(consentVersion) {
                    if (consentVersion == 0) {
                        val chosen = LocaleManager.applySystemDefault(this@MainActivity)
                        // Сохраняем, иначе переключатель покажет
                        // «Как в системе» вместо фактического языка
                        vm.prefs.setLanguage(chosen)
                    }
                }
```
