# Правки 7, 9, 10, 13

---

## Правка 7: шторка действий выезжает не полностью

### Файл: `ui/screens/ContactActionSheet.kt`

**Найти:**

```kotlin
    ModalBottomSheet(onDismissRequest = onDismiss) {
```

**Заменить на:**

```kotlin
    /*
     * Шторка раскрывается сразу полностью и учитывает системные панели.
     *
     * По умолчанию ModalBottomSheet открывается наполовину и не знает
     * о навигационной панели: нижние пункты оказываются под кнопками
     * системы, и нажать их нельзя.
     *
     * skipPartiallyExpanded убирает промежуточное состояние — здесь оно
     * бессмысленно, список действий короткий и помещается целиком.
     */
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
```

Добавить импорты:

```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.rememberModalBottomSheetState
```

То же самое применить в `ui/components/FilterSheet.kt`
и `ui/components/NumberPickerSheet.kt`.

---

## Правка 9: голосовой ввод недоступен

Проверка `SpeechRecognizer.isRecognitionAvailable` возвращает `true`,
а само распознавание не работает: приложение Google отвечает
на запрос, но без сети и офлайн-модели языка выдаёт «Голосовой поиск
недоступен».

Проверить заранее, будет ли распознавание работать, **нельзя** —
это состояние чужого приложения.

### Решение: убрать функцию

### Файл: `ui/components/BottomControlPanel.kt`

**Найти:**

```kotlin
        if (voiceAvailable) {
            ActionIcon(Icons.Default.Mic, R.string.voice_search, onVoice)
        } else {
            Spacer(Modifier.size(Dimens.MIN_TOUCH_TARGET))
        }
```

**Заменить на:**

```kotlin
        /*
         * ГОЛОСОВОЙ ВВОД УБРАН.
         *
         * SpeechRecognizer.isRecognitionAvailable отвечает утвердительно,
         * но фактическое распознавание зависит от приложения Google:
         * без сети и без офлайн-модели языка оно показывает «Голосовой
         * поиск недоступен». Проверить это заранее нельзя — состояние
         * чужого приложения нам не видно.
         *
         * Кнопка, ведущая в тупик, хуже отсутствующей: человек нажимает,
         * получает чужое окно с ошибкой и не понимает, что сломалось.
         *
         * Код VoiceInput и VoiceQueryParser оставлен: если появится
         * надёжный способ проверки, вернуть кнопку — вопрос одной строки.
         */
        Spacer(Modifier.size(Dimens.MIN_TOUCH_TARGET))
```

Параметр `voiceAvailable` и `onVoice` можно оставить в сигнатуре —
они не мешают и понадобятся при возврате.

---

## Правка 10: выбранные записи не отмечаются

### Файл: `ui/components/CallLogCard.kt`

Признак `selected` в карточку передаётся, но на вид не влияет.

**Найти:**

```kotlin
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
```

Если строка уже такая, проблема в другом: **`SwipeRevealRow` рисует
своё содержимое поверх фона карточки**, и цвет не виден.

**Добавить внутрь `SwipeRevealRow`, обернув содержимое строки:**

```kotlin
            Row(
                Modifier
                    .fillMaxWidth()
                    /*
                     * Фон отметки задаётся здесь, а не на карточке.
                     *
                     * Карточка лежит под SwipeRevealRow, и его содержимое
                     * перекрывает её цвет: отметка выбора была не видна.
                     */
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .combinedClickableCompat(onClick, onLongClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
```

Дополнительно — явная галочка слева вместо аватара в режиме выбора:

```kotlin
                if (selectionActive) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle
                        else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(appearance.avatarSize)
                    )
                } else {
                    Avatar(row.photoUri, row.hasMissed)
                }
```

Вернуть параметр `selectionActive: Boolean` в сигнатуру `CallLogCard`
и передавать `selection.isActive` из `MainDialerScreen`.

---

## Правка 13: логотип на заставке

Векторная отрисовка логотипа получилась неудачной. Пока проще вернуть
растровое изображение.

### Файл: `app/src/main/res/values/splash.xml`

**Найти:**

```xml
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_logo</item>
```

**Заменить на:**

```xml
        <!--
          Растровый логотип вместо вектора.

          Векторная версия рисовалась вручную и вышла неточной. Растр
          из mipmap-xxxhdpi имеет достаточное разрешение для заставки:
          система масштабирует его под размер экрана, и на телефонах
          это незаметно.

          Когда появится аккуратный вектор, вернуть строку ниже.
        -->
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_foreground</item>
```

Файл `drawable/ic_splash_logo.xml` **удалить** — иначе он останется
мёртвым кодом и будет сбивать с толку при следующем изменении заставки.
