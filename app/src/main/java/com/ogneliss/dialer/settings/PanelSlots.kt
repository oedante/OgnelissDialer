package com.ogneliss.dialer.settings

/**
 * КОНСТРУКТОР НИЖНЕЙ ПАНЕЛИ (2.9.2, автор 23.11.2026).
 *
 * «Нижняя панель — вариант А. При первом запуске спрашиваем про установку
 * приложения СМС по умолчанию, если да, то порядок: журнал звонков, журнал смс,
 * клавиатура, контакты, меню. Если пользователь даёт права только на диалер,
 * то: журнал звонков, контакты, клавиатура, заметки, меню… Дополнительно можно,
 * как в Ogneliss Mail, сделать конструктор нижней панели: журнал звонков всегда
 * на панели, но можно поменять положение, клавиатура всегда на панели по центру.
 * Контакты, смс, меню, заметки, напоминания можно добавить или убрать из нижней
 * панели, но они должны быть в меню».
 *
 * ── Как устроено ───────────────────────────────────────────────────────────
 * Четыре места: два слева и два справа от центральной кнопки (её на панели
 * не выбирают — она всегда в центре). Как в «Нижней панели» почты
 * (phone_bar_section.dart): нажал место — выбрал раздел; раздел, который уже
 * на панели, меняется местами; «Журнал» можно только переставить.
 *
 * Пока человек ничего не менял, порядок — по роли SMS ([defaults]): приложение
 * SMS по умолчанию — «SMS» на втором месте, иначе — «Контакты», а справа
 * «Заметки». Изменил — сохраняется строкой «JOURNAL,CONTACTS,NOTES,MENU»
 * и больше от роли не зависит.
 *
 * Чистый Kotlin — PanelSlotsTest.
 */
enum class PanelSlot { JOURNAL, MESSAGES, CONTACTS, NOTES, REMINDERS, MENU }

object PanelSlots {

    /** Мест на панели (без центральной кнопки). */
    const val COUNT = 4

    /** Порядок, пока человек его не менял: по тому, наше ли приложение SMS. */
    fun defaults(smsDefault: Boolean): List<PanelSlot> =
        if (smsDefault) listOf(PanelSlot.JOURNAL, PanelSlot.MESSAGES, PanelSlot.CONTACTS, PanelSlot.MENU)
        else listOf(PanelSlot.JOURNAL, PanelSlot.CONTACTS, PanelSlot.NOTES, PanelSlot.MENU)

    /**
     * Сохранённый порядок: четыре разных места, среди них «Журнал».
     * Пусто, битое или старое — null (тогда [defaults]).
     */
    fun parse(raw: String?): List<PanelSlot>? {
        if (raw.isNullOrBlank()) return null
        val out = raw.split(',').map { token ->
            PanelSlot.entries.firstOrNull { it.name == token.trim() } ?: return null
        }
        if (out.size != COUNT || out.toSet().size != COUNT || PanelSlot.JOURNAL !in out) return null
        return out
    }

    fun encode(slots: List<PanelSlot>): String = slots.joinToString(",") { it.name }

    /** Что на панели: сохранённое или порядок по роли SMS. */
    fun resolve(raw: String?, smsDefault: Boolean): List<PanelSlot> = parse(raw) ?: defaults(smsDefault)

    /**
     * Поставить [item] на место [index]. Уже на панели — меняется местами
     * с тем, что там стоит. «Журнал» с панели не убирается: заменить его
     * можно только тем, что уже на панели (то есть переставить).
     */
    fun place(slots: List<PanelSlot>, index: Int, item: PanelSlot): List<PanelSlot> {
        if (index !in slots.indices) return slots
        val old = slots[index]
        if (old == item) return slots
        val out = slots.toMutableList()
        val at = slots.indexOf(item)
        if (at >= 0) {
            out[at] = old
            out[index] = item
            return out
        }
        if (old == PanelSlot.JOURNAL) return slots
        out[index] = item
        return out
    }

    /** Разделы, которых нет на панели, — их показывает меню (боковая панель). */
    fun offPanel(slots: List<PanelSlot>): List<PanelSlot> = PanelSlot.entries.filter { it !in slots }
}
