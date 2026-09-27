package com.ogneliss.dialer.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.ContactFilter

/**
 * ШТОРКА ФИЛЬТРОВ КОНТАКТОВ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ШТОРКА, А НЕ ФИШКИ НА ПАНЕЛИ
 * ══════════════════════════════════════════════════════════════════════════
 * Пять фишек не помещались в строку и переносились на две. Две строки
 * экрана заняты постоянно ради выбора, который делают несколько раз
 * в неделю — и почти всегда возвращаются к «Все».
 *
 * Место дороже: на нём помещаются два контакта, а их листают каждый день.
 *
 * ── Почему список, а не фишки внутри шторки ────────────────────────────────
 * Фильтр контактов всегда один: нельзя выбрать «избранные» и «более
 * года» разом. Список с отметкой показывает это устройство честно,
 * а фишки намекали бы на возможность выбрать несколько.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFilterSheet(
    current: ContactFilter,
    onSelect: (ContactFilter) -> Unit,
    onDismiss: () -> Unit
) {
    /*
     * ПАНЕЛЬ РАСКРЫВАЕТСЯ СРАЗУ ПОЛНОСТЬЮ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЭТО ВАЖНО
     * ══════════════════════════════════════════════════════════════════════
     * По умолчанию ModalBottomSheet открывается наполовину, и нижние
     * пункты приходится дотягивать жестом. Пять строк не помещаются
     * в половину экрана: «Более полугода» и «Более года» оказались бы
     * за краем.
     *
     * Получилось бы ровно то, от чего уходили: скрытые пункты,
     * о которых не догадаться.
     *
     * skipPartiallyExpanded открывает панель целиком с первого раза.
     */
    /*
     * Разметка общая с заметками и напоминаниями: SingleChoiceSheet.
     * Здесь остаётся только список вариантов — то, чем этот фильтр
     * отличается от остальных.
     */
    SingleChoiceSheet(
        options = listOf(
            ContactFilter.ALL to R.string.contacts_all,
            ContactFilter.FAVOURITES to R.string.contacts_favourites,
            ContactFilter.FREQUENT to R.string.contacts_filter_frequent,
            ContactFilter.STALE_HALF_YEAR to R.string.contacts_filter_half_year,
            ContactFilter.STALE_YEAR to R.string.contacts_filter_year,
            ContactFilter.WITH_NOTE to R.string.filter_with_note,
            ContactFilter.WITH_REMINDER to R.string.filter_with_reminder
        ),
        current = current,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}
