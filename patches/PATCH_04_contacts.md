# Правки 4, 11: контакты

## Правка 4: в группе «Избранные» все подряд

## Файл: `ui/screens/ContactsScreen.kt`

**Найти** (около строки 95):

```kotlin
    val favourites = remember(state.contacts) { state.contacts.filter { it.starred } }
```

Фильтр верный. Проблема ниже — в том, **что выводится**:

**Найти** (около строки 243):

```kotlin
                if (tab == 0 && search.isBlank() && favourites.isNotEmpty()) {
                    item(key = "favourites_header") {
                        LetterHeader(stringResource(R.string.contacts_favourites))
                    }
                    items(favourites, key = { "fav_${it.id}" }) { contact ->
```

Если здесь всё верно, а на экране видны все контакты — значит,
**`starred` не читается из провайдера**. Проверить в
`ui/screens/ContactsScreen.kt` не получится, смотреть нужно here:

## Файл: `data/ContactsRepository.kt`

**Найти** список колонок запроса и убедиться, что в нём есть:

```kotlin
        ContactsContract.Contacts.STARRED,
```

и что значение читается:

```kotlin
                    starred = cursor.getInt(starredIndex) == 1,
```

**Частая причина:** колонка запрошена, но индекс берётся по неверному
номеру — тогда `starred` получает значение соседнего поля и оказывается
ненулевым почти у всех. Надёжнее брать индекс по имени:

```kotlin
        // Индекс по имени колонки, а не по позиции в массиве:
        // при добавлении колонки в начало списка позиции сдвигаются,
        // и starred начинает читаться из соседнего поля — тогда
        // «избранными» оказываются почти все контакты
        val starredIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
```

---

## Правка 11: правый свайп в контактах не работает

## Файл: `ui/screens/ContactsScreen.kt`

**Найти** (около строки 337):

```kotlin
        onDirectStart = null,
        onDirectEnd = null
```

Панель кнопок передана, но обработчик прямого действия — `null`.
Когда действие одно (обычный случай), панели нет, а прямого действия
не задано: жест не делает ничего.

**Заменить на:**

```kotlin
        /*
         * Прямое действие при одном номере.
         *
         * Раньше здесь стоял null: панель кнопок передавалась, но при
         * единственном номере панели нет, а прямого действия задано
         * не было — жест не делал ничего. У большинства контактов
         * номер один, то есть свайп не работал почти ни у кого.
         */
        onDirectStart = {
            contact.numbers.firstOrNull()?.let { vm.call(it.value) }
        },
        onDirectEnd = {
            contact.numbers.firstOrNull()?.let { vm.openSms(it.value) }
        }
```

И передать пиктограммы для подсказки (см. правку 8):

```kotlin
        directStartAction = callHintAction,
        directEndAction = smsHintAction,
```

где выше по файлу:

```kotlin
    // Пиктограммы подсказки: строятся один раз, а не на каждую строку
    val callHintAction = remember {
        RevealAction("call", Icons.Default.Call, "", "", CallGreen) {}
    }
    val smsHintAction = remember {
        RevealAction("sms", Icons.AutoMirrored.Filled.Message, "", "", NeutralGrey) {}
    }
```
