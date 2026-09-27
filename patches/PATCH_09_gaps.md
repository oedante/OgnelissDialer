# Дополнение: три незакрытых места

В правках 4, 8 и 10 не хватало конкретики. Здесь они закрыты полностью.

---

## Правка 4 (закрыта): «Избранные» показывают всех

### Что оказалось на самом деле

Фильтр написан верно, чтение колонки — тоже:

```kotlin
            ContactsContract.CommonDataKinds.Phone.STARRED,   // индекс 4
            ...
            meta.getOrPut(id) { Triple(..., c.getInt(4) == 1) }
```

Проблема в **источнике**. Запрос идёт к таблице `Phone`, то есть
к таблице данных, а `STARRED` там — присоединённое поле из таблицы
контактов. Присоединение выполняет провайдер, и **часть прошивок
делает это неверно**: возвращает единицу для всех строк подряд.

Именно это и даёт «в избранных все контакты».

### Решение: спрашивать избранное у таблицы контактов

### Файл: `data/ContactsRepository.kt`

**Убрать** `STARRED` из списка колонок и сдвинуть индексы:

```kotlin
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,        // 0
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,        // 1
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY, // 2
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,  // 3
            ContactsContract.CommonDataKinds.Phone.NUMBER,            // 4
            ContactsContract.CommonDataKinds.Phone.TYPE,              // 5
            ContactsContract.CommonDataKinds.Phone.LABEL              // 6
        )
```

**Добавить отдельный запрос:**

```kotlin
    /**
     * ИДЕНТИФИКАТОРЫ ИЗБРАННЫХ КОНТАКТОВ.
     *
     * ── Почему отдельным запросом ──────────────────────────────────────────
     * В таблице Phone поле STARRED присоединяется из таблицы контактов,
     * и присоединение выполняет провайдер. Часть прошивок делает это
     * неверно и возвращает единицу для всех строк — тогда «избранными»
     * оказываются все контакты подряд.
     *
     * Прямой запрос к таблице контактов такой неоднозначности не имеет:
     * STARRED там собственное поле, а не результат присоединения.
     *
     * Запрос дешёвый: возвращает только идентификаторы и только
     * отмеченных, то есть обычно единицы записей.
     */
    private fun loadStarredIds(): Set<Long> = runCatching {
        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.STARRED} = 1",
            null,
            null
        )?.use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        ids
    }.getOrElse {
        Logger.w("Contacts", "Не удалось прочитать избранные", it)
        emptySet()
    }
```

**Изменить сборку контактов:**

```kotlin
        val starredIds = loadStarredIds()
        val emails = loadEmails()

        meta.map { (id, m) ->
            Contact(
                id = id,
                lookupKey = lookup[id].orEmpty(),
                displayName = m.first,
                photoUri = m.second,
                // Избранное из таблицы контактов, а не из присоединённого
                // поля таблицы данных — см. пояснение в loadStarredIds
                starred = id in starredIds,
                numbers = byId[id]?.distinctBy { it.e164 } ?: emptyList(),
                emails = emails[id].orEmpty()
            )
        }
```

И поправить чтение в цикле — `Triple` теперь без третьего поля:

```kotlin
                meta.getOrPut(id) { c.getString(2).orEmpty() to c.getString(3) }
                val number = c.getString(4).orEmpty()
                val label = ContactsContract.CommonDataKinds.Phone
                    .getTypeLabel(context.resources, c.getInt(5), c.getString(6)).toString()
```

с соответствующей заменой типа `meta` на `MutableMap<Long, Pair<String, String?>>`
и обращений `m.first` / `m.second`.

### Как проверить, что дело было в этом

Временно вывести число избранных в лог:

```kotlin
        Logger.d("Contacts", "Избранных: ${starredIds.size} из ${meta.size}")
```

Если раньше в списке было столько же, сколько всего контактов, —
причина найдена верно.

---

## Правка 8 (закрыта): передача пиктограммы на месте вызова

В правке 1 описан компонент подсказки и параметры, но не показано,
откуда берутся сами действия.

### Файл: `ui/screens/MainDialerScreen.kt`

**Добавить** рядом с подготовкой строк:

