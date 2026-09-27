# Быстродействие: что осталось

По убыванию влияния. Первые два пункта, вероятно, объясняют
большую часть того, что вы видите.

---

## 1. Вы тестируете debug-сборку. Это половина проблемы

**Debug-сборка Compose медленнее release в два-пять раз.** Причины:

- композиции не оптимизированы: компилятор Compose в debug оставляет
  проверки и трассировку, которые в release вырезаются;
- нет R8: код не инлайнится, вызовы не devirtualize'ятся;
- **нет предкомпиляции**: методы интерпретируются, пока ART не решит
  их скомпилировать — а решает он по счётчику вызовов, то есть первые
  секунды работы всё идёт интерпретатором;
- включён `StrictMode` (мы сами его включили в debug) — он проверяет
  каждое обращение к диску и сети в главном потоке.

### Что сделать прямо сейчас

```
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Без `keystore.properties` сборка подпишется отладочным ключом — этого
достаточно, чтобы поставить на телефон и оценить скорость.

**Сравните ощущения.** Если разница заметная — дальнейшая оптимизация
debug-сборки смысла не имеет, вы будете чинить то, чего у пользователей
не будет.

---

## 2. Baseline Profile не сгенерирован

### Файл: `app/src/main/baseline-prof.txt`

Сейчас в нём **только комментарии** — 22 строки, ни одного правила.
То есть профиль не работает вовсе.

Baseline Profile — это список методов, которые Android компилирует
заранее при установке, вместо интерпретации при первом запуске.
Для приложения на Compose это **20–30 % к скорости холодного старта
и к плавности первой прокрутки** — той самой, на которой обычно
и замечают рывки.

### Как сгенерировать

Нужно подключённое устройство или эмулятор с root/AOSP-образом:

```
./gradlew :app:generateBaselineProfile
```

Команда запустит `BaselineProfileGenerator` из модуля `:benchmark`,
прогонит сценарии (запуск, прокрутка журнала, открытие клавиатуры)
и запишет реальные правила в файл.

**Файл после этого обязательно закоммитить** — он часть исходников,
а не артефакт сборки.

### Если модуль `:benchmark` отключён

Тогда профиль можно снять вручную на устройстве с Android 9+:

```
adb shell cmd package compile -m speed -f com.ogneliss.dialer
```

Это не заменяет профиль, но даёт понять, сколько именно вы теряете:
если после команды приложение стало заметно живее — профиль нужен.

---

## 3. Три слоя фона в каждой строке

### Файлы: `ui/components/CallLogCard.kt`, `SwipeRevealRow.kt`

Сейчас каждая строка рисует фон трижды:

1. `Card` со своим `containerColor`
2. фон панели свайпа под ним
3. `Row` с фоном отметки выбора (после правки 10)

На слабых видеоядрах это заметно: каждый пиксель закрашивается три раза,
и на списке во весь экран расход утраивается.

### Решение: один фон

```kotlin
    /*
     * ОДИН СЛОЙ ФОНА НА СТРОКУ.
     *
     * Раньше фон рисовали и Card, и панель свайпа, и внутренний Row —
     * каждый пиксель закрашивался трижды. На слабых видеоядрах
     * это заметно на списке во весь экран.
     *
     * Card делаем прозрачной: она нужна только ради формы и отступов,
     * а цвет задаёт содержимое, которое и так знает про отметку выбора.
     */
    Card(
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
```

Проверить результат: **Настройки → Для разработчиков → Отладка
наложения GPU**. Синий — один слой, зелёный — два, розовый — три,
красный — четыре и больше. Список должен быть синим и зелёным.

---

## 4. Якоря свайпа пересоздаются при каждой перерисовке

### Файл: `ui/components/swipe/SwipeRevealRow.kt`

`DraggableAnchors { ... }` строится в теле composable. Объект небольшой,
но создаётся на **каждую строку при каждой перерисовке**, а строк
на экране десяток.

**Найти:**

```kotlin
            anchors = DraggableAnchors {
                RevealState.CLOSED at 0f
                ...
            },
```

**Обернуть:**

```kotlin
    /*
     * Якоря пересчитываются только при изменении того, от чего зависят.
     *
     * Раньше объект создавался в теле composable, то есть на каждую
     * строку при каждой перерисовке. По отдельности дёшево, но при
     * десяти видимых строках и шестидесяти кадрах в секунду —
     * шестьсот созданий в секунду на ровном месте.
     */
    val anchors = remember(startWidth, endWidth, hasStartPanel, hasEndPanel,
        hasStartDirect, hasEndDirect) {
        DraggableAnchors {
            RevealState.CLOSED at 0f
            if (hasStartPanel) RevealState.OPEN_START at startWidth
            if (hasEndPanel) RevealState.OPEN_END at -endWidth
            if (hasStartDirect) RevealState.TRIGGER_START at startWidth
            if (hasEndDirect) RevealState.TRIGGER_END at -endWidth
        }
    }
```

и передать `anchors = anchors`.

---

## 5. Загрузка фотографий без подсказки о размере

### Файл: `OgnelissApp.kt`

Coil декодирует изображение в его исходном разрешении, а показываем мы
его в кружке 48 dp. Для миниатюр из книги разница невелика, но
для контактов с полноразмерным фото — существенная.

**Добавить в `newImageLoader`:**

```kotlin
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCachePolicy(CachePolicy.DISABLED)
        /*
         * Плавное появление отключено.
         *
         * Анимация появления фотографии — лишняя работа во время
         * прокрутки: пока список движется, каждая новая карточка
         * запускает свою анимацию, и они складываются.
         *
         * Для аватаров 48 dp эффект всё равно почти незаметен.
         */
        .crossfade(false)
        .build()
```

### Файл: `ui/components/CallLogCard.kt`

Указать размер явно:

```kotlin
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUri)
                        // Размер задан явно: иначе Coil декодирует
                        // изображение целиком, а показываем мы кружок 48 dp
                        .size(with(LocalDensity.current) { size.roundToPx() })
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(size)
                )
