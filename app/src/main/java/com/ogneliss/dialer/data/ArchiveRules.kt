package com.ogneliss.dialer.data

/**
 * АРХИВ ЖУРНАЛА И SMS (2.1.0) — правила без Android, чтобы их проверяли тесты.
 *
 * Просьба автора (07.11.2026): «В журнале и в SMS долгое нажатие на портрет
 * должно включать режим выбора с соответствующими вариантами (удалить
 * и архивировать)». Было — только удалить (и то лишь в журнале), стало —
 * ещё «В архив»: запись уходит с глаз, но не стирается навсегда.
 *
 * ── SMS ────────────────────────────────────────────────────────────────────
 * Храним ключ переписки (smsPinKey — как у закреплённых) и время архивации.
 * Переписка в архиве, пока её последнее сообщение не новее этого времени:
 * пришло новое — она сама возвращается в общий список, как в Google
 * Messages. Записывать «вернуть» при этом не нужно — это вычисление.
 *
 * ── Журнал ─────────────────────────────────────────────────────────────────
 * Храним id записей системного журнала (CallLog._ID) — как удаление
 * с «Отменить»: по конкретным записям, не по номеру. Карточка скрыта, если
 * в архиве все её записи; новый звонок — новый id, и карточка снова видна.
 */
object ArchiveRules {

    /** Разделитель ключа и времени в строке хранения: «ключ|millis». */
    private const val SEP = '|'

    /** Строки хранения «ключ|millis» → карта. Испорченные строки пропускаются. */
    fun parseSms(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return buildMap {
            raw.split('\n').forEach { line ->
                // lastIndexOf: в буквенном ключе («bank|x») разделитель мог бы встретиться
                val i = line.lastIndexOf(SEP)
                if (i <= 0) return@forEach
                val key = line.substring(0, i).trim()
                val at = line.substring(i + 1).trim().toLongOrNull() ?: return@forEach
                if (key.isNotEmpty()) put(key, at)
            }
        }
    }

    fun formatSms(map: Map<String, Long>): String =
        map.entries.joinToString("\n") { (k, at) -> "$k$SEP$at" }

    /**
     * Переписка в архиве: ключ есть и последнее сообщение не новее
     * времени архивации. Новое сообщение возвращает её без записи.
     */
    fun isSmsArchived(key: String, lastDate: Long, archive: Map<String, Long>): Boolean {
        val at = archive[key] ?: return false
        return lastDate <= at
    }

    /**
     * Время архивации: не раньше последнего сообщения — иначе переписка
     * с датой «из будущего» (часы оператора спешат) не ушла бы в архив.
     */
    fun smsArchiveTime(now: Long, lastDate: Long): Long = maxOf(now, lastDate)

    /** Id записей карточки журнала; без списка записей — последний вызов. */
    fun callIds(group: CallLogGroup): Set<Long> {
        val ids = group.entries.mapTo(HashSet()) { it.id }
        if (group.lastCallId > 0) ids += group.lastCallId
        return ids
    }

    /** Карточка в архиве — все её записи в архиве (новый звонок её возвращает). */
    fun isGroupArchived(group: CallLogGroup, archivedIds: Set<Long>): Boolean {
        if (archivedIds.isEmpty()) return false
        val ids = callIds(group)
        return ids.isNotEmpty() && archivedIds.containsAll(ids)
    }

    /** Ключи карточек в архиве — для отбора строк журнала. */
    fun archivedGroupKeys(groups: List<CallLogGroup>, archivedIds: Set<Long>): Set<String> =
        if (archivedIds.isEmpty()) emptySet()
        else groups.filter { isGroupArchived(it, archivedIds) }.mapTo(HashSet()) { it.key }

    /** Хранение id журнала — через запятую. */
    fun parseCallIds(raw: String?): Set<Long> =
        raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty()

    fun formatCallIds(ids: Set<Long>): String = ids.sorted().joinToString(",")
}
