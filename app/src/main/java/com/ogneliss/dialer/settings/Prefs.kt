package com.ogneliss.dialer.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ogneliss.dialer.call.AutoRedialMode
import com.ogneliss.dialer.call.QuietHours
import com.ogneliss.dialer.call.RedialPace
import com.ogneliss.dialer.data.ArchiveRules
import com.ogneliss.dialer.data.GroupingSettings
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.data.PinnedContacts
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ДЕЙСТВИЯ ДЛЯ ЖЕСТОВ.
 * Каждое значение имеет строковый ключ, который и сохраняется на диск.
 * Ключи менять нельзя — иначе у существующих пользователей слетят настройки.
 * Порядок элементов при этом можно менять свободно.
 */
enum class SwipeAction(val key: String) {
    NONE("none"),
    CALL("call"),
    SMS("sms"),
    WHATSAPP("whatsapp"),
    TELEGRAM("telegram"),
    VIBER("viber"),

    /**
     * Мессенджер Max.
     *
     * Российский мессенджер, набравший распространение в 2025 году.
     * Работает по тому же принципу, что WhatsApp и Viber: приложение
     * регистрирует обработчик для номера телефона, и мы просто передаём
     * ему намерение — своей интеграции не требуется.
     */
    MAX("max"),
    EMAIL("email"),
    DELETE_FROM_LOG("delete_log"),
    BLOCK_NUMBER("block"),
    VIEW_CONTACT("view_contact"),
    EDIT_BEFORE_CALL("edit_before_call"),
    COPY_NUMBER("copy"),
    SHARE_NUMBER("share"),
    REMIND_CALLBACK("remind"),

    /**
     * Записать заметку к номеру.
     *
     * Самое частое желание после разговора с незнакомого номера —
     * пометить, кто это был. Свайпом это делается на месте,
     * не открывая карточку абонента.
     */
    ADD_NOTE("note");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** Режим оформления. SYSTEM — следовать системной тёмной теме. */
/**
 * РЕЖИМЫ ОФОРМЛЕНИЯ.
 *
 * HIGH_CONTRAST — отдельный режим для слабого зрения, а не просто
 * «тёмная потемнее». В нём максимальный контраст текста к фону,
 * убраны полутона и приглушённые подписи: то, что делает интерфейс
 * изящным для одних, для других делает его нечитаемым.
 *
 * Вместе с системным увеличением шрифта и режимом крупных кнопок
 * это даёт связный набор для тех, кому обычное оформление не подходит.
 */
/**
 * ВЫБОР SIM ДЛЯ ДЕЙСТВИЙ СВАЙПА.
 *
 * ── Зачем настройка ────────────────────────────────────────────────────────
 * При двух активных картах свайп не может решить сам, с какой звонить.
 * Раньше он показывал две кнопки — и жест переставал быть быстрым:
 * приходилось целиться в одну из них.
 *
 * Теперь одна иконка и одно действие. Карту выбирает эта настройка:
 * либо спрашиваем диалогом, либо действуем заранее выбранной.
 */
enum class SwipeSimMode {
    /** Спрашивать каждый раз. Значение по умолчанию. */
    ASK,
    FIRST,
    SECOND;

    companion object {
        fun from(name: String?): SwipeSimMode =
            entries.firstOrNull { it.name == name } ?: ASK
    }
}

/**
 * ГДЕ ПОКАЗЫВАТЬ ОПЕРАТОРА И РЕГИОН.
 *
 * Сведения берутся из выгрузки Россвязи и есть у любого российского
 * номера, включая записанные в книге. Но нужны они не всем и не везде:
 *
 * • UNKNOWN_ONLY — только у незнакомых. Значение по умолчанию: у контакта
 *   с именем «Мама» оператор ничего не добавляет, а у номера без имени
 *   это единственное, за что можно зацепиться.
 * • ALL — у всех. Кому-то важно видеть, на какой номер звонили
 *   с рабочего, а на какой с личного.
 * • HIDDEN — не показывать. Строка под номером остаётся пустой,
 *   список становится спокойнее.
 */
enum class CarrierInfoMode {
    UNKNOWN_ONLY,
    ALL,
    HIDDEN;

    companion object {
        fun from(name: String?): CarrierInfoMode =
            entries.firstOrNull { it.name == name } ?: UNKNOWN_ONLY
    }
}

/**
 * ФОТО ЗВОНЯЩЕГО НА ЭКРАНЕ ВЫЗОВА (1.5.5, отзыв в Google Play: «можно ли
 * при звонках фото контакта не на весь экран, а в круге или квадрате,
 * среднего размера»).
 *
 * • CIRCLE — портрет в круге над именем, за ним живой фон, волны
 *   расходятся от края портрета. ПО УМОЛЧАНИЮ (автор, 18.10: «этот режим
 *   сделаем по умолчанию») — и для тех, кто настройку не трогал.
 * • FULL_SCREEN — фото фоном на весь экран (как в 1.3.4–1.5.4).
 * • SQUARE — то же в скруглённом квадрате.
 *
 * Лёжа и в позе «ноутбук» фото на весь экран не помещается и раньше —
 * там всегда портрет; квадрат и круг там выбираются этой настройкой.
 */
/**
 * МАСШТАБ ИНТЕРФЕЙСА — КАК В OGNELISS MAIL (2.2.1).
 *
 * Автор, 11.11.2026: «масштаб давай тоже оттуда возьмём и изменение
 * масштаба» (за образец интерфейса взята почта). Значения те же, что
 * в `UiZoom.manual` почты: «Авто» (по умолчанию) и 85, 90, 95, 100, 110 %.
 * Разбор и расчёт «Авто» — ui/theme/UiScale.kt.
 *
 * Было (1.5.6–2.2.0): «Авто», 90, 100, 110, 125 %, по умолчанию 90 %.
 * Выбранное человеком значение сохраняется; 125 % нет в почте — становится
 * 110 %. Кто не выбирал (жил на прежнем умолчании 90 %) — получает «Авто».
 */
enum class UiScale(val fixed: Float?) {
    AUTO(null),
    S85(0.85f),
    S90(0.9f),
    S95(0.95f),
    S100(1f),
    S110(1.1f);

    companion object {
        /** Масштаб, пока человек его не выбрал. */
        val DEFAULT = AUTO

        /** Имя из настроек; «S125» прежних версий — ближайшее из нынешних. */
        fun from(name: String?): UiScale = when (name) {
            "S125" -> S110
            else -> entries.firstOrNull { it.name == name } ?: DEFAULT
        }
    }
}

/**
 * РАЗМЕР ШРИФТА (2.2.1) — как «Размер шрифта» в Ogneliss Mail: множитель
 * 0,8…1,6 с шагом 0,1 поверх размера шрифта Android. Разбор — UiScale.kt.
 */
object FontScale {
    const val MIN = 0.8f
    const val MAX = 1.6f
    const val DEFAULT = 1f

    /** Шагов ползунка между краями: 0,8 · 0,9 · … · 1,6. */
    const val STEPS = 7

    /** К шагу 0,1 и в пределы: из настроек может прийти что угодно. */
    fun clamp(v: Float): Float = (Math.round(v.coerceIn(MIN, MAX) * 10f) / 10f)
}

enum class CallPhotoStyle {
    CIRCLE,
    SQUARE,
    FULL_SCREEN;

    companion object {
        fun from(name: String?): CallPhotoStyle =
            entries.firstOrNull { it.name == name } ?: CIRCLE
    }
}

/**
 * РАЗМЕР ПОРТРЕТА НА ЭКРАНЕ ВЫЗОВА (1.5.7).
 *
 * Отзыв valmikk (4PDA, 18.10, о 1.5.5): «А увеличить (раза в полтора)
 * круг/квадрат фото получится?» В 1.5.6 портрет уже вырос — до 46 %
 * ширины; «Крупный» даёт ещё треть сверху. Выше места над панелью
 * портрет не вырастет ни при каком выборе: разбор в AdaptivePortraitColumn.
 * Размеры — во FlowUi (callPortraitMaxFor).
 *
 * ── «Крупный» — по умолчанию (1.5.8, обзор интерфейса, П4) ─────────────────
 * На снимке автора под портретом «Обычного» до панели кнопок оставалась
 * пустая полоса — «Крупный» её занимает. Кто выбрал «Обычный» сам, у того
 * он и останется: в хранилище лежит его выбор.
 */
enum class CallPhotoSize {
    NORMAL,
    LARGE;

    companion object {
        fun from(name: String?): CallPhotoSize =
            entries.firstOrNull { it.name == name } ?: LARGE
    }
}

/**
 * ОТВЕТ НА ВЫЗОВ: КАСАНИЕМ ИЛИ СДВИГОМ (1.5.8, обзор интерфейса, У6).
 *
 * «Ответить» и «Отклонить» — большие кнопки, и это правильно для того,
 * кто смотрит на экран. Но телефон, вынутый из кармана со звонком, может
 * ответить или сбросить вызов случайным касанием ладони. Системные
 * диалеры поэтому отвечают сдвигом.
 *
 * По умолчанию — касанием, как было: менять привычку без спроса нельзя.
 * Сдвиг — по выбору; разбор жеста — в call/SlideToAnswer.kt.
 */
/**
 * ФОРМА ЗНАЧКОВ «SMS» И «КОНТАКТЫ» НА ГЛАВНОМ ЭКРАНЕ (2.1.3).
 *
 * Автор, 11.11.2026 (значок-виджет вышел кругом среди скруглённых квадратов
 * набора значков): «хотелось бы адаптивный виджет SMS и контактов — чтобы
 * по форме можно было выбрать скругление». Виджет рисует приложение, а не
 * лаунчер: ни формы соседних значков, ни темы значков лаунчер не сообщает,
 * и угадать её удаётся не всегда (WidgetIcon). Поэтому форму можно выбрать:
 * AUTO — как у системы (прежнее поведение), иначе — доля стороны под
 * скругление угла ([corner]): 0,5 — круг, 0 — квадрат.
 */
enum class WidgetShape(val corner: Float?) {
    AUTO(null),
    CIRCLE(0.5f),
    ROUND_LARGE(0.34f),
    ROUND_MEDIUM(0.25f),
    ROUND_SMALL(0.16f),
    SQUARE(0f);

