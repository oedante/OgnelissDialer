package com.ogneliss.dialer.ui.help

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.screens.LegalDocument
import com.ogneliss.dialer.ui.settings.SettingsCategory

/**
 * «ВОПРОСЫ И ОТВЕТЫ» (1.6.0) — ЧТО СПРАШИВАЮТ И КУДА ВЕДЁТ ОТВЕТ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ВНУТРИ ПРИЛОЖЕНИЯ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 23.10: «FAQ надо в самом приложении сделать тоже. Чтобы
 * пользователя на сайт постоянно не перебрасывали». Документы открываются
 * на сайте — там их редакция всегда последняя, — а ответ на «как сделать…»
 * нужен здесь и сейчас, в том числе без сети. И ответ не только
 * объясняет, а ведёт: кнопка под ним открывает ту самую настройку.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК УСТРОЕНО
 * ══════════════════════════════════════════════════════════════════════════
 * Текст — строки faq_q_* и faq_a_* на четырёх языках. Места в настройках
 * в тексте не написаны, а подставлены (%1$s, %2$s…) из названий самих
 * пунктов — ссылки [FaqLink.Setting] и [FaqLink.Section] по порядку:
 * переименуют пункт или перенесут в другой раздел — ответ не устареет.
 * Число подстановок в каждом языке сверяет scripts/prebuild_check.py.
 *
 * Те же вопросы — на сайте, /dialer/faq: страницу собирает
 * scripts/faq_site.py из этих же строк и этого файла. Поэтому вид записей
 * ниже простой и одинаковый — скрипт читает его без компилятора.
 *
 * Поиск по настройкам находит и вопросы (SettingsHomeScreen).
 */
sealed interface FaqLink {
    /**
     * Пункт настроек по id реестра. В тексте — путь «Настройки → раздел →
     * «пункт»»; кнопка открывает раздел и подсвечивает пункт, а действие
     * (системный экран, отправка журнала) выполняет сразу.
     *
     * @param button false — путь только в тексте, без кнопки: когда кнопка
     *   вела бы туда же, куда соседняя (экспорт и импорт — один экран).
     */
    data class Setting(val entryId: String, val button: Boolean = true) : FaqLink

    /** Раздел настроек целиком: путь «Настройки → раздел». */
    data class Section(val category: SettingsCategory) : FaqLink

    /** Стать телефоном по умолчанию — системное окно. Уже стал — кнопки нет. */
    data object DefaultDialer : FaqLink

    /** Документ, встроенный в приложение: открывается здесь, без сайта. */
    data class Document(val document: LegalDocument) : FaqLink

    /** Исходный код на GitHub — в браузере. */
    data object SourceCode : FaqLink

    /** Письмо разработчику. */
    data object Mail : FaqLink
}

/** Место в настройках: путь к нему подставляется в текст ответа. */
val FaqLink.inText: Boolean
    get() = this is FaqLink.Setting || this is FaqLink.Section

/** Показывать под ответом кнопкой. */
val FaqLink.isButton: Boolean
    get() = this !is FaqLink.Setting || button

@Immutable
class FaqItem(
    /** Постоянный id: по нему поиск открывает вопрос, а сайт ставит якорь. */
    val id: String,
    @StringRes val question: Int,
    @StringRes val answer: Int,
    val links: List<FaqLink> = emptyList()
)

@Immutable
class FaqSection(@StringRes val title: Int, val items: List<FaqItem>)

object Faq {

    val sections: List<FaqSection> = listOf(
        FaqSection(
            R.string.faq_section_start,
            listOf(
                FaqItem(
                    "default", R.string.faq_q_default, R.string.faq_a_default,
                    listOf(FaqLink.DefaultDialer, FaqLink.Setting("sys_default_apps"))
                ),
                FaqItem(
                    "internet", R.string.faq_q_internet, R.string.faq_a_internet,
                    listOf(FaqLink.Setting("carrier_info"))
                ),
                FaqItem(
                    "transfer", R.string.faq_q_transfer, R.string.faq_a_transfer,
                    listOf(FaqLink.Setting("backup_export"), FaqLink.Setting("backup_import", button = false))
                )
            )
        ),
        FaqSection(
            R.string.faq_section_lists,
            listOf(
                FaqItem("switch", R.string.faq_q_switch, R.string.faq_a_switch),
                FaqItem("search", R.string.faq_q_search, R.string.faq_a_search),
                // 1.6.2, отзыв в Google Play: «редактировать контакты и делать
                // их сортировку по фамилии»
                FaqItem(
                    "names", R.string.faq_q_names, R.string.faq_a_names,
                    listOf(FaqLink.Setting("contact_name_format"), FaqLink.Setting("contact_sort"))
                ),
                FaqItem("undo", R.string.faq_q_undo, R.string.faq_a_undo),
                FaqItem(
                    "spam", R.string.faq_q_spam, R.string.faq_a_spam,
                    listOf(FaqLink.Section(SettingsCategory.SPAM))
                )
            )
        ),
        FaqSection(
            R.string.faq_section_calls,
            listOf(
                FaqItem(
                    "slide", R.string.faq_q_slide, R.string.faq_a_slide,
                    listOf(FaqLink.Setting("answer_mode"))
                ),
                FaqItem(
                    "sim", R.string.faq_q_sim, R.string.faq_a_sim,
                    listOf(FaqLink.Setting("remember_sim"), FaqLink.Setting("sim_labels"))
                ),
                FaqItem(
                    "missed", R.string.faq_q_missed, R.string.faq_a_missed,
                    listOf(FaqLink.Setting("battery"))
                ),
                FaqItem("recording", R.string.faq_q_recording, R.string.faq_a_recording)
            )
        ),
        FaqSection(
            R.string.faq_section_about,
            listOf(
                FaqItem(
                    "work", R.string.faq_q_work, R.string.faq_a_work,
                    listOf(FaqLink.Document(LegalDocument.LICENSE), FaqLink.SourceCode)
                ),
                FaqItem(
                    "contact", R.string.faq_q_contact, R.string.faq_a_contact,
                    listOf(FaqLink.Mail, FaqLink.Setting("log_share"))
                )
            )
        )
    )

    /** Все вопросы по порядку. */
    val items: List<FaqItem> = sections.flatMap { it.items }

    fun byId(id: String?): FaqItem? = id?.let { wanted -> items.firstOrNull { it.id == wanted } }
}
