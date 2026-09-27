package com.ogneliss.dialer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember

/**
 * РЕЖИМ МНОЖЕСТВЕННОГО ВЫБОРА.
 *
 * Назначение: выделить несколько записей и применить действие сразу ко всем.
 *
 * ── Зачем это нужно ────────────────────────────────────────────────────────
 * Удалять записи по одной — работа, которую пользователь делать не станет.
 * Он либо оставит мусор в журнале, либо очистит всё целиком, потеряв
 * и нужное. Заблокировать пять номеров подряд без выбора тоже нельзя.
 *
 * ── Как включается ─────────────────────────────────────────────────────────
 * Долгим нажатием на записи — это устоявшийся жест во всех списках Android,
 * и объяснять его не приходится.
 *
 * Проблема в том, что долгое нажатие у нас уже занято: оно открывает
 * карточку абонента. Решение — долгое нажатие открывает карточку,
 * а режим выбора включается кнопкой в меню. Отбирать привычный жест
 * у более частого действия неправильно: карточку открывают гораздо чаще,
 * чем удаляют пачками.
 *
 * ── Почему состояние отдельным классом ─────────────────────────────────────
 * Логика выбора одинакова для журнала, контактов и списков блокировки.
 * Дублировать её трижды означало бы получить три слегка разных поведения.
 */
class SelectionState<T> {

    var isActive by mutableStateOf(false)
        private set

    var selected by mutableStateOf<Set<T>>(emptySet())
        private set

    val count: Int get() = selected.size

    /** Включить режим и сразу выделить первый элемент. */
    /**
     * Включение режима без выделения элемента.
     *
     * ── Зачем отдельно от start(item) ──────────────────────────────────────
     * Кнопка в панели включает режим, но не знает, что выбрать: человек
     * ещё не указал. Дальше он отмечает нужное нажатиями.
     *
     * Пустой выбор при этом не сбрасывает режим автоматически — иначе
     * он выключился бы сразу после включения.
     */
    fun startEmpty() {
        isActive = true
    }

    fun start(item: T) {
        isActive = true
        selected = setOf(item)
    }

    fun toggle(item: T) {
        selected = if (item in selected) selected - item else selected + item
        // Снятие последнего выделения выходит из режима: пустой режим
        // выбора с панелью действий наверху сбивает с толку
        // Режим не выключается автоматически при пустом выборе:
        // его могли включить кнопкой, ещё ничего не отметив

    }

    fun selectAll(items: Collection<T>) {
        selected = items.toSet()
        isActive = selected.isNotEmpty()
    }

    fun clear() {
        selected = emptySet()
        isActive = false
    }

    fun isSelected(item: T): Boolean = item in selected
}

@Composable
fun <T> rememberSelectionState(): SelectionState<T> = remember { SelectionState() }