    companion object {
        fun from(name: String?): WidgetShape =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * ОТМЕНА ОТПРАВКИ SMS (2.2.0): сколько секунд после «Отправить» сообщение
 * ждёт в очереди с «Отменить» под пузырём (SmsQueue). OFF — сразу.
 */
enum class UndoSend(val seconds: Int) {
    OFF(0), S3(3), S5(5), S10(10);

    companion object {
        fun from(name: String?): UndoSend = entries.firstOrNull { it.name == name } ?: S5
    }
}

/**
 * АВТОУДАЛЕНИЕ КОДОВ (2.2.0): входящие с кодом подтверждения удаляются
 * через [hours] после прихода (OtpCleanup в sms). OFF — не удалять.
 */
enum class OtpCleanup(val hours: Int) {
    OFF(0), H1(1), H24(24);

    companion object {
        fun from(name: String?): OtpCleanup = entries.firstOrNull { it.name == name } ?: OFF
    }
}

enum class AnswerMode {
    TAP,
    SLIDE;

    companion object {
        fun from(name: String?): AnswerMode =
            entries.firstOrNull { it.name == name } ?: TAP
    }
}

/**
 * КАК ПОКАЗЫВАТЬ ИМЯ КОНТАКТА (1.6.2).
 *
 * Отзыв в Google Play: «сильно не хватает … сортировки по фамилии»;
 * автор, 23.10: «отображение имени контакта можно сделать настраиваемым
 * и предлагать пользователю выбор, как показывать».
 *
 * GIVEN_FIRST — «Имя Фамилия», как записано в книге (DISPLAY_NAME_PRIMARY);
 * FAMILY_FIRST — «Фамилия Имя» (DISPLAY_NAME_ALTERNATIVE: провайдер
 * собирает его из частей имени сам, запятую после фамилии убираем).
 * У записи без частей имени — организации, почты, одного слова —
 * обе формы совпадают. Разбор — data/ContactNames.kt.
 */
enum class ContactNameFormat {
    GIVEN_FIRST,
    FAMILY_FIRST;

    companion object {
        fun from(name: String?): ContactNameFormat =
            entries.firstOrNull { it.name == name } ?: GIVEN_FIRST
    }
}

/**
 * ПОРЯДОК СПИСКА КОНТАКТОВ (1.6.2): по имени или по фамилии.
 *
 * Отдельно от формата имени, как в системных «Контактах»: можно
 * показывать «Андрей Петров», а искать его под «П». Буква раздела —
 * по тому, по чему отсортировано.
 */
enum class ContactSort {
    GIVEN,
    FAMILY;

    companion object {
        fun from(name: String?): ContactSort =
            entries.firstOrNull { it.name == name } ?: GIVEN
    }
}

/**
 * ВИД ЖУРНАЛА: КАЖДАЯ ЗАПИСЬ КАРТОЧКОЙ ИЛИ ЗАПИСИ ГРУППОЙ.
 *
 * CARDS — каждая запись — отдельная скруглённая карточка с зазором.
 * Так журнал выглядел до 1.1.3.
 *
 * GROUPS — записи одной группы («Сегодня», «Вчера»…) лежат на одной
 * общей подложке, разделённые линией. Меньше контейнеров и зазоров —
 * на экране больше записей, вид легче. Предложено аудитом оформления
 * (docs/UI_AUDIT_2026_09.md, пункт C).
 *
 * С 1.1.3 по умолчанию — GROUPS: решение принято по сравнению обоих
 * видов на устройстве. Кто уже выбрал вид сам, сохраняет свой выбор:
 * значение по умолчанию действует, только пока в настройках ничего
 * не записано.
 */
enum class JournalStyle {
    CARDS,
    GROUPS;

    companion object {
        fun from(name: String?): JournalStyle =
            entries.firstOrNull { it.name == name } ?: GROUPS
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK_AMOLED, HIGH_CONTRAST }

/**
 * Цветовая тема (1.8.4) — как «Цветовая тема» Ogneliss Mail: тон фона
 * и акцент, у каждой светлый и тёмный вариант. Расчёт — ThemePresetMath.
 * Хранится строкой, имена не менять: они же ключи ThemePresetMath.SPECS.
 */
enum class ThemePreset {
    CLASSIC, SKY, OCEAN, FOREST, SUNSET, GRAPHITE, MIDNIGHT;

    companion object {
        fun from(name: String?): ThemePreset = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

/*
 * ОФОРМЛЕНИЕ КАК В OGNELISS MAIL (1.9.0).
 *
 * Автор, 03.11.2026: «переноси все» — четыре настройки «Оформления» почты
 * (lib/core/theme/appearance.dart, ui_fonts.dart), которых не было
 * в диалере: свой акцент, скругление углов, плотность списков, шрифт.
 * Значения по умолчанию — прежний вид диалера: кто не трогал настройки,
 * разницы не увидит. Хранятся строкой (имена не менять) и числом (акцент).
 */

/** Скругление углов: множитель радиусов карточек, окон, шторок, полей (как в почте). */
enum class CornerSize(val factor: Float) {
    SMALL(0.5f), MEDIUM(1f), LARGE(1.4f);

    companion object {
        fun from(name: String?): CornerSize = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** Плотность журнала и контактов: поле строки и размер аватара (DensitySpec почты). */
enum class ListDensity {
    COMPACT, NORMAL, COMFORTABLE;

    companion object {
        fun from(name: String?): ListDensity = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/**
 * Шрифт интерфейса (как UiFonts почты на Android): системный (по умолчанию —
 * прежний вид), Roboto из пакета приложения (одинаков на любом телефоне,
 * как в почте), Noto Serif и Roboto Condensed — шрифты системы.
 */
enum class UiFont {
    SYSTEM, ROBOTO, SERIF, CONDENSED;

    companion object {
        fun from(name: String?): UiFont = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Всё, из чего строится тема приложения, кроме режима и масштаба:
 * цветовая тема, свой акцент (ARGB; null — акцент темы), скругление, шрифт.
 * Одно значение — экраны (главный, вызов, псевдозвонок) берут его одним потоком.
 */
data class ThemeLook(
    val preset: ThemePreset = ThemePreset.CLASSIC,
    val accent: Int? = null,
    val corners: CornerSize = CornerSize.MEDIUM,
    val font: UiFont = UiFont.SYSTEM
)

/**
 * Режим группировки журнала. Значения совпадают с CallLogGrouper.Mode.
 * Хранится строкой, поэтому имена менять нельзя.
 */
enum class GroupingMode { CONSECUTIVE, BY_CONTACT, NONE }

/** Порядок результатов T9-поиска. */
enum class SortOrder { RELEVANCE, RECENCY, FREQUENCY, ALPHABET }

private val Context.dataStore by preferencesDataStore("ogneliss_prefs")

/**
 * НАСТРОЙКИ ПРИЛОЖЕНИЯ.
 *
 * Назначение: постоянное хранение пользовательских настроек, переживающее
 * перезапуск приложения и перезагрузку устройства.
 *
 * ── Где и как хранится ─────────────────────────────────────────────────────
 * DataStore Preferences — файл в приватном каталоге приложения
 * (/data/data/com.ogneliss.dialer/files/datastore/). Записи атомарны:
 * при внезапном отключении питания файл не окажется наполовину записанным,
 * в отличие от SharedPreferences.
 *
 * ── Что здесь, а что в базе ────────────────────────────────────────────────
 * ЗДЕСЬ (DataStore): одиночные значения-переключатели — тема, язык, жесты,
 * пороги, флаги. Их мало, они читаются при каждом запуске целиком.
 *
 * В БАЗЕ (Room): всё, чего может быть много и что нужно искать —
 * заметки, метки, чёрный и белый списки, T9-индекс.
 * См. data/db/AppDatabase.kt.
 *
 * ── Восстановление после сбоя ──────────────────────────────────────────────
 * Если файл настроек повреждён, DataStore бросает IOException. Мы его
 * перехватываем и отдаём значения по умолчанию, чтобы приложение
 * запустилось, а не падало в цикле.
 */
@Singleton
class Prefs @Inject constructor(@ApplicationContext private val context: Context) : GroupingSettings {




    /**
     * Ключи хранения. Строковые константы — часть контракта совместимости:
     * переименование ключа равносильно сбросу настройки у пользователя.
     */
    private companion object {
        const val TAG = "Prefs"

        /**
         * Сколько дополнительных действий помещается в панель свайпа.
         * Больше трёх кнопок делают их уже комфортного размера,
         * и выбор превращается в прицеливание.
         */
        const val MAX_EXTRA_ACTIONS = 3
    }

    private object Keys {
        // Жесты
        val SWIPE_RIGHT = stringPreferencesKey("swipe_right")
        val SWIPE_TRIGGER_DP = intPreferencesKey("swipe_trigger_dp")
        val SWIPE_POSITIONAL = intPreferencesKey("swipe_positional")
        val SWIPE_VELOCITY = intPreferencesKey("swipe_velocity")
        val SWIPE_HORIZONTAL_START = booleanPreferencesKey("swipe_horizontal_start")
        val SWIPE_RIGHT_EXTRA = stringPreferencesKey("swipe_right_extra")
        val SWIPE_LEFT_EXTRA = stringPreferencesKey("swipe_left_extra")
        val SWIPE_LEFT = stringPreferencesKey("swipe_left")
        // 2.1.0: свайпы раздела «SMS» — свои, отдельно от журнала
        val SMS_SWIPE_RIGHT = stringPreferencesKey("sms_swipe_right")
        val SMS_SWIPE_LEFT = stringPreferencesKey("sms_swipe_left")
        val SMS_SWIPE_RIGHT_EXTRA = stringPreferencesKey("sms_swipe_right_extra")
        val SMS_SWIPE_LEFT_EXTRA = stringPreferencesKey("sms_swipe_left_extra")
        // 2.1.0: закреплённые переписки — свои, отдельно от журнала
        val SMS_PINNED = stringPreferencesKey("sms_pinned")
        // 2.1.0: архив — переписки («ключ|время» строками) и записи журнала (id через запятую)
        val SMS_ARCHIVED = stringPreferencesKey("sms_archived")
        val CALL_ARCHIVED = stringPreferencesKey("call_archived")
        // Оформление
        val THEME = stringPreferencesKey("theme")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        // 1.9.0: оформление как в Ogneliss Mail
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val CORNER_SIZE = stringPreferencesKey("corner_size")
        val LIST_DENSITY = stringPreferencesKey("list_density")
        val UI_FONT = stringPreferencesKey("ui_font")
        val LANGUAGE = stringPreferencesKey("language")
        val SWIPE_SIM_MODE = stringPreferencesKey("swipe_sim_mode")
        val CARRIER_INFO = stringPreferencesKey("carrier_info")
        val JOURNAL_STYLE = stringPreferencesKey("journal_style")

        /**
         * Номер, с которого «звонят» при псевдозвонке.
         *
         * Пусто — берётся случайный контакт. Это и значение
         * по умолчанию: тот, кто нажмёт кнопку в неудобный момент,
         * ничего заранее не настраивал.
         */
        val FAKE_CALL_NUMBER = stringPreferencesKey("fake_call_number")

        /**
         * Показывать ли круглую кнопку псевдозвонка на экранах.
         *
         * Включена по умолчанию: без кнопки функцию найдёт только тот,
         * кто уже знает про неё. Но она видна всем, кто смотрит
         * на экран, и человеку, которому эта функция не нужна,
         * её лучше убрать.
         */
        val FAKE_CALL_BUTTON = booleanPreferencesKey("fake_call_button")

        /**
         * Время назначенного псевдозвонка, RTC. 0 — не назначен.
         *
         * Будильник в системе о себе не рассказывает: спросить
         * AlarmManager «а что там назначено» нельзя. Без своей записи
         * окно псевдозвонка не может показать, установлен ли он
         * и на когда, — а без этого «Отменить» нажимают вслепую.
         */
        val FAKE_CALL_AT = longPreferencesKey("fake_call_at")

        /**
         * Последний известный список SIM-карт, строкой JSON.
         *
         * Нужен, чтобы кнопки выбора карты были на экране с первого
         * кадра. Разбор — в DialerViewModel, рядом с cachedSims.
         */
        val SIM_CACHE = stringPreferencesKey("sim_cache")
        val PANEL_SWIPE_NAV = booleanPreferencesKey("panel_swipe_nav")
        val PINNED_NUMBERS = stringPreferencesKey("pinned_numbers")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SHOW_PHOTO = booleanPreferencesKey("show_photo")
        /** «Написать»: установленные мессенджеры для любого номера (1.3.5, выкл.). */
        val MESSENGERS_ANY_NUMBER = booleanPreferencesKey("messengers_any_number")
        val LARGE_BUTTONS = booleanPreferencesKey("large_buttons")
        val BUTTON_LABELS = booleanPreferencesKey("button_labels")
        val PROXIMITY_SCREEN_OFF = booleanPreferencesKey("proximity_screen_off")
        val CALL_HIDE_CONTACT_NUMBER = booleanPreferencesKey("call_hide_contact_number")
        /** Живой фон экрана вызова (1.5.1, по умолчанию вкл.). */
        val CALL_ANIMATION = booleanPreferencesKey("call_animation")
        /** Фото звонящего: на весь экран, круг или квадрат (1.5.5). */
        val CALL_PHOTO_STYLE = stringPreferencesKey("call_photo_style")
        /** Размер портрета на экране вызова (1.5.7). */
        val CALL_PHOTO_SIZE = stringPreferencesKey("call_photo_size")
        /** Ответ на вызов: касанием или сдвигом (1.5.8). */
        val ANSWER_MODE = stringPreferencesKey("answer_mode")
        val WIDGET_SHAPE = stringPreferencesKey("widget_shape")
        /** Имя контакта: «Имя Фамилия» или «Фамилия Имя» (1.6.2). */
        val CONTACT_NAME_FORMAT = stringPreferencesKey("contact_name_format")
        /** Порядок контактов: по имени или по фамилии (1.6.2). */
        val CONTACT_SORT = stringPreferencesKey("contact_sort")
        /** Аккаунт последнего созданного контакта (1.7.0): «тип\u0000имя». */
        val CONTACT_ACCOUNT = stringPreferencesKey("contact_account")
        /** Масштаб интерфейса (1.5.6). */
        val UI_SCALE = stringPreferencesKey("ui_scale")
        // 2.2.1: размер шрифта, как в Ogneliss Mail
        val FONT_SCALE = floatPreferencesKey("font_scale")
        // 2.2.2: подсказка о свайпах в журнале показана
        val SWIPE_HINT_DONE = booleanPreferencesKey("swipe_hint_done")
        // Поведение
        val HAPTIC = booleanPreferencesKey("haptic")
        val KEY_TONES = booleanPreferencesKey("key_tones")
        val MERGE_CALLS = booleanPreferencesKey("merge_calls")
        val GROUPING_MODE = stringPreferencesKey("grouping_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val DEFAULT_SIM = intPreferencesKey("default_sim")
        /** Пользовательские имена слотов: "Рабочая", "Личная". */
        val SIM_LABEL_1 = stringPreferencesKey("sim_label_1")
        val SIM_LABEL_2 = stringPreferencesKey("sim_label_2")
        val REMEMBER_SIM = booleanPreferencesKey("remember_sim")
        val SMART_SPEAKER = booleanPreferencesKey("smart_speaker")
        val DIALPAD_OPEN_AT_START = booleanPreferencesKey("dialpad_open_at_start")
        val CALLBACK_SUGGESTION = booleanPreferencesKey("callback_suggestion")
        // 2.1.0: входящий плашкой сверху, когда телефоном пользуются
        val INCOMING_BANNER = booleanPreferencesKey("incoming_banner")
        val SWAP_ANSWER = booleanPreferencesKey("swap_answer")
        /** Вибрация, когда вызываемый снял трубку (2.5.0). */
        val VIBRATE_ON_CONNECT = booleanPreferencesKey("vibrate_on_connect")
        /** Автодозвон (1.8.0): выключен, предлагать, сразу — разбор в RedialPolicy. */
        val AUTO_REDIAL = stringPreferencesKey("auto_redial")
        /** Сколько раз и как часто (1.8.0): готовые сочетания RedialPace. */
        val AUTO_REDIAL_PACE = stringPreferencesKey("auto_redial_pace")
        /** Памятка о мошенниках в долгом разговоре с незнакомым (1.8.0). */
        val SCAM_MEMO = booleanPreferencesKey("scam_memo")
        /**
         * Сообщения (2.0.0): разбор в sms/SmsRole.kt (SmsSettings).
         * «sms_in_app» убран в 2.1.0 — сохранённое значение больше не читается.
         */
        val SMS_DELIVERY_REPORTS = booleanPreferencesKey("sms_delivery_reports")
        // 2.2.0
        val SMS_UNDO_SEND = stringPreferencesKey("sms_undo_send")
        val SMS_CALLS_IN_THREAD = booleanPreferencesKey("sms_calls_in_thread")
        val SMS_OTP_CLEANUP = stringPreferencesKey("sms_otp_cleanup")
        val SMS_FOLDERS = stringPreferencesKey("sms_folders")
        /** Порядок и показ папок журнала и контактов (2.5.0, FolderOrder) — формат как у SMS. */
        val JOURNAL_FOLDERS = stringPreferencesKey("journal_folders")
        val CONTACT_FOLDERS = stringPreferencesKey("contact_folders")
        val NOTES_RETENTION = stringPreferencesKey("notes_retention")
        val NOTES_RETENTION_SCOPE = stringPreferencesKey("notes_retention_scope")
        // Экран после звонка
        val POST_CALL_ENABLED = booleanPreferencesKey("post_call_enabled")
        val POST_CALL_UNKNOWN_ONLY = booleanPreferencesKey("post_call_unknown_only")
        // Антиспам
        val BLOCK_HIDDEN = booleanPreferencesKey("block_hidden")
        val BLOCK_NOT_IN_CONTACTS = booleanPreferencesKey("block_not_in_contacts")
        val BLOCK_TAGGED_SPAM = booleanPreferencesKey("block_tagged_spam")
        /** Тихие часы для незнакомых (1.8.0): разбор в call/QuietHours.kt. */
        val QUIET_HOURS = stringPreferencesKey("quiet_hours")
        val EXTERNAL_LOOKUP_IDS = stringSetPreferencesKey("external_lookup_ids")
        // Диагностика
        val FILE_LOGGING = booleanPreferencesKey("file_logging")
        // Служебное
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val GESTURE_HINT_SHOWN = booleanPreferencesKey("gesture_hint_shown")
        val OWN_NUMBER = stringPreferencesKey("own_number")
        val CONSENT_VERSION = intPreferencesKey("consent_version")
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
    }

    /**
     * Поток настроек с защитой от повреждённого файла.
     * Любая ошибка чтения → пустые настройки → значения по умолчанию.
     */
    private val data: Flow<Preferences> = context.dataStore.data
        .catch { error ->
            if (error is IOException) {
                Logger.e(TAG, "Файл настроек повреждён, применяю значения по умолчанию", error)
                emit(emptyPreferences())
            } else throw error
        }

    // ── Жесты ──────────────────────────────────────────────────────────────
    /**
     * ДОПОЛНИТЕЛЬНЫЕ ДЕЙСТВИЯ СВАЙПА.
     *
     * ── Зачем несколько действий на одну сторону ───────────────────────────
     * Написать человеку можно по-разному: SMS, WhatsApp, Max. Заставлять
     * выбирать одно на все случаи неправильно — нужное зависит от того,
     * кому пишешь.
     *
     * Основное действие срабатывает сразу при коротком свайпе.
     * Дополнительные появляются кнопками, если протянуть дальше:
     * быстрый путь остаётся быстрым, а выбор — доступным.
     *
     * Хранится строкой через запятую: набор небольшой и меняется редко,
     * заводить ради него таблицу избыточно.
     */
    val swipeRightExtra: Flow<List<SwipeAction>> = data.map { p ->
        parseActions(p[Keys.SWIPE_RIGHT_EXTRA])
    }

    val swipeLeftExtra: Flow<List<SwipeAction>> = data.map { p ->
        parseActions(p[Keys.SWIPE_LEFT_EXTRA])
    }

    /*
     * СВАЙПЫ РАЗДЕЛА «SMS» (2.1.0).
     *
     * Было: переписки брали свайпы журнала — вправо «Позвонить», влево
     * «SMS», и «SMS» по переписке открывал ту же переписку. Автор,
     * 07.11.2026: «свайпы должны быть „SMS“ и „удалить“, настраиваться
     * отдельно от журнала звонков». Стало: свои ключи, по умолчанию
     * вправо — «SMS» (открыть переписку), влево — «Удалить».
     */
    val smsSwipeRight: Flow<SwipeAction> =
        data.map { SwipeAction.from(it[Keys.SMS_SWIPE_RIGHT] ?: SwipeAction.SMS.key) }
    val smsSwipeLeft: Flow<SwipeAction> =
        data.map { SwipeAction.from(it[Keys.SMS_SWIPE_LEFT] ?: SwipeAction.DELETE_FROM_LOG.key) }
    val smsSwipeRightExtra: Flow<List<SwipeAction>> = data.map { parseActions(it[Keys.SMS_SWIPE_RIGHT_EXTRA]) }
    val smsSwipeLeftExtra: Flow<List<SwipeAction>> = data.map { parseActions(it[Keys.SMS_SWIPE_LEFT_EXTRA]) }

    suspend fun setSmsSwipeRight(v: SwipeAction) = edit("sms_swipe_right", v.key) { it[Keys.SMS_SWIPE_RIGHT] = v.key }
    suspend fun setSmsSwipeLeft(v: SwipeAction) = edit("sms_swipe_left", v.key) { it[Keys.SMS_SWIPE_LEFT] = v.key }
    suspend fun setSmsSwipeRightExtra(actions: List<SwipeAction>) =
        edit("sms_swipe_right_extra", actions.size) {
            it[Keys.SMS_SWIPE_RIGHT_EXTRA] = actions.joinToString(",") { a -> a.key }
        }
    suspend fun setSmsSwipeLeftExtra(actions: List<SwipeAction>) =
        edit("sms_swipe_left_extra", actions.size) {
            it[Keys.SMS_SWIPE_LEFT_EXTRA] = actions.joinToString(",") { a -> a.key }
        }

    /*
     * ЗАКРЕПЛЁННЫЕ ПЕРЕПИСКИ (2.1.0) — отдельно от закреплённых журнала
     * (автор: «нужна возможность закрепления в SMS, отдельное от журнала
     * звонков»). Храним адрес переписки в E.164 (или как есть у буквенных
     * отправителей, «Bank»), порядок — порядок закрепления.
     */
    val smsPinned: Flow<List<String>> = data.map { p ->
        p[Keys.SMS_PINNED]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
    }

    suspend fun setSmsPinned(addresses: List<String>) =
        edit("sms_pinned", addresses.size) { it[Keys.SMS_PINNED] = addresses.joinToString("\n") }

    /*
     * АРХИВ (2.1.0) — правила в ArchiveRules. Было: только удалить; стало —
     * «В архив» из режима выбора журнала и SMS. Изменение — одним edit
     * (прочитать и записать внутри него): два быстрых нажатия «В архив»
     * подряд не должны терять первое, как при записи готового списка.
     */
    val smsArchived: Flow<Map<String, Long>> = data.map { ArchiveRules.parseSms(it[Keys.SMS_ARCHIVED]) }

    suspend fun updateSmsArchived(transform: (Map<String, Long>) -> Map<String, Long>) =
        edit("sms_archived", null) {
            it[Keys.SMS_ARCHIVED] = ArchiveRules.formatSms(transform(ArchiveRules.parseSms(it[Keys.SMS_ARCHIVED])))
        }

    val callArchived: Flow<Set<Long>> = data.map { ArchiveRules.parseCallIds(it[Keys.CALL_ARCHIVED]) }

    suspend fun updateCallArchived(transform: (Set<Long>) -> Set<Long>) =
        edit("call_archived", null) {
            it[Keys.CALL_ARCHIVED] = ArchiveRules.formatCallIds(transform(ArchiveRules.parseCallIds(it[Keys.CALL_ARCHIVED])))
        }

    private fun parseActions(raw: String?): List<SwipeAction> =
        raw?.split(',')
            ?.mapNotNull { key -> SwipeAction.entries.firstOrNull { it.key == key.trim() } }
            ?.filter { it != SwipeAction.NONE }
            ?.distinct()
            ?.take(MAX_EXTRA_ACTIONS)
            .orEmpty()

    /**
     * НАСТРОЙКИ ЧУВСТВИТЕЛЬНОСТИ СВАЙПА.
     *
     * Собраны в один объект, а не в четыре отдельных потока: они всегда
     * читаются вместе, и раздельная подписка означала бы четыре
     * перерисовки списка вместо одной при сбросе к значениям
     * по умолчанию.
     */
    val swipeTuning: Flow<SwipeTuning> = data.map { p ->
        SwipeTuning(
            triggerDistanceDp = p[Keys.SWIPE_TRIGGER_DP]
                ?: SwipeTuning.DEFAULT_TRIGGER_DP,
            positionalThresholdPercent = p[Keys.SWIPE_POSITIONAL]
                ?: SwipeTuning.DEFAULT_POSITIONAL_PERCENT,
            velocityDpPerSecond = p[Keys.SWIPE_VELOCITY]
                ?: SwipeTuning.DEFAULT_VELOCITY_DP,
            requireHorizontalStart = p[Keys.SWIPE_HORIZONTAL_START]
                ?: SwipeTuning.DEFAULT_REQUIRE_HORIZONTAL
        )
    }

    val swipeRight: Flow<SwipeAction> =
        data.map { SwipeAction.from(it[Keys.SWIPE_RIGHT] ?: SwipeAction.CALL.key) }
    val swipeLeft: Flow<SwipeAction> =
        data.map { SwipeAction.from(it[Keys.SWIPE_LEFT] ?: SwipeAction.SMS.key) }

    // ── Оформление ─────────────────────────────────────────────────────────
    /*
     * Масштаба шрифта здесь нет намеренно: вся типографика задана в sp
     * и подчиняется системной настройке размера шрифта. Своя копия
     * этой настройки конфликтовала бы с системной, и пользователь
     * получал бы двойное увеличение.
     */
    /**
     * ВЫБРАННЫЙ ЯЗЫК ИНТЕРФЕЙСА.
     *
     * Хранится у нас, а не только в AppCompatDelegate: тот не является
     * состоянием Compose, и экран настроек не узнавал бы об изменении —
     * переключатель оставался бы на «Как в системе».
     *
     * Пустая строка означает «следовать за системой».
     */
    val language: Flow<String> = data.map { it[Keys.LANGUAGE].orEmpty() }

    /**
     * КАКОЙ SIM-КАРТОЙ ДЕЙСТВОВАТЬ ПРИ СВАЙПЕ.
     *
     * По умолчанию спрашиваем: свайп — быстрый жест, и ошибиться картой
     * при двух номерах означает потратить деньги не с того счёта.
     * Кому неважно — выбирает конкретную карту и избавляется от диалога.
     */
    /**
     * ПЕРЕЛИСТЫВАНИЕ МЕЖДУ ВЫЗОВАМИ И КОНТАКТАМИ.
     *
     * Свайп по нижней панели: влево — в контакты, вправо — обратно.
     *
     * По умолчанию включено: жест не мешает ничему другому — панель
     * не прокручивается, и горизонтальное движение по ней однозначно.
     *
     * Отключаемо на случай, если человек часто задевает панель
     * при наборе номера.
     */
    val panelSwipeNav: Flow<Boolean> = data.map { it[Keys.PANEL_SWIPE_NAV] ?: true }

    val fakeCallNumber: Flow<String> = data.map { it[Keys.FAKE_CALL_NUMBER].orEmpty() }

    val fakeCallButton: Flow<Boolean> = data.map { it[Keys.FAKE_CALL_BUTTON] ?: true }

    val fakeCallAt: Flow<Long> = data.map { it[Keys.FAKE_CALL_AT] ?: 0L }

    val simCache: Flow<String> = data.map { it[Keys.SIM_CACHE].orEmpty() }

    val carrierInfo: Flow<CarrierInfoMode> = data.map { p ->
        CarrierInfoMode.from(p[Keys.CARRIER_INFO])
    }

    val journalStyle: Flow<JournalStyle> = data.map { p ->
        JournalStyle.from(p[Keys.JOURNAL_STYLE])
    }

    val swipeSimMode: Flow<SwipeSimMode> = data.map { p ->
        SwipeSimMode.from(p[Keys.SWIPE_SIM_MODE])
    }

    /**
     * ЗАКРЕПЛЁННЫЕ НОМЕРА.
     *
     * Строкой через запятую: набор не больше пяти элементов и меняется
     * редко — заводить ради него таблицу в базе избыточно.
     *
     * Порядок в строке значим: он определяет порядок карточек наверху
     * журнала, и сортировать его нельзя.
     */
    val pinnedNumbers: Flow<List<String>> = data.map { p ->
        p[Keys.PINNED_NUMBERS]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(PinnedContacts.MAX_PINNED)
            .orEmpty()
    }

    val theme: Flow<ThemeMode> = data.map { p ->
        runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    /** Цветовая тема (1.8.4), по умолчанию «Классика» — прежние цвета. */
    val themePreset: Flow<ThemePreset> = data.map { ThemePreset.from(it[Keys.THEME_PRESET]) }

    /** Свой акцент (1.9.0): ARGB; null — акцент цветовой темы. */
    val accentColor: Flow<Int?> = data.map { it[Keys.ACCENT_COLOR] }
    val cornerSize: Flow<CornerSize> = data.map { CornerSize.from(it[Keys.CORNER_SIZE]) }
    val listDensity: Flow<ListDensity> = data.map { ListDensity.from(it[Keys.LIST_DENSITY]) }
    val uiFont: Flow<UiFont> = data.map { UiFont.from(it[Keys.UI_FONT]) }

    /** Тема целиком (1.9.0): одно чтение хранилища на все четыре значения. */
    val themeLook: Flow<ThemeLook> = data.map {
        ThemeLook(
            preset = ThemePreset.from(it[Keys.THEME_PRESET]),
            accent = it[Keys.ACCENT_COLOR],
            corners = CornerSize.from(it[Keys.CORNER_SIZE]),
            font = UiFont.from(it[Keys.UI_FONT])
        )
    }.distinctUntilChanged()
    /** По умолчанию false: динамический цвет системы перекрасил бы бренд. */
    /**
     * Цвета из обоев пользователя.
     *
     * ВЫКЛЮЧЕНО по умолчанию: результат непредсказуем. При одних обоях
     * интерфейс получается контрастным, при других блёклым, и проверить
     * это заранее нельзя.
     *
     * По умолчанию работает проверенная палитра с посчитанным контрастом;
     * кому нужна связь с обоями — включает сам.
     */
    val dynamicColor: Flow<Boolean> = data.map { it[Keys.DYNAMIC_COLOR] ?: false }
    val showPhoto: Flow<Boolean> = data.map { it[Keys.SHOW_PHOTO] ?: true }
    val messengersAnyNumber: Flow<Boolean> = data.map { it[Keys.MESSENGERS_ANY_NUMBER] ?: false }
    val largeButtons: Flow<Boolean> = data.map { it[Keys.LARGE_BUTTONS] ?: false }

    /** Подписи под кнопками: разбор у Appearance.buttonLabels. По умолчанию — показывать. */
    val buttonLabels: Flow<Boolean> = data.map { it[Keys.BUTTON_LABELS] ?: true }

    /**
     * Гасить экран у уха по датчику приближения. По умолчанию — да:
     * без этого щека нажимает кнопки. Просили выключаемым в отзыве
     * в Google Play (бывают датчики, которые гасят экран не вовремя).
     */
    val proximityScreenOff: Flow<Boolean> = data.map { it[Keys.PROXIMITY_SCREEN_OFF] ?: true }

    /**
     * На экране вызова у сохранённого контакта — тип номера вместо
     * самого номера. По умолчанию — нет: номер показывается.
     */
    val callHideContactNumber: Flow<Boolean> = data.map { it[Keys.CALL_HIDE_CONTACT_NUMBER] ?: false }

    /**
     * Живой фон экрана вызова (1.5.1): медленные пятна цветов темы.
     * По умолчанию — да; при системном «Убрать анимацию» фон неподвижен
     * и без этой настройки (call/CallAmbientBackground.kt).
     */
    val callAnimation: Flow<Boolean> = data.map { it[Keys.CALL_ANIMATION] ?: true }

    /** Масштаб интерфейса (1.5.6): по умолчанию 90 % с 1.8.0 (было «Авто») — разбор в ui/theme/UiScale.kt. */
    val uiScale: Flow<UiScale> = data.map { UiScale.from(it[Keys.UI_SCALE]) }

    /**
     * ПОДСКАЗКА О СВАЙПАХ — ОДИН РАЗ (2.2.2).
     *
     * Форум, 12.11.2026: «не знал про свайп, я видел, что плашки двигаются,
     * но для чего, не понял, а там дальше чуть надо выдвинуть и отпустить».
     * Автор: «наверное, надо краткое обучение добавить. Я ради свайпов
     * и делал приложение». Полоса над журналом — пока человек не нажмёт
     * «Понятно» или не выполнит первый свайп; сброс настроек её возвращает.
     */
    val swipeHintDone: Flow<Boolean> = data.map { it[Keys.SWIPE_HINT_DONE] ?: false }

    suspend fun setSwipeHintDone() = edit("swipe_hint_done", true) { it[Keys.SWIPE_HINT_DONE] = true }

    /** Размер шрифта (2.2.1): множитель поверх системного, 0,8…1,6. */
    val fontScale: Flow<Float> = data.map { FontScale.clamp(it[Keys.FONT_SCALE] ?: FontScale.DEFAULT) }

    /** Фото звонящего на экране вызова (1.5.5): разбор в CallPhotoStyle. */
    val callPhotoStyle: Flow<CallPhotoStyle> = data.map { CallPhotoStyle.from(it[Keys.CALL_PHOTO_STYLE]) }

    /** Размер портрета на экране вызова (1.5.7): разбор в CallPhotoSize. */
    val callPhotoSize: Flow<CallPhotoSize> = data.map { CallPhotoSize.from(it[Keys.CALL_PHOTO_SIZE]) }

    /** Ответ на вызов (1.5.8): разбор в AnswerMode. */
    val answerMode: Flow<AnswerMode> = data.map { AnswerMode.from(it[Keys.ANSWER_MODE]) }

    /** Форма значков-виджетов «SMS» и «Контакты» (2.1.3): разбор в WidgetShape. */
    val widgetShape: Flow<WidgetShape> = data.map { WidgetShape.from(it[Keys.WIDGET_SHAPE]) }

    /** Как показывать имя контакта (1.6.2): разбор в ContactNameFormat. */
    val contactNameFormat: Flow<ContactNameFormat> =
        data.map { ContactNameFormat.from(it[Keys.CONTACT_NAME_FORMAT]) }

    /** Порядок списка контактов (1.6.2): разбор в ContactSort. */
    val contactSort: Flow<ContactSort> = data.map { ContactSort.from(it[Keys.CONTACT_SORT]) }

    /**
     * Аккаунт, в который человек сохранил последний новый контакт (1.7.0):
     * в редакторе он стоит первым. Пусто — ещё не выбирали.
     */
    val contactAccount: Flow<String> = data.map { it[Keys.CONTACT_ACCOUNT].orEmpty() }

    // ── Поведение ──────────────────────────────────────────────────────────
    val haptic: Flow<Boolean> = data.map { it[Keys.HAPTIC] ?: true }
    val keyTones: Flow<Boolean> = data.map { it[Keys.KEY_TONES] ?: true }
    val mergeCalls: Flow<Boolean> = data.map { it[Keys.MERGE_CALLS] ?: true }

    /**
     * По умолчанию CONSECUTIVE — так работают штатные диалеры,
     * и журнал читается как хронологическая лента. Режим BY_CONTACT
     * меняет смысл экрана на «список людей», это осознанный выбор.
     */
    override val groupingMode: Flow<GroupingMode> = data.map { p ->
        /*
         * По умолчанию объединяем ВСЕ вызовы абонента.
         *
         * Прежнее умолчание «подряд идущие» показывало одного человека
         * несколькими карточками: позвонил утром, потом кто-то другой,
         * потом он же вечером — три записи вместо одной. Журнал из-за
         * этого выглядел длиннее, чем есть, и найти нужного человека
         * было труднее.
         *
         * Режим «подряд идущие» остаётся в настройках: он полезен, когда
         * важна последовательность событий, а не итог общения.
         */
        runCatching { GroupingMode.valueOf(p[Keys.GROUPING_MODE] ?: GroupingMode.BY_CONTACT.name) }
            .getOrDefault(GroupingMode.BY_CONTACT)
    }
    val sortOrder: Flow<SortOrder> = data.map { p ->
        runCatching { SortOrder.valueOf(p[Keys.SORT_ORDER] ?: SortOrder.RELEVANCE.name) }
            .getOrDefault(SortOrder.RELEVANCE)
    }
    /** -1 означает «спрашивать» или «использовать системную SIM по умолчанию». */
    val defaultSim: Flow<Int> = data.map { it[Keys.DEFAULT_SIM] ?: -1 }

    /**
     * Имена слотов SIM, заданные пользователем.
     * Пустая строка означает «использовать системное имя».
     * Ключ карты — ИНДЕКС СЛОТА, а не номер телефона: номер может
     * отсутствовать в карте, слот есть всегда.
     */
    val simLabels: Flow<Map<Int, String>> = data.map { p ->
        buildMap {
            p[Keys.SIM_LABEL_1]?.takeIf { it.isNotBlank() }?.let { put(0, it) }
            p[Keys.SIM_LABEL_2]?.takeIf { it.isNotBlank() }?.let { put(1, it) }
        }
    }

    /**
     * Автоматическое переключение на громкую связь по датчикам.
     * Выключено по умолчанию: функция необычная, и внезапно заоравший
     * динамик на совещании пугает сильнее, чем радует удобство.
     */
    val smartSpeaker: Flow<Boolean> = data.map { it[Keys.SMART_SPEAKER] ?: false }

    /**
     * Автодозвон (1.8.0). По умолчанию — «Предлагать»: экран после «занято»
     * спрашивает, а без согласия ничего не набирается. Разбор — RedialPolicy.
     */
    val autoRedialMode: Flow<AutoRedialMode> = data.map { AutoRedialMode.from(it[Keys.AUTO_REDIAL]) }

    /** Сколько раз и как часто повторять (1.8.0): по умолчанию 5 раз через 10 с. */
    val autoRedialPace: Flow<RedialPace> = data.map { RedialPace.from(it[Keys.AUTO_REDIAL_PACE]) }

    /**
     * Памятка о мошенниках (1.8.0). ВКЛЮЧЕНА по умолчанию: она появляется
     * один раз за разговор и только с незнакомым номером дольше двух
     * минут — честный звонок курьера до неё не доживает, а тот, кому
     * она нужна, о настройке не узнает, пока не станет поздно.
     */
    val scamMemo: Flow<Boolean> = data.map { it[Keys.SCAM_MEMO] ?: true }

    /*
     * Сообщения (2.0.0). Умолчание — то же, что у SmsSettings: отчёт
     * о доставке — нет (часть операторов берёт за него плату).
     */
    val smsDeliveryReports: Flow<Boolean> = data.map { it[Keys.SMS_DELIVERY_REPORTS] ?: false }

    /** Отмена отправки SMS (2.2.0): по умолчанию 5 секунд. */
    val smsUndoSend: Flow<UndoSend> = data.map { UndoSend.from(it[Keys.SMS_UNDO_SEND]) }

    /** Звонки в ленте переписки (2.2.0): по умолчанию показываются. */
    val smsCallsInThread: Flow<Boolean> = data.map { it[Keys.SMS_CALLS_IN_THREAD] ?: true }

    /** Автоудаление кодов (2.2.0): по умолчанию выключено — удаление без спроса. */
    val smsOtpCleanup: Flow<OtpCleanup> = data.map { OtpCleanup.from(it[Keys.SMS_OTP_CLEANUP]) }

    /** Папки SMS (2.2.0): порядок и видимость строкой, разбор — SmsFolders. */
    val smsFolders: Flow<String?> = data.map { it[Keys.SMS_FOLDERS] }

    /**
     * Открывать клавиатуру сразу при запуске.
     *
     * Выключено по умолчанию: чаще приложение открывают посмотреть,
     * кто звонил, а не набрать номер. Тем, у кого наоборот,
     * переключатель экономит одно нажатие на каждом запуске.
     */
    val dialpadOpenAtStart: Flow<Boolean> =
        data.map { it[Keys.DIALPAD_OPEN_AT_START] ?: false }

    /**
     * ВХОДЯЩИЙ ПЛАШКОЙ СВЕРХУ (2.1.0). Включено — пока телефон
     * разблокирован и экран горит, входящий — плашкой с фото, «Ответить»
     * и «Отклонить» поверх того, чем человек занят (OgnelissInCallService).
     * Выключено — входящий всегда открывает экран вызова на весь экран.
     *
     * По умолчанию ВКЛЮЧЕНО (автор, 08.11.2026, после вопроса тестировщика):
     * так ведут себя стандартные звонилки. Где плашка может не всплыть или
     * не продержаться (IncomingScreen.bannerSafe) — экран вызова целиком
     * и при включённой настройке.
     */
    /*
     * 2.2.2 (форум, realme XT: «не работает плашка сверху при звонке… какие-то
     * разрешения дать?»). С 2.1.0 на MIUI/HyperOS, vivo и ColorOS (OPPO,
     * realme, OnePlus) плашка не показывалась вовсе — прошивка может её
     * не пропустить, — а переключатель при этом стоял включённым и ничего
     * не делал. Теперь на этих прошивках он по умолчанию ВЫКЛЮЧЕН (вид
     * соответствует делу), а включённый рукой — работает: человек решил
     * сам, и под переключателем сказано, что разрешить в телефоне.
     */
    val incomingBanner: Flow<Boolean> = data.map {
        it[Keys.INCOMING_BANNER] ?: !com.ogneliss.dialer.util.IncomingCallAccess.restrictiveShell
    }

    suspend fun setIncomingBanner(v: Boolean) = edit("incoming_banner", v) { it[Keys.INCOMING_BANNER] = v }

    /**
     * «ОТВЕТИТЬ» СЛЕВА (2.1.1, просьба пользователей через автора 09.11.2026).
     *
     * Обычно, как в большинстве звонилок, «Отклонить» слева, «Ответить»
     * справа. Привыкшим к другому телефону (часть прошивок ставит ответ
     * слева) — наоборот: кнопки экрана вызова и псевдозвонка меняются местами,
     * сдвиг к ответу — влево. Плашку и уведомление вызова раскладывает
     * система (CallStyle) — там порядок прежний, одинаковый во всех звонилках.
     */
    val swapAnswer: Flow<Boolean> = data.map { it[Keys.SWAP_ANSWER] ?: false }

    suspend fun setSwapAnswer(v: Boolean) = edit("swap_answer", v) { it[Keys.SWAP_ANSWER] = v }

    /**
     * ВИБРАЦИЯ ПРИ ОТВЕТЕ (2.5.0, форум: «добавить бы вибрацию при снятии трубки
     * вызываемым абонентом»). Исходящий соединился — короткая вибрация: телефон
     * у уха или в кармане с гарнитурой, гудки сменились тишиной, и не всегда
     * понятно, ответили ли уже. Включена по умолчанию: просили, а мешать ей
     * нечему — одно короткое касание за звонок.
     */
    val vibrateOnConnect: Flow<Boolean> = data.map { it[Keys.VIBRATE_ON_CONNECT] ?: true }

    suspend fun setVibrateOnConnect(v: Boolean) = edit("vibrate_on_connect", v) { it[Keys.VIBRATE_ON_CONNECT] = v }

    /** Предлагать напоминание после сброшенного вызова. */
    val callbackSuggestion: Flow<Boolean> =
        data.map { it[Keys.CALLBACK_SUGGESTION] ?: true }

    /**
     * СРОК ЖИЗНИ ЗАМЕТОК.
     *
     * По умолчанию не удалять: заметка — то, что человек написал сам,
     * и стирать её без спроса нельзя. Настройка для тех, у кого заметок
     * накопилось столько, что они мешают поиску.
     */
    val notesRetention: Flow<NotesRetention.Period> = data.map { p ->
        NotesRetention.Period.from(p[Keys.NOTES_RETENTION])
    }

    /**
     * Чьи заметки чистить.
     *
     * По умолчанию только номера вне книги: заметка о человеке
     * из контактов относится к живой связи, а не к разовому делу.
     */
    val notesRetentionScope: Flow<NotesRetention.Scope> = data.map { p ->
        NotesRetention.Scope.from(p[Keys.NOTES_RETENTION_SCOPE])
    }

    /** Ежемесячный аудит забытых контактов. */

    /**
     * ПРИНЯТАЯ РЕДАКЦИЯ ДОКУМЕНТОВ.
     *
     * ── Почему версия, а не булев флаг ─────────────────────────────────────
     * Документы меняются. Флаг «согласился» означал бы, что человек принял
     * условия, которых мог не видеть: обновили политику — а согласие
     * осталось от прежней редакции.
     *
     * Номер версии позволяет спросить заново именно тогда, когда условия
     * изменились существенно, и не дёргать людей при исправлении опечаток.
     */
    val consentVersion: Flow<Int> = data.map { it[Keys.CONSENT_VERSION] ?: 0 }

    /** Запоминать выбранную для номера SIM. */
    val rememberSim: Flow<Boolean> = data.map { it[Keys.REMEMBER_SIM] ?: true }

    // ── Экран после звонка ─────────────────────────────────────────────────
    /**
     * ЭКРАН ПОСЛЕ ЗВОНКА — ВЫКЛЮЧЕН ПО УМОЛЧАНИЮ.
     *
     * ── Почему выключен ────────────────────────────────────────────────────
     * Окно появляется в момент, когда разговор окончен и человек уже
     * переключился на другое дело: вернулся к работе, к собеседнику
     * рядом, к дороге. Всплывающее окно там перехватывает внимание,
     * которое ему уже не принадлежит.
     *
     * Заметку при этом можно записать в любой момент — из карточки
     * абонента или свайпом по записи журнала. Тем, кому это нужно
     * постоянно, включить настройку проще, чем всем остальным
     * отключать её после каждого звонка.
     */
    val postCallEnabled: Flow<Boolean> = data.map { it[Keys.POST_CALL_ENABLED] ?: false }
    /** По умолчанию true: всплывающее окно после КАЖДОГО звонка раздражает. */
    val postCallUnknownOnly: Flow<Boolean> =
        data.map { it[Keys.POST_CALL_UNKNOWN_ONLY] ?: true }

    // ── Антиспам ───────────────────────────────────────────────────────────
    val blockHidden: Flow<Boolean> = data.map { it[Keys.BLOCK_HIDDEN] ?: false }
    val blockNotInContacts: Flow<Boolean> = data.map { it[Keys.BLOCK_NOT_IN_CONTACTS] ?: false }
    /** Блокировать номера, помеченные пользователем как спам. Включено по умолчанию:
     *  пользователь ставил метку осознанно, значит ждёт от неё эффекта. */
    val blockTaggedSpam: Flow<Boolean> = data.map { it[Keys.BLOCK_TAGGED_SPAM] ?: true }

    /** Тихие часы для незнакомых (1.8.0). Выключены по умолчанию: правило для тех, кто его выбрал. */
    val quietHours: Flow<QuietHours> = data.map { QuietHours.from(it[Keys.QUIET_HOURS]) }
    /** Эвристическая автоблокировка. ВЫКЛЮЧЕНА по умолчанию: ложное
     *  срабатывание вредит сильнее, чем пропущенный спам. */
    /** Порог 0..100. Высокий по умолчанию — консервативно. */
    /** Идентификаторы явно разрешённых внешних определителей. Пусто = все выключены. */
    val externalLookupIds: Flow<Set<String>> =
        data.map { it[Keys.EXTERNAL_LOOKUP_IDS] ?: emptySet() }

    // ── Диагностика ────────────────────────────────────────────────────────
    val fileLogging: Flow<Boolean> = data.map { it[Keys.FILE_LOGGING] ?: true }

    // ── Служебное ──────────────────────────────────────────────────────────
    val onboardingDone: Flow<Boolean> = data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /**
     * ПОДСКАЗКА О ЖЕСТАХ УЖЕ ПОКАЗАНА.
     *
     * Отдельно от onboardingDone: тот отвечает за мастер разрешений,
     * а его человек мог пройти в прошлой версии — до появления жестов.
     * Общий признак означал бы, что подсказку не увидит никто из тех,
     * кто обновился.
     */
    val gestureHintShown: Flow<Boolean> = data.map { it[Keys.GESTURE_HINT_SHOWN] ?: false }

    /**
     * СОБСТВЕННЫЙ НОМЕР.
     *
     * Пустая строка означает «не указан» — и это обычное состояние,
     * а не сбой: телефон свой номер знает далеко не всегда. Оператор
     * записывает его на SIM-карту по желанию, и многие не записывают.
     *
     * Нужен, чтобы поделиться своей карточкой.
     */
    val ownNumber: Flow<String> = data.map { it[Keys.OWN_NUMBER].orEmpty() }

    // ── Сеттеры ────────────────────────────────────────────────────────────
    suspend fun setSwipeRightExtra(actions: List<SwipeAction>) =
        edit("swipe_right_extra", actions.size) {
            it[Keys.SWIPE_RIGHT_EXTRA] = actions.joinToString(",") { a -> a.key }
        }

    suspend fun setSwipeLeftExtra(actions: List<SwipeAction>) =
        edit("swipe_left_extra", actions.size) {
            it[Keys.SWIPE_LEFT_EXTRA] = actions.joinToString(",") { a -> a.key }
        }

    suspend fun setSwipeTuning(tuning: SwipeTuning) =
        edit("swipe_tuning", tuning.triggerDistanceDp) {
            it[Keys.SWIPE_TRIGGER_DP] = tuning.triggerDistanceDp
            it[Keys.SWIPE_POSITIONAL] = tuning.positionalThresholdPercent
            it[Keys.SWIPE_VELOCITY] = tuning.velocityDpPerSecond
            it[Keys.SWIPE_HORIZONTAL_START] = tuning.requireHorizontalStart
        }

    /**
     * Сброс параметров свайпа.
     *
     * Ключи удаляются, а не перезаписываются значениями по умолчанию:
     * так настройка снова следует за кодом. Если значения по умолчанию
     * изменятся в следующей версии, пользователь получит новые,
     * а не замороженные старые.
     */
    suspend fun resetSwipeTuning() = edit("swipe_tuning_reset", true) {
        it.remove(Keys.SWIPE_TRIGGER_DP)
        it.remove(Keys.SWIPE_POSITIONAL)
        it.remove(Keys.SWIPE_VELOCITY)
        it.remove(Keys.SWIPE_HORIZONTAL_START)
    }

    suspend fun setSwipeRight(v: SwipeAction) = edit("swipe_right", v.key) { it[Keys.SWIPE_RIGHT] = v.key }
    suspend fun setSwipeLeft(v: SwipeAction) = edit("swipe_left", v.key) { it[Keys.SWIPE_LEFT] = v.key }
    suspend fun setPinnedNumbers(numbers: List<String>) {
        edit("pinned_numbers", numbers.size) {
            it[Keys.PINNED_NUMBERS] = numbers.joinToString(",")
        }
        // Виджет «Закреплённые» (2.2.0) — сразу с новым списком
        com.ogneliss.dialer.widget.PinnedWidget.update(context)
    }

    suspend fun setPanelSwipeNav(v: Boolean) =
        edit("panel_swipe_nav", v) { it[Keys.PANEL_SWIPE_NAV] = v }

    suspend fun setSimCache(json: String) =
        edit("sim_cache", "…") { it[Keys.SIM_CACHE] = json }

    suspend fun setFakeCallAt(at: Long) =
        edit("fake_call_at", at) { it[Keys.FAKE_CALL_AT] = at }

    suspend fun setFakeCallButton(v: Boolean) =
        edit("fake_call_button", v) { it[Keys.FAKE_CALL_BUTTON] = v }

    suspend fun setFakeCallNumber(number: String) =
        edit("fake_call_number", number) { it[Keys.FAKE_CALL_NUMBER] = number }

    suspend fun setCarrierInfo(mode: CarrierInfoMode) =
        edit("carrier_info", mode.name) { it[Keys.CARRIER_INFO] = mode.name }

    suspend fun setJournalStyle(style: JournalStyle) =
        edit("journal_style", style.name) { it[Keys.JOURNAL_STYLE] = style.name }

    suspend fun setSwipeSimMode(mode: SwipeSimMode) =
        edit("swipe_sim_mode", mode.name) { it[Keys.SWIPE_SIM_MODE] = mode.name }

    suspend fun setLanguage(tag: String) =
        edit("language", tag) { it[Keys.LANGUAGE] = tag }

    suspend fun setTheme(v: ThemeMode) = edit("theme", v.name) { it[Keys.THEME] = v.name }
    suspend fun setThemePreset(v: ThemePreset) =
        edit("theme_preset", v.name) { it[Keys.THEME_PRESET] = v.name }
    /** null — акцент цветовой темы: ключ удаляется. */
    suspend fun setAccentColor(v: Int?) = edit("accent_color", v?.let { Integer.toHexString(it) }) {
        if (v == null) {
            it.remove(Keys.ACCENT_COLOR)
        } else {
            it[Keys.ACCENT_COLOR] = v
        }
    }
    suspend fun setCornerSize(v: CornerSize) = edit("corner_size", v.name) { it[Keys.CORNER_SIZE] = v.name }
    suspend fun setListDensity(v: ListDensity) = edit("list_density", v.name) { it[Keys.LIST_DENSITY] = v.name }
    suspend fun setUiFont(v: UiFont) = edit("ui_font", v.name) { it[Keys.UI_FONT] = v.name }
    suspend fun setDynamicColor(v: Boolean) = edit("dynamic_color", v) { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setShowPhoto(v: Boolean) = edit("show_photo", v) { it[Keys.SHOW_PHOTO] = v }
    suspend fun setMessengersAnyNumber(v: Boolean) = edit("messengers_any_number", v) { it[Keys.MESSENGERS_ANY_NUMBER] = v }
    suspend fun setLargeButtons(v: Boolean) = edit("large_buttons", v) { it[Keys.LARGE_BUTTONS] = v }
    suspend fun setButtonLabels(v: Boolean) = edit("button_labels", v) { it[Keys.BUTTON_LABELS] = v }
    suspend fun setProximityScreenOff(v: Boolean) =
        edit("proximity_screen_off", v) { it[Keys.PROXIMITY_SCREEN_OFF] = v }
    suspend fun setCallHideContactNumber(v: Boolean) =
        edit("call_hide_contact_number", v) { it[Keys.CALL_HIDE_CONTACT_NUMBER] = v }
    suspend fun setCallAnimation(v: Boolean) =
        edit("call_animation", v) { it[Keys.CALL_ANIMATION] = v }
    suspend fun setFontScale(v: Float) =
        edit("font_scale", v) { it[Keys.FONT_SCALE] = FontScale.clamp(v) }
    suspend fun setUiScale(v: UiScale) =
        edit("ui_scale", v.name) { it[Keys.UI_SCALE] = v.name }
    suspend fun setCallPhotoStyle(v: CallPhotoStyle) =
        edit("call_photo_style", v.name) { it[Keys.CALL_PHOTO_STYLE] = v.name }
    suspend fun setCallPhotoSize(v: CallPhotoSize) =
        edit("call_photo_size", v.name) { it[Keys.CALL_PHOTO_SIZE] = v.name }
    suspend fun setAnswerMode(v: AnswerMode) =
        edit("answer_mode", v.name) { it[Keys.ANSWER_MODE] = v.name }
    suspend fun setWidgetShape(v: WidgetShape) =
        edit("widget_shape", v.name) { it[Keys.WIDGET_SHAPE] = v.name }
    suspend fun setContactNameFormat(v: ContactNameFormat) =
        edit("contact_name_format", v.name) { it[Keys.CONTACT_NAME_FORMAT] = v.name }
    suspend fun setContactSort(v: ContactSort) =
        edit("contact_sort", v.name) { it[Keys.CONTACT_SORT] = v.name }
    suspend fun setContactAccount(key: String) =
        edit("contact_account", key.substringBefore('\u0000')) { it[Keys.CONTACT_ACCOUNT] = key }
    suspend fun setHaptic(v: Boolean) = edit("haptic", v) { it[Keys.HAPTIC] = v }
    suspend fun setKeyTones(v: Boolean) = edit("key_tones", v) { it[Keys.KEY_TONES] = v }
    suspend fun setGroupingMode(v: GroupingMode) =
        edit("grouping_mode", v.name) { it[Keys.GROUPING_MODE] = v.name }
    suspend fun setMergeCalls(v: Boolean) = edit("merge_calls", v) { it[Keys.MERGE_CALLS] = v }
    suspend fun setSortOrder(v: SortOrder) = edit("sort_order", v.name) { it[Keys.SORT_ORDER] = v.name }
    suspend fun setDefaultSim(v: Int) = edit("default_sim", v) { it[Keys.DEFAULT_SIM] = v }
    suspend fun setSimLabel(slotIndex: Int, label: String) = edit("sim_label", label) {
        when (slotIndex) {
            0 -> it[Keys.SIM_LABEL_1] = label
            1 -> it[Keys.SIM_LABEL_2] = label
        }
    }

    suspend fun setDialpadOpenAtStart(v: Boolean) =
        edit("dialpad_open_at_start", v) { it[Keys.DIALPAD_OPEN_AT_START] = v }

    suspend fun setSmartSpeaker(v: Boolean) =
        edit("smart_speaker", v) { it[Keys.SMART_SPEAKER] = v }
    suspend fun setCallbackSuggestion(v: Boolean) =
        edit("callback_suggestion", v) { it[Keys.CALLBACK_SUGGESTION] = v }
    suspend fun setAutoRedialMode(v: AutoRedialMode) =
        edit("auto_redial", v.name) { it[Keys.AUTO_REDIAL] = v.name }
    suspend fun setAutoRedialPace(v: RedialPace) =
        edit("auto_redial_pace", v.name) { it[Keys.AUTO_REDIAL_PACE] = v.name }
    suspend fun setScamMemo(v: Boolean) = edit("scam_memo", v) { it[Keys.SCAM_MEMO] = v }

    /*
     * Настройки сообщений повторяются в SmsSettings (SharedPreferences):
     * приёмник SMS и кнопки «Написать» решают сразу, без чтения DataStore.
     */
    suspend fun setSmsDeliveryReports(v: Boolean) {
        edit("sms_delivery_reports", v) { it[Keys.SMS_DELIVERY_REPORTS] = v }
        com.ogneliss.dialer.sms.SmsSettings.put(context, com.ogneliss.dialer.sms.SmsSettings.KEY_DELIVERY_REPORTS, v)
    }
    // 2.2.0: копия в SmsSettings — для приёмника SMS и очереди без корутин
    suspend fun setSmsUndoSend(v: UndoSend) {
        edit("sms_undo_send", v.name) { it[Keys.SMS_UNDO_SEND] = v.name }
        com.ogneliss.dialer.sms.SmsSettings.putInt(context, com.ogneliss.dialer.sms.SmsSettings.KEY_UNDO_SECONDS, v.seconds)
    }
    suspend fun setSmsFolders(v: String) = edit("sms_folders", v) { it[Keys.SMS_FOLDERS] = v }

    /** Папки журнала и контактов в «Меню»: порядок и показ (2.5.0, разбор в FolderOrder). */
    val journalFolders: Flow<String?> = data.map { it[Keys.JOURNAL_FOLDERS] }
    val contactFolders: Flow<String?> = data.map { it[Keys.CONTACT_FOLDERS] }

    suspend fun setJournalFolders(v: String) = edit("journal_folders", v) { it[Keys.JOURNAL_FOLDERS] = v }

    suspend fun setContactFolders(v: String) = edit("contact_folders", v) { it[Keys.CONTACT_FOLDERS] = v }
    suspend fun setSmsCallsInThread(v: Boolean) =
        edit("sms_calls_in_thread", v) { it[Keys.SMS_CALLS_IN_THREAD] = v }
    suspend fun setSmsOtpCleanup(v: OtpCleanup) {
        edit("sms_otp_cleanup", v.name) { it[Keys.SMS_OTP_CLEANUP] = v.name }
        com.ogneliss.dialer.sms.SmsSettings.putInt(context, com.ogneliss.dialer.sms.SmsSettings.KEY_OTP_HOURS, v.hours)
    }
    suspend fun setQuietHours(v: QuietHours) =
        edit("quiet_hours", v.name) { it[Keys.QUIET_HOURS] = v.name }
    suspend fun setNotesRetention(v: NotesRetention.Period) =
        edit("notes_retention", v.name) { it[Keys.NOTES_RETENTION] = v.name }

    suspend fun setNotesRetentionScope(v: NotesRetention.Scope) =
        edit("notes_retention_scope", v.name) { it[Keys.NOTES_RETENTION_SCOPE] = v.name }

    suspend fun setConsentVersion(v: Int) =
        edit("consent_version", v) { it[Keys.CONSENT_VERSION] = v }

    suspend fun setRememberSim(v: Boolean) = edit("remember_sim", v) { it[Keys.REMEMBER_SIM] = v }
    suspend fun setPostCallEnabled(v: Boolean) = edit("post_call", v) { it[Keys.POST_CALL_ENABLED] = v }
    suspend fun setPostCallUnknownOnly(v: Boolean) =
        edit("post_call_unknown_only", v) { it[Keys.POST_CALL_UNKNOWN_ONLY] = v }
    suspend fun setBlockHidden(v: Boolean) = edit("block_hidden", v) { it[Keys.BLOCK_HIDDEN] = v }
    suspend fun setBlockNotInContacts(v: Boolean) =
        edit("block_not_in_contacts", v) { it[Keys.BLOCK_NOT_IN_CONTACTS] = v }
    suspend fun setBlockTaggedSpam(v: Boolean) =
        edit("block_tagged_spam", v) { it[Keys.BLOCK_TAGGED_SPAM] = v }
    suspend fun setExternalLookupIds(v: Set<String>) =
        edit("external_lookup_ids", v.size) { it[Keys.EXTERNAL_LOOKUP_IDS] = v }
    suspend fun setFileLogging(v: Boolean) = edit("file_logging", v) { it[Keys.FILE_LOGGING] = v }
    suspend fun setOnboardingDone(v: Boolean) =
        edit("onboarding_done", v) { it[Keys.ONBOARDING_DONE] = v }

    suspend fun setOwnNumber(value: String) =
        edit("own_number", value) { it[Keys.OWN_NUMBER] = value }

    suspend fun setGestureHintShown(v: Boolean) =
        edit("gesture_hint_shown", v) { it[Keys.GESTURE_HINT_SHOWN] = v }

    /**
     * Сброс всех настроек к значениям по умолчанию.
     * Данные (заметки, списки) при этом НЕ удаляются — они в базе.
     */
    suspend fun resetAll() {
        runCatching { context.dataStore.edit { it.clear() } }
            .onSuccess {
                Logger.i(TAG, "Настройки сброшены к значениям по умолчанию")
                // Копия настроек SMS для мест без корутин (2.2.0) — тоже к умолчаниям,
                // иначе отмена отправки и автоудаление кодов остались бы прежними
                com.ogneliss.dialer.sms.SmsSettings.putInt(
                    context, com.ogneliss.dialer.sms.SmsSettings.KEY_UNDO_SECONDS, UndoSend.from(null).seconds
                )
                com.ogneliss.dialer.sms.SmsSettings.putInt(
                    context, com.ogneliss.dialer.sms.SmsSettings.KEY_OTP_HOURS, OtpCleanup.from(null).hours
                )
            }
            .onFailure { Logger.e(TAG, "Не удалось сбросить настройки", it) }
    }

    /**
     * Общая обёртка записи: логирует изменение и не даёт исключению
     * из хранилища уронить вызывающий код.
     */
    private suspend fun edit(name: String, value: Any?, block: (MutablePreferences) -> Unit) {
        runCatching { context.dataStore.edit(block) }
            .onSuccess { Logger.d(TAG, "Настройка '$name' = $value") }
            .onFailure { Logger.e(TAG, "Не удалось сохранить настройку '$name'", it) }
    }
}
