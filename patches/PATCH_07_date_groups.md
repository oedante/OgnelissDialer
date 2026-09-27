# Правка 12: группировка журнала по датам

## Новый файл: `data/CallLogDateGroup.kt`

```kotlin
package com.ogneliss.dialer.data

import java.util.Calendar

/**
 * ГРУППИРОВКА ЖУРНАЛА ПО ДАВНОСТИ.
 *
 * Чистая логика без Android — покрыта тестами.
 *
 * ── Зачем ──────────────────────────────────────────────────────────────────
 * Без разделителей журнал читается как сплошная лента: дата у каждой
 * записи есть, но чтобы понять, где кончается сегодняшнее и начинается
 * вчерашнее, приходится вчитываться в каждую строку.
 *
 * Заголовки отвечают на вопрос «когда это было» одним взглядом,
 * не требуя чтения.
 *
 * ── Почему именно такие границы ────────────────────────────────────────────
 * «Сегодня» и «Вчера» — то, чем человек оперирует в разговоре.
 * «На этой неделе» и «На прошлой» — следующий по естественности уровень.
 * Дальше дробить бессмысленно: «три недели назад» и «месяц назад»
 * для памяти одно и то же, поэтому всё остальное сведено в «Раньше».
 *
 * ── Границы календарные, а не по количеству часов ──────────────────────────
 * «Вчера» — это вчерашний календарный день, а не «от 24 до 48 часов
 * назад». Звонок вчера в 23:50 попадает во «Вчера», даже если сейчас
 * час ночи и прошло меньше двух часов.
 */
enum class CallLogDateGroup {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    LAST_WEEK,
    EARLIER;

    companion object {

        /**
         * Группа для времени вызова.
         *
         * @param firstDayOfWeek первый день недели по настройкам системы:
         *        в России неделя начинается с понедельника, в США —
         *        с воскресенья, и «на этой неделе» означает разное
         */
        fun of(
            callMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            firstDayOfWeek: Int = Calendar.MONDAY
        ): CallLogDateGroup {
            val now = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                this.firstDayOfWeek = firstDayOfWeek
            }
            val call = Calendar.getInstance().apply {
                timeInMillis = callMillis
                this.firstDayOfWeek = firstDayOfWeek
            }

            // Будущее означает сбитые часы устройства или записи
            // из другого часового пояса. Показываем как сегодняшние:
            // прятать их в «Раньше» значило бы потерять из виду
            if (callMillis > nowMillis) return TODAY

            val startOfToday = startOfDay(now)
            if (callMillis >= startOfToday) return TODAY

            val startOfYesterday = startOfToday - DAY_MILLIS
            if (callMillis >= startOfYesterday) return YESTERDAY

            val startOfWeek = startOfWeek(now)
            if (callMillis >= startOfWeek) return THIS_WEEK

            val startOfLastWeek = startOfWeek - WEEK_MILLIS
            if (callMillis >= startOfLastWeek) return LAST_WEEK

            return EARLIER
        }

        /**
         * Разбивка списка по группам с сохранением порядка.
         *
         * Возвращает пары «группа — записи»: так интерфейсу не нужно
         * сортировать заново, а пустые группы просто не появляются.
         */
        fun split(
            groups: List<CallLogGroup>,
            nowMillis: Long = System.currentTimeMillis(),
            firstDayOfWeek: Int = Calendar.MONDAY
        ): List<Pair<CallLogDateGroup, List<CallLogGroup>>> {
            if (groups.isEmpty()) return emptyList()

            val result = mutableListOf<Pair<CallLogDateGroup, MutableList<CallLogGroup>>>()

            groups.forEach { group ->
                val bucket = of(group.lastDate, nowMillis, firstDayOfWeek)
                val last = result.lastOrNull()

                // Записи уже отсортированы по убыванию даты, поэтому
                // достаточно сравнить с последней группой: если совпала —
                // дописываем, если нет — начинаем новую
                if (last != null && last.first == bucket) {
                    last.second += group
                } else {
                    result += bucket to mutableListOf(group)
                }
            }

            return result.map { it.first to it.second.toList() }
        }

        private fun startOfDay(calendar: Calendar): Long =
            (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        private fun startOfWeek(calendar: Calendar): Long =
            (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            }.timeInMillis

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val WEEK_MILLIS = 7 * DAY_MILLIS
    }
}
```

## Файл: `ui/screens/MainDialerScreen.kt`

**Заменить** вывод списка на разбитый по группам:

```kotlin
            /*
             * Журнал с заголовками по давности.
             *
             * Заголовки берутся из того же компонента, что буквенные
             * разделители в контактах: одинаковый приём в двух списках
             * не требует привыкания.
             *
             * Разбивка считается один раз при изменении списка,
             * а не в теле items: перебор трёх тысяч записей на каждый
             * кадр прокрутки свёл бы на нет всю работу
             * по производительности.
             */
            val dateGroups = remember(rows) {
                CallLogDateGroup.split(rows.map { it.group })
            }

            dateGroups.forEach { (bucket, groupsInBucket) ->
                item(key = "date_${bucket.name}", contentType = "date_header") {
                    LetterHeader(stringResource(dateGroupLabel(bucket)))
                }

                val rowsInBucket = rows.filter { it.group in groupsInBucket }
                items(rowsInBucket, key = { it.key }, contentType = { "call_log_row" }) { row ->
                    // ... прежнее содержимое карточки
                }
            }
```

Вспомогательная функция там же:

```kotlin
private fun dateGroupLabel(group: CallLogDateGroup): Int = when (group) {
    CallLogDateGroup.TODAY -> R.string.date_group_today
    CallLogDateGroup.YESTERDAY -> R.string.date_group_yesterday
    CallLogDateGroup.THIS_WEEK -> R.string.date_group_this_week
    CallLogDateGroup.LAST_WEEK -> R.string.date_group_last_week
    CallLogDateGroup.EARLIER -> R.string.date_group_earlier
}
```

⚠️ Строка `rows.filter { it.group in groupsInBucket }` перебирает список
на каждую группу. При трёх тысячах записей это заметно. Лучше сразу
разбивать готовые строки, а не группы:

```kotlin
            val dateGroups = remember(rows) {
                val now = System.currentTimeMillis()
                rows.groupBy { CallLogDateGroup.of(it.group.lastDate, now) }
                    .toList()
                    .sortedBy { it.first.ordinal }
            }
```

## Строки

`values/strings.xml`:

```xml
    <string name="date_group_today">Today</string>
    <string name="date_group_yesterday">Yesterday</string>
    <string name="date_group_this_week">This week</string>
    <string name="date_group_last_week">Last week</string>
    <string name="date_group_earlier">Earlier</string>
```

`values-ru/strings.xml`:

```xml
    <string name="date_group_today">Сегодня</string>
    <string name="date_group_yesterday">Вчера</string>
    <string name="date_group_this_week">На этой неделе</string>
    <string name="date_group_last_week">На прошлой неделе</string>
    <string name="date_group_earlier">Раньше</string>
```

`values-de/strings.xml`:

```xml
    <string name="date_group_today">Heute</string>
    <string name="date_group_yesterday">Gestern</string>
    <string name="date_group_this_week">Diese Woche</string>
    <string name="date_group_last_week">Letzte Woche</string>
    <string name="date_group_earlier">Früher</string>
```
