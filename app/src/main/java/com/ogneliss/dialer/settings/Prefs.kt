package com.ogneliss.dialer.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ogneliss.dialer.data.GroupingSettings
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.data.PinnedContacts
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
        // Оформление
        val THEME = stringPreferencesKey("theme")
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
        /** Шапка меню записи в цвете темы (1.3.5, по умолчанию вкл.). */
        val SHEET_HEADER_TINT = booleanPreferencesKey("sheet_header_tint")
        /** «Написать»: установленные мессенджеры для любого номера (1.3.5, выкл.). */
        val MESSENGERS_ANY_NUMBER = booleanPreferencesKey("messengers_any_number")
        val LARGE_BUTTONS = booleanPreferencesKey("large_buttons")
        val BUTTON_LABELS = booleanPreferencesKey("button_labels")
        val PROXIMITY_SCREEN_OFF = booleanPreferencesKey("proximity_screen_off")
        val CALL_HIDE_CONTACT_NUMBER = booleanPreferencesKey("call_hide_contact_number")
        /** Живой фон экрана вызова (1.5.1, по умолчанию вкл.). */
        val CALL_ANIMATION = booleanPreferencesKey("call_animation")
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
        val NOTES_RETENTION = stringPreferencesKey("notes_retention")
        val NOTES_RETENTION_SCOPE = stringPreferencesKey("notes_retention_scope")
        // Экран после звонка
        val POST_CALL_ENABLED = booleanPreferencesKey("post_call_enabled")
        val POST_CALL_UNKNOWN_ONLY = booleanPreferencesKey("post_call_unknown_only")
        // Антиспам
        val BLOCK_HIDDEN = booleanPreferencesKey("block_hidden")
        val BLOCK_NOT_IN_CONTACTS = booleanPreferencesKey("block_not_in_contacts")
        val BLOCK_TAGGED_SPAM = booleanPreferencesKey("block_tagged_spam")
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
    val sheetHeaderTint: Flow<Boolean> = data.map { it[Keys.SHEET_HEADER_TINT] ?: true }
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
     * Открывать клавиатуру сразу при запуске.
     *
     * Выключено по умолчанию: чаще приложение открывают посмотреть,
     * кто звонил, а не набрать номер. Тем, у кого наоборот,
     * переключатель экономит одно нажатие на каждом запуске.
     */
    val dialpadOpenAtStart: Flow<Boolean> =
        data.map { it[Keys.DIALPAD_OPEN_AT_START] ?: false }

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
    suspend fun setPinnedNumbers(numbers: List<String>) =
        edit("pinned_numbers", numbers.size) {
            it[Keys.PINNED_NUMBERS] = numbers.joinToString(",")
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
    suspend fun setDynamicColor(v: Boolean) = edit("dynamic_color", v) { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setShowPhoto(v: Boolean) = edit("show_photo", v) { it[Keys.SHOW_PHOTO] = v }
    suspend fun setSheetHeaderTint(v: Boolean) = edit("sheet_header_tint", v) { it[Keys.SHEET_HEADER_TINT] = v }
    suspend fun setMessengersAnyNumber(v: Boolean) = edit("messengers_any_number", v) { it[Keys.MESSENGERS_ANY_NUMBER] = v }
    suspend fun setLargeButtons(v: Boolean) = edit("large_buttons", v) { it[Keys.LARGE_BUTTONS] = v }
    suspend fun setButtonLabels(v: Boolean) = edit("button_labels", v) { it[Keys.BUTTON_LABELS] = v }
    suspend fun setProximityScreenOff(v: Boolean) =
        edit("proximity_screen_off", v) { it[Keys.PROXIMITY_SCREEN_OFF] = v }
    suspend fun setCallHideContactNumber(v: Boolean) =
        edit("call_hide_contact_number", v) { it[Keys.CALL_HIDE_CONTACT_NUMBER] = v }
    suspend fun setCallAnimation(v: Boolean) =
        edit("call_animation", v) { it[Keys.CALL_ANIMATION] = v }
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
            .onSuccess { Logger.i(TAG, "Настройки сброшены к значениям по умолчанию") }
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
