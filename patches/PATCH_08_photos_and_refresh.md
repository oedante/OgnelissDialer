# Правки: фото абонентов и обновление журнала

---

## Проблема 1: фото не появляются в карточках журнала

### Почему в поиске они есть, а в журнале нет

Это разные пути получения фотографии.

**В поиске** карточка контакта берёт `contact.photoUri` напрямую
из телефонной книги — там фото есть всегда.

**В журнале** используется цепочка:

```kotlin
photoUri = group.photoUri ?: group.contactId?.let { contactPhotos[it] }
```

Оба звена ненадёжны:

1. `group.photoUri` — это `CACHED_PHOTO_URI` из журнала вызовов.
   Система заполняет его в момент звонка и **часто оставляет пустым**,
   особенно для старых записей и для контактов, добавленных после звонка.

2. `group.contactId` разбирается из строки:

```kotlin
    private fun parseContactId(lookupUri: String?): Long? {
        if (lookupUri.isNullOrBlank()) return null
        return lookupUri.substringAfterLast('/').toLongOrNull()
    }
```

`CACHED_LOOKUP_URI` имеет вид
`content://com.android.contacts/contacts/lookup/0r1-2A3B/1`, но
**на многих прошивках последний сегмент — это lookup-ключ, а не число**.
`toLongOrNull()` возвращает null, и вся цепочка обрывается.

### Решение: искать фото по номеру, а не по идентификатору

Номер есть всегда — это ключ, который не зависит от того, что система
записала в кэш журнала.

### Файл: `ui/screens/MainDialerScreen.kt`

**Найти:**

```kotlin
    // Фотографии из книги: журнал кэширует их не всегда
    val contactPhotos = remember(state.contacts) {
        state.contacts.mapNotNull { c -> c.photoUri?.let { c.id to it } }.toMap()
    }
```

**Заменить на:**

```kotlin
    /*
     * ФОТОГРАФИИ ПО НОМЕРУ, А НЕ ПО ИДЕНТИФИКАТОРУ КОНТАКТА.
     *
     * Прежняя карта строилась по contactId, а он разбирается из строки
     * CACHED_LOOKUP_URI журнала. На многих прошивках последний сегмент
     * этой строки — lookup-ключ, а не число, разбор возвращает null,
     * и фотография не находится никогда.
     *
     * Номер в формате E.164 есть у каждой записи и не зависит от того,
     * что система записала в кэш журнала.
     */
    val contactPhotos = remember(state.contacts) {
        buildMap<String, String> {
            state.contacts.forEach { contact ->
                val photo = contact.photoUri ?: return@forEach
                contact.numbers.forEach { number -> put(number.e164, photo) }
            }
        }
    }
```

### Файл: `data/CallLogRowMapper.kt`

**Найти в сигнатуре:**

```kotlin
        contactPhotos: Map<Long, String>,
```

**Заменить на:**

```kotlin
        /** Фотографии из книги по номеру в E.164 — см. пояснение в экране. */
        contactPhotos: Map<String, String>,
```

**Найти:**

```kotlin
            photoUri = group.photoUri
                ?: group.contactId?.let { contactPhotos[it] },
```

**Заменить на:**

```kotlin
            /*
             * Фото из книги имеет приоритет над кэшем журнала.
             *
             * Порядок перевёрнут намеренно: кэш журнала мог сохранить
             * старую фотографию, которую человек с тех пор поменял,
             * а книга всегда актуальна. Кэш остаётся запасным вариантом
             * для номеров, которых в книге нет.
             */
            photoUri = contactPhotos[group.e164] ?: group.photoUri,
```

**Найти в `signatureOf`:**

```kotlin
        result = 31 * result + (group.contactId?.let { contactPhotos[it] }?.hashCode() ?: 0)
```

**Заменить на:**

```kotlin
        result = 31 * result + (contactPhotos[group.e164]?.hashCode() ?: 0)
```

### То же в контактах и на экране абонента

Проверить, что `ContactsRepository` действительно читает фото:

```kotlin
        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
```

Именно `PHOTO_THUMBNAIL_URI`, а не `PHOTO_URI`: полноразмерное фото
есть не у всех контактов, а миниатюра создаётся системой всегда.
Если запрашивается только `PHOTO_URI`, у части контактов фото
не будет — и это ровно тот случай, когда «у некоторых есть,
у некоторых нет».