```

---

## 6. Подгрузка проверяется на каждом кадре прокрутки

### Файл: `ui/screens/MainDialerScreen.kt`

**Найти:**

```kotlin
    LaunchedEffect(listState, list.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }.collect { nearEnd -> if (nearEnd && state.query.isEmpty()) vm.loadMore() }
    }
```

`layoutInfo` пересчитывается на каждом кадре, и `snapshotFlow`
выполняет лямбду каждый раз.

**Заменить на:**

```kotlin
    /*
     * Подгрузка отслеживается через derivedStateOf.
     *
     * snapshotFlow над layoutInfo выполняет лямбду на каждом кадре
     * прокрутки. derivedStateOf вычисляет то же самое, но сообщает
     * об изменении только когда меняется РЕЗУЛЬТАТ — то есть один раз
     * при подходе к концу списка, а не шестьдесят раз в секунду.
     */
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.query.isEmpty()) vm.loadMore()
    }
```

То же самое применить к закрытию панели свайпа при прокрутке.

---

## 7. Как перестать гадать и начать измерять

Всё перечисленное — предположения, пока не подтверждено замером.

### Пропущенные кадры

```
adb shell dumpsys gfxinfo com.ogneliss.dialer reset
# прокрутить журнал 5 секунд
adb shell dumpsys gfxinfo com.ogneliss.dialer
```

В выводе интересны `Janky frames` (доля кадров сверх бюджета)
и `50th/90th/95th/99th percentile`. Норма — меньше 5 % janky.

### Что именно тормозит

**Настройки → Для разработчиков → Профилирование отрисовки GPU →
В виде столбиков на экране.** Зелёная линия — бюджет 16 мс.
Цвет столбика говорит о причине:

- **синий** — время на создание списка отрисовки. Много синего
  означает избыточные перерисовки composable
- **красный** — время GPU на сами команды. Много красного означает
  избыточное наложение слоёв (см. пункт 3)
- **оранжевый** — ожидание GPU

### Точный разбор

Android Studio → **Profiler → Track System Traces**, режим
«Jank detection». Показывает конкретные composable, которые
перерисовываются лишний раз.

---

## Порядок действий

1. **Собрать release и сравнить** — возможно, дальше ничего не нужно
2. **Сгенерировать Baseline Profile** — самый большой выигрыш из того,
   что делается один раз
3. Пункты 3–6 — по результатам замера, а не наугад
