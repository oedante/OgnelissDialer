# Правка 5: тормоза интерфейса

Три причины, по убыванию влияния.

---

## 5.1. Списки кнопок свайпа строятся заново на каждый кадр

### Файл: `ui/screens/MainDialerScreen.kt`

**Найти** (около строки 336):

```kotlin
                    startActions = if (plan == SwipeActionPlan.Behaviour.REVEAL)
                        vm.revealActionsFor(settings.swipeRight, rightExtra, group, state.sims)
                    else emptyList(),
                    endActions = if (leftPlan == SwipeActionPlan.Behaviour.REVEAL)
                        vm.revealActionsFor(settings.swipeLeft, leftExtra, group, state.sims)
                    else emptyList(),
```

**Это главная причина рывков.** `revealActionsFor` вызывается прямо
в композиции для **каждой** строки: он создаёт список объектов, читает
строки из ресурсов и собирает подписи. При прокрутке это происходит
десятки раз в секунду на каждую видимую карточку.

**Заменить на:**

```kotlin
                    /*
                     * Кнопки свайпа считаются один раз на строку.
                     *
                     * Раньше revealActionsFor вызывался прямо в композиции
                     * для каждой карточки: создание списка объектов
                     * и чтение строк из ресурсов повторялось на каждый
                     * кадр прокрутки. Это и было главной причиной рывков.
                     *
                     * Ключи remember перечислены полностью: набор кнопок
                     * зависит от действия, дополнительных действий,
                     * числа номеров абонента и состава SIM.
                     */
                    startActions = remember(
                        row.key, settings.swipeRight, rightExtra, state.sims
                    ) {
                        if (plan == SwipeActionPlan.Behaviour.REVEAL)
                            vm.revealActionsFor(settings.swipeRight, rightExtra, group, state.sims)
                        else emptyList()
                    },
                    endActions = remember(
                        row.key, settings.swipeLeft, leftExtra, state.sims
                    ) {
                        if (leftPlan == SwipeActionPlan.Behaviour.REVEAL)
                            vm.revealActionsFor(settings.swipeLeft, leftExtra, group, state.sims)
                        else emptyList()
                    },
```

---

## 5.2. Обработчики создаются заново и ломают пропуск перерисовки

### Файл: `ui/screens/MainDialerScreen.kt`

Лямбды `onClick` и `onLongClick` создаются в теле `items` — при каждой
рекомпозиции это **новые объекты**. Compose сравнивает параметры
по равенству, видит изменение и перерисовывает карточку, даже если
данные те же.

**Обернуть в `remember`:**

```kotlin
                val onCardClick = remember(row.key, selection.isActive) {
                    {
                        when {
                            selection.isActive -> selection.toggle(row.key)
                            swipeController.isOpen(row.key) -> swipeController.close()
                            else -> onOpenSubscriber(row.key)
                        }
                    }
                }
                val onCardLongClick = remember(row.key, selection.isActive) {
                    {
                        if (selection.isActive) selection.toggle(row.key)
                        else selectedGroup = group
                    }
                }
```

и передавать их вместо встроенных лямбд.

---

## 5.3. Пересборка строк запускается чаще, чем нужно

### Файл: `ui/screens/MainDialerScreen.kt`

**Найти:**

```kotlin
    LaunchedEffect(list, state.numberInfo, state.notes, state.contacts, state.sims) {
        vm.rebuildRows(list, infoLabels, dateFormat)
    }
```

`state.contacts` — список на сотни объектов. Любое обновление данных
создаёт новый список, и сравнение по равенству перебирает все элементы,
после чего пересобираются все строки.

**Заменить на:**

```kotlin
    /*
     * Пересборка запускается по признакам изменения, а не по самим
     * спискам.
     *
     * Сравнение списка контактов по равенству перебирает все элементы
     * при каждой проверке, а таких проверок на прокрутке много.
     * Размер и время последней записи меняются вместе с содержимым,
     * а сравниваются мгновенно.
     */
    val rowsSignature = remember(
        list.size,
        list.firstOrNull()?.key,
        state.numberInfo.size,
        state.notes.size,
        state.contacts.size,
        state.sims.size
    ) { System.nanoTime() }

    LaunchedEffect(rowsSignature) {
        vm.rebuildRows(list, infoLabels, dateFormat)
    }
```

---

## 5.4. Проверить в первую очередь

Если после этих правок рывки останутся, замерить, что именно тормозит:

```
adb shell dumpsys gfxinfo com.ogneliss.dialer.debug framestats
```

Полезен и режим разработчика: **Настройки → Для разработчиков →
Профилирование отрисовки GPU → В виде столбиков на экране**. Столбики
выше зелёной линии — кадры, не уложившиеся в бюджет.