Надёжный вариант — брать первое непустое:

```kotlin
                    // Миниатюра есть всегда, полноразмерное фото — не у всех.
                    // Для списка миниатюры достаточно, и она легче
                    photoUri = cursor.getString(thumbnailIndex)
                        ?: cursor.getString(photoIndex),
```

---

## Проблема 2: новый звонок не появляется до перезапуска

### Почему подписка не помогает

`ContentObserver` регистрируется в `init` ViewModel:

```kotlin
        observer.register()
```

Но внутри `register()` стоит проверка разрешения — и это правильно,
без неё система бросает `SecurityException`. Проблема в последствии:

**если на момент создания ViewModel разрешение ещё не выдано,
подписка не устанавливается, и второй попытки не происходит.**

`onPermissionsChanged()` вызывает `observer.register()` повторно, но
срабатывает он только когда `MainActivity` заметила смену разрешений
через свой флаг. Если мастер разрешений закрылся иначе — например,
пользователь выдал доступ в системных настройках и вернулся, —
флаг не выставится, и подписки не будет до перезапуска.

Плюс есть второй случай, который подпиской не лечится в принципе:
**во время разговора приложение находится в фоне**, и часть прошивок
приостанавливает доставку уведомлений от провайдеров.

### Решение: обновлять при возвращении на передний план

Для диалера это не запасной вариант, а основной: звонок почти всегда
происходит вне списка, и возвращение к нему — естественный момент,
чтобы показать свежие данные.

### Файл: `ui/RootScreen.kt`

**Добавить** внутри `RootScreen`, рядом с другими эффектами:

```kotlin
    /*
     * ОБНОВЛЕНИЕ ПРИ ВОЗВРАЩЕНИИ НА ПЕРЕДНИЙ ПЛАН.
     *
     * Подписки на провайдер недостаточно по двум причинам.
     *
     * Первая: если разрешение выдали позже создания ViewModel,
     * подписка могла не установиться, а повторная попытка происходит
     * не во всех сценариях возврата.
     *
     * Вторая: во время разговора приложение в фоне, и часть прошивок
     * придерживает уведомления от провайдеров до возвращения процесса
     * на передний план.
     *
     * Для диалера возвращение к списку после звонка — самый частый
     * сценарий вообще, и обновление именно в этот момент решает задачу
     * надёжнее любой подписки.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onReturnedToForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
```

Импорты:

```kotlin
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
```

### Файл: `ui/DialerViewModel.kt`

**Добавить метод:**

```kotlin
    /**
     * Возвращение приложения на передний план.
     *
     * Заодно повторяет подписку на провайдеры: если разрешение выдали
     * после создания ViewModel, первая попытка ничего не установила,
     * а без подписки список не обновится и в следующий раз.
     *
     * Повторный register() безопасен — наблюдатель сначала снимает
     * прежнюю подписку.
     */
    fun onReturnedToForeground() {
        runCatching { observer.register() }
            .onFailure { Logger.w(TAG, "Не удалось подписаться на провайдеры", it) }
        refresh()
    }
```

### Файл: `background/ContentChangeObserver.kt`

Убедиться, что повторный вызов не плодит подписки:

```kotlin
    fun register() {
        // Повторный вызов допустим: сначала снимаем прежнюю подписку,
        // иначе при каждом возвращении на передний план добавлялся бы
        // ещё один наблюдатель, и одно изменение журнала вызывало бы
        // несколько перезагрузок подряд
        unregister()

        val cr = context.contentResolver
        // ... остальное без изменений
    }
```

И в `unregister()` — не падать, если подписки не было:

```kotlin
    fun unregister() {
        runCatching { context.contentResolver.unregisterContentObserver(callLogObserver) }
        runCatching { context.contentResolver.unregisterContentObserver(contactsObserver) }
    }
```

---

## Проверка после правок

1. Позвонить с телефона и вернуться в приложение — запись должна
   появиться сразу, без перезапуска.
2. Открыть контакт с фотографией, позвонить ему, вернуться — фото
   должно быть в карточке журнала.
3. Добавить фото контакту, у которого его не было, вернуться
   в приложение — фото должно появиться и в старых записях журнала
   (именно это и проверяет приоритет книги над кэшем).