```kotlin
    /*
     * Пиктограммы для подсказки прямого свайпа.
     *
     * Строятся один раз на экран, а не на каждую строку: они одинаковы
     * для всех записей и зависят только от настроек жестов.
     *
     * Берём первое действие из того же набора, что показывается
     * в панели — иначе подсказка обещала бы одно, а срабатывало другое.
     */
    val directStartHint = remember(settings.swipeRight, state.sims) {
        vm.revealActionsFor(settings.swipeRight, emptyList(), null, state.sims)
            .firstOrNull()
    }
    val directEndHint = remember(settings.swipeLeft, state.sims) {
        vm.revealActionsFor(settings.swipeLeft, emptyList(), null, state.sims)
            .firstOrNull()
    }
```

Для этого `revealActionsFor` должен принимать `group` как `CallLogGroup?`
и при `null` строить действие без привязки к номеру — только
пиктограмму и цвет:

### Файл: `ui/DialerViewModel.kt`

```kotlin
    fun revealActionsFor(
        primary: SwipeAction,
        extra: List<SwipeAction>,
        /** null — нужен только вид кнопки, без привязки к абоненту. */
        group: CallLogGroup?,
        sims: List<SimSlot>
    ): List<RevealAction> {
        // Без группы возвращаем «пустое» действие: оно нужно только
        // чтобы показать пиктограмму и цвет под строкой при свайпе
        if (group == null) {
            return listOf(RevealActionFactory.appearanceOnly(primary, revealStrings))
        }
        ...
    }
```

### Файл: `ui/components/swipe/RevealActionFactory.kt`

```kotlin
    /**
     * Действие только для вида: пиктограмма и цвет, без обработчика.
     *
     * Нужно подсказке прямого свайпа — она показывает, что произойдёт,
     * но сама ничего не выполняет.
     */
    fun appearanceOnly(action: SwipeAction, strings: Strings): RevealAction {
        val (icon, color) = iconAndColor(action)
        return RevealAction(
            id = "hint_${action.key}",
            icon = icon,
            label = "",
            description = strings.actionLabel(action),
            background = color,
            onClick = {}
        )
    }
```

**И передать в карточку:**

```kotlin
                CallLogCard(
                    ...
                    directStartAction = directStartHint,
                    directEndAction = directEndHint,
```

### Файл: `ui/components/CallLogCard.kt`

Пробросить дальше в `SwipeRevealRow`:

```kotlin
    directStartAction: RevealAction? = null,
    directEndAction: RevealAction? = null,
```

```kotlin
        SwipeRevealRow(
            ...
            directStartAction = directStartAction,
            directEndAction = directEndAction,
```

---

## Правка 10 (закрыта): проброс признака режима выбора

В правке 6 описана отрисовка галочки, но параметра `selectionActive`
в карточке сейчас нет — он был убран при чистке неиспользуемых
параметров.

### Файл: `ui/components/CallLogCard.kt`

**Найти:**

```kotlin
    selected: Boolean,
```

**Заменить на:**

```kotlin
    selected: Boolean,
    /**
     * Активен ли режим множественного выбора.
     *
     * Нужен отдельно от `selected`: в режиме выбора аватар заменяется
     * кружком-галочкой у ВСЕХ строк, включая неотмеченные. Иначе
     * непонятно, какие строки вообще можно отмечать.
     */
    selectionActive: Boolean = false,
```

### Файл: `ui/screens/MainDialerScreen.kt`

```kotlin
                    selected = selection.isSelected(row.key),
                    selectionActive = selection.isActive,
```

---

## Итоговое покрытие

| № | Пункт | Где закрыт |
|---|---|---|
| 1 | Панель не возвращается | PATCH_01 |
| 2 | Окончание номера на кнопке | PATCH_02 |
| 3 | Тема не сохраняется | PATCH_03 |
| 4 | «Избранные» показывают всех | **PATCH_09** |
| 5 | Тормоза интерфейса | PATCH_05 |
| 6 | Язык не меняется | PATCH_03 |
| 7 | Шторка перекрывает кнопки | PATCH_06 |
| 8 | Нет пиктограммы при свайпе | PATCH_01 + **PATCH_09** |
| 9 | Голосовой ввод | PATCH_06 (удаление) |
| 10 | Выбранные не отмечаются | PATCH_06 + **PATCH_09** |
| 11 | Свайп в контактах | PATCH_04 |
| 12 | Группировка по датам | PATCH_07 |
| 13 | Логотип | PATCH_06 |
| — | Фото в карточках | PATCH_08 |
| — | Звонок без перезапуска | PATCH_08 |
