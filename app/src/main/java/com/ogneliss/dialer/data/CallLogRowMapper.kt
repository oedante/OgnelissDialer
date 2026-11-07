package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.CarrierInfoMode
import com.ogneliss.dialer.ui.components.CallLogRowState
import com.ogneliss.dialer.util.PhoneFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ПОДГОТОВКА СТРОК ЖУРНАЛА К ОТРИСОВКЕ.
 *
 * ── Почему это делается заранее, а не в карточке ───────────────────────────
 * Форматирование номера через libphonenumber — это разбор строки
 * и обращение к офлайн-справочнику. Операция недорогая по отдельности,
 * но на экране помещается около десяти карточек, и при прокрутке
 * они перерисовываются десятки раз в секунду.
 *
 * Считать одно и то же по шестьсот раз в секунду — прямая причина рывков,
 * которые пользователь описывает как «интерфейс тормозит».
 *
 * Здесь всё считается один раз на группу, при обновлении списка.
 *
 * ── Почему форматтер даты передаётся снаружи ───────────────────────────────
 * Он зависит от локали, а её знает только слой интерфейса. Создавать
 * форматтер на каждую строку — ещё одна дорогая операция, поэтому
 * на пересборку берётся одна его копия (разбор у [Render]).
 */
object CallLogRowMapper {

    /**
     * ИНКРЕМЕНТАЛЬНАЯ ПОДГОТОВКА.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Подгрузка страницы добавляет к списку сто пятьдесят записей,
     * а пересобирался он целиком: три тысячи строк заново разбирались
     * через libphonenumber ради ста пятидесяти новых. То же при каждом
     * звонке: журнал перечитывается, и меняется в нём одна строка.
     *
     * Здесь готовые строки переиспользуются: карточка пересобирается
     * только если изменились данные, от которых она зависит.
     *
     * ── Как определяется «изменилось» ──────────────────────────────────────
     * У строки есть отпечаток всего, от чего зависит её вид (разбор
     * у [Inputs.signatureOf]). Совпал с отпечатком готовой строки того же
     * ключа — строка берётся как есть, без разбора номера и дат.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ДО 1.8.0 ПЕРЕИСПОЛЬЗОВАНИЕ НЕ СРАБАТЫВАЛО НИКОГДА
     * ══════════════════════════════════════════════════════════════════════
     * Отпечаток для сравнения считался с добавкой «номер в книге» (1.3.5),
     * а в готовой строке хранился без неё. Числа не совпадали, и журнал
     * при каждом обновлении собирался целиком — а кэш форматтера номеров
     * держит 512 номеров, так что большой журнал каждый раз разбирался
     * через libphonenumber заново. Теперь отпечаток считается одной
     * функцией — и для записи в строку, и для сравнения.
     *
     * ── Чего отпечаток не покрывает ────────────────────────────────────────
     * Подписи, формат даты, часовой пояс, регион номеров — общие для всех
     * строк. Их сверяет [Context]: поменялись (сменили язык) — вызывающий
     * передаёт пустой [previous], и список собирается заново.
     *
     * @param days границы «Сегодня» и «Вчера»; снаружи — только в тестах
     */
    fun mapIncremental(
        groups: List<CallLogGroup>,
        previous: List<CallLogRowState>,
        numberInfo: Map<String, NumberInfo>,
        knownNumbers: Set<String>,
        simSlots: Map<String, Int>,
        /** Фотографии из книги по номеру в E.164. */
        contactPhotos: Map<String, String>,
        /**
         * Подписи о блокировке по номеру — или по ключу строки, для пометки
         * одного звонка (тихие часы, BlockedCallLog.silencedRows).
         *
         * Готовые строки, а не сами причины: формирование текста требует
         * контекста, а маппер работает в фоне и о ресурсах не знает.
         */
        blockedLabels: Map<String, String> = emptyMap(),
        notes: Map<String, NumberNote>,
        dateFormat: SimpleDateFormat,
        labels: Labels,
        /** Где показывать оператора и регион — из настроек. */
        carrierInfo: CarrierInfoMode = CarrierInfoMode.UNKNOWN_ONLY,
        /** Номера с назначенным напоминанием: у них в строке значок. */
        remindedNumbers: Set<String> = emptySet(),
        /** «Фамилия Имя» (1.6.2): ContactNames.journalNames; пусто — имя как в журнале. */
        contactNames: Map<Pair<String, String>, String> = emptyMap(),
        /** Имена из книги по номеру (1.7.0): разбор у nameOf. */
        bookNames: Map<String, String> = emptyMap(),
        /** Имена от оператора по номеру (1.8.0): разбор у networkNameOf. */
        networkNames: Map<String, String> = emptyMap(),
        days: Days = Days()
    ): List<CallLogRowState> {
        val inputs = Inputs(
            numberInfo, knownNumbers, simSlots, contactPhotos, blockedLabels, notes,
            carrierInfo, remindedNumbers, contactNames, bookNames, networkNames, days
        )
        // Форматтеры — только если хоть одна строка собирается заново
        val render = lazy(LazyThreadSafetyMode.NONE) { Render(dateFormat, labels, days) }
        val byKey = if (previous.isEmpty()) emptyMap() else previous.associateBy { it.key }

        return groups.map { group ->
            /*
             * Сравнение отпечатка, а не пересчёт полей.
             *
             * Проверять «изменилось ли что-нибудь» через те же функции,
             * что готовят строку, значило бы выполнить работу дважды —
             * и оптимизация превратилась бы в замедление. Целое число
             * сравнивается за наносекунды.
             */
            val signature = inputs.signatureOf(group)
            val cached = byKey[group.key]
            if (cached != null && cached.signature == signature) cached
            else mapOne(group, inputs, render.value, signature)
        }
    }

    /** Полная сборка — без готовых строк. */
    fun map(
        groups: List<CallLogGroup>,
        numberInfo: Map<String, NumberInfo>,
        knownNumbers: Set<String>,
        simSlots: Map<String, Int>,
        /** Фотографии из книги по номеру в E.164. */
        contactPhotos: Map<String, String>,
        /**
         * Подписи о блокировке по номеру (или по ключу строки).
         *
         * Готовые строки, а не причины: текст требует контекста,
         * а маппер работает в фоне и о ресурсах не знает.
         */
        blockedLabels: Map<String, String> = emptyMap(),
        /** Заметки пользователя по номерам в формате E.164. */
        notes: Map<String, NumberNote>,
        dateFormat: SimpleDateFormat,
        labels: Labels,
        /** Где показывать оператора и регион — из настроек. */
        carrierInfo: CarrierInfoMode = CarrierInfoMode.UNKNOWN_ONLY,
        /** Номера с назначенным напоминанием: у них в строке значок. */
        remindedNumbers: Set<String> = emptySet(),
        /** «Фамилия Имя» (1.6.2): ContactNames.journalNames; пусто — имя как в журнале. */
        contactNames: Map<Pair<String, String>, String> = emptyMap(),
        /** Имена из книги по номеру (1.7.0): разбор у nameOf. */
        bookNames: Map<String, String> = emptyMap(),
        /** Имена от оператора по номеру (1.8.0): разбор у networkNameOf. */
        networkNames: Map<String, String> = emptyMap(),
        days: Days = Days()
    ): List<CallLogRowState> = mapIncremental(
        groups, emptyList(), numberInfo, knownNumbers, simSlots, contactPhotos,
        blockedLabels, notes, dateFormat, labels, carrierInfo, remindedNumbers,
        contactNames, bookNames, networkNames, days
    )

    /**
     * ОБСТАНОВКА, В КОТОРОЙ СОБРАНЫ СТРОКИ (1.8.0).
     *
     * Подписи («Сегодня», «Входящий», «по данным оператора»), формат даты,
     * часовой пояс и регион, по которому форматируются номера, — общие
     * для всех строк, поэтому в отпечаток строки не входят. Поменялись —
     * сменили язык, переехали, вставили SIM другой страны, — и готовые
     * строки показывали бы прежнее. Вызывающий сравнивает обстановку
     * прошлой сборки с нынешней и при несовпадении собирает всё заново.
     *
     * Формат даты сравнивается по значению (SimpleDateFormat.equals: шаблон,
     * язык, часовой пояс), подписи — по отпечатку [Labels.fingerprint].
     */
    data class Context(
        val labels: Int,
        val dateFormat: SimpleDateFormat,
        val timeZone: String,
        val region: String
    )

    /** Обстановка нынешней сборки; null — подписи без отпечатка, готовые строки не годятся. */
    fun contextOf(labels: Labels, dateFormat: SimpleDateFormat): Context? {
        if (labels.fingerprint == 0) return null
        return Context(
            labels = labels.fingerprint,
            dateFormat = dateFormat.clone() as SimpleDateFormat,
            timeZone = TimeZone.getDefault().id,
            region = PhoneFormatter.defaultRegion
        )
    }

    /**
     * ВСЁ, ОТ ЧЕГО ЗАВИСИТ СТРОКА, КРОМЕ САМОЙ ГРУППЫ И ПОДПИСЕЙ.
     *
     * Собрано в одно место, чтобы отпечаток ([signatureOf]) и сама строка
     * ([mapOne]) читали одно и то же: всё, что попадает в строку, должно
     * попадать и в отпечаток — иначе строка возьмётся из прошлого списка
     * со старым видом.
     */
    private class Inputs(
        val numberInfo: Map<String, NumberInfo>,
        val knownNumbers: Set<String>,
        val simSlots: Map<String, Int>,
        val contactPhotos: Map<String, String>,
        val blockedLabels: Map<String, String>,
        val notes: Map<String, NumberNote>,
        val carrierInfo: CarrierInfoMode,
        val remindedNumbers: Set<String>,
        val contactNames: Map<Pair<String, String>, String>,
        val bookNames: Map<String, String>,
        val networkNames: Map<String, String>,
        val days: Days
    ) {

        /**
         * Отпечаток данных, влияющих на вид строки.
         *
         * Один на запись и на сравнение — разбор у [mapIncremental].
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО В НЁМ И ПОЧЕМУ
         * ══════════════════════════════════════════════════════════════════
         * • Группа целиком (1.8.0). Раньше — ключ и число вызовов, а имя
         *   из журнала, фото из кэша журнала, подпись SIM в отпечаток
         *   не входили. Ключ включает номер последнего вызова, и почти
         *   любое изменение группы меняет его, — но «почти» здесь означает
         *   строку со старым видом. Группа — data class, её отпечаток
         *   считается за доли миллисекунды на весь журнал.
         * • Заметка и сведения определителя — целиком (раньше — время
         *   заметки и категория номера): оператор и регион в подписи
         *   тоже из сведений.
         * • «Номер в книге» (1.3.5): номер сохранили в контакты — строка
         *   пересобирается и получает буквы.
         * • Подпись о блокировке. Блокировка номера не меняет ни число
         *   вызовов, ни заметку, ни фотографию, ни SIM. Без неё отпечаток
         *   совпадал, строка бралась из прошлого списка вместе со старым
         *   (пустым) полем blockedLabel, и пометка в журнале не появлялась;
         *   разблокировка ломалась так же, только наоборот.
         * • Режим показа оператора: иначе переключение настройки не меняло
         *   бы уже построенные строки — та же ошибка, что с блокировкой.
         * • Напоминание: поставили — в строке появился значок, а данные
         *   вызова не изменились.
         * • Имя для показа (1.6.2): переключили «Как показывать имя».
         * • Имя от оператора (1.8.0): пришло после звонка.
         * • Календарный день звонка (1.8.0): «Сегодня, 18:02» после полуночи
         *   должно стать «Вчера, 18:02». Только день строки, а не дата
         *   сборки: в полночь пересобираются звонки последних двух дней,
         *   а не весь журнал.
         */
        fun signatureOf(group: CallLogGroup): Int {
            var result = group.hashCode()
            result = 31 * result + (notes[group.e164]?.hashCode() ?: 0)
            result = 31 * result + (numberInfo[group.e164]?.hashCode() ?: 0)
            result = 31 * result + (if (group.e164 in knownNumbers) 1 else 0)
            result = 31 * result + (contactPhotos[group.e164]?.hashCode() ?: 0)
            result = 31 * result + (simSlots[group.subscriptionId] ?: -1)
            result = 31 * result + (blockedLabelOf(group, blockedLabels)?.hashCode() ?: 0)
            result = 31 * result + carrierInfo.ordinal
            result = 31 * result + (if (group.e164 in remindedNumbers) 1 else 0)
            result = 31 * result + (nameOf(group, contactNames, bookNames)?.hashCode() ?: 0)
            result = 31 * result + (networkNames[group.e164]?.hashCode() ?: 0)
            result = 31 * result + days.of(group.lastDate).ordinal
            return result
        }
    }

    /**
     * Подписи и форматы на одну пересборку.
     *
     * ── Почему копии форматтеров ───────────────────────────────────────────
     * SimpleDateFormat не потокобезопасен, а пересборки идут в пуле Default:
     * новая начинается, пока прежняя ещё досчитывает отменённую работу.
     * Общий форматтер на двоих путал бы цифры дат. Копия на пересборку —
     * одно выделение памяти, и только если строку правда надо собрать.
     *
     * Часовой пояс — нынешний: форматтер из интерфейса запомнил тот,
     * что был при его создании, а «Сегодня» считается по нынешнему.
     */
    private class Render(dateFormat: SimpleDateFormat, val labels: Labels, private val days: Days) {
        private val zone = TimeZone.getDefault()
        private val date = (dateFormat.clone() as SimpleDateFormat).apply { timeZone = zone }

        /** Время без даты: подставляется в «Сегодня, …». */
        private val time = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = zone }

        /*
         * ДАТА СЛОВАМИ ДЛЯ БЛИЖАЙШИХ ДНЕЙ.
         *
         * «5 сент. 2026 г. 18:02» человек читает и сравнивает с сегодняшним
         * числом в уме. «Сегодня, 18:02» сравнивать не надо — а именно
         * вчерашние и сегодняшние звонки смотрят чаще всего.
         *
         * Дальше двух дней слова бесполезны: «12 дней назад» хуже даты,
         * потому что по дате видно и день недели, и месяц.
         *
         * Подписи приходят из labels: строки берутся из ресурсов,
         * а маппер работает без контекста и локали.
         */
        fun dateLabel(millis: Long): String = when (days.of(millis)) {
            Day.TODAY -> labels.today(time.format(Date(millis)))
            Day.YESTERDAY -> labels.yesterday(time.format(Date(millis)))
            Day.EARLIER -> date.format(Date(millis))
        }
    }

    /** Календарный день звонка — для подписи даты. */
    enum class Day { TODAY, YESTERDAY, EARLIER }

    /**
     * ГРАНИЦЫ «СЕГОДНЯ» И «ВЧЕРА» — РАЗ НА ПЕРЕСБОРКУ (1.8.0).
     *
     * По календарным датам, а не по разнице в миллисекундах: звонок в 23:50
     * и звонок в 00:10 разделяют двадцать минут, но это «вчера» и «сегодня»,
     * и человек различает их именно так.
     *
     * ── Было ───────────────────────────────────────────────────────────────
     * Разница начал двух дней делилась на 24 часа — для каждой строки,
     * с двумя календарями на строку. В день перевода часов сутки длятся
     * 23 или 25 часов, и вчерашний звонок на следующий день после перевода
     * подписывался «Сегодня». Границы теперь — полночь сегодня, вчера
     * и завтра по календарю, считаются один раз; строка только сравнивает
     * два числа.
     *
     * Звонок «из будущего» (часы телефона отвели назад) — датой, как раньше.
     */
    class Days(now: Long = System.currentTimeMillis()) {
        private val startOfYesterday: Long
        private val startOfToday: Long
        private val startOfTomorrow: Long

        init {
            val day = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            startOfToday = day.timeInMillis
            day.add(Calendar.DAY_OF_MONTH, 1)
            startOfTomorrow = day.timeInMillis
            day.add(Calendar.DAY_OF_MONTH, -2)
            startOfYesterday = day.timeInMillis
        }

        fun of(millis: Long): Day = when {
            millis >= startOfTomorrow -> Day.EARLIER
            millis >= startOfToday -> Day.TODAY
            millis >= startOfYesterday -> Day.YESTERDAY
            else -> Day.EARLIER
        }

        /** Полночь, с которой начался нынешний день: сменилась — подписи пора пересобрать. */
        val today: Long get() = startOfToday

        /** Сколько осталось до следующей полуночи. */
        fun untilTomorrow(now: Long = System.currentTimeMillis()): Long =
            (startOfTomorrow - now).coerceAtLeast(0L)
    }

    /**
     * Пометка о блокировке для строки: сначала по ключу самой строки, потом
     * по номеру. По ключу строки кладётся «Без звука — тихие часы» (1.8.0):
     * она про один звонок, а не про номер. Ключ строки («number:…#id»)
     * с номером в E.164 не совпадает никогда. Разбор — BlockedCallLog.silencedRows.
     */
    private fun blockedLabelOf(group: CallLogGroup, blockedLabels: Map<String, String>): String? =
        blockedLabels[group.key] ?: blockedLabels[group.e164]

    /*
     * ИМЯ ОТ ОПЕРАТОРА (1.8.0, «маркировка звонков»): разбор в data/NetworkNames.kt.
     *
     * Только у номеров не из книги и без имени в журнале: книга и журнал
     * точнее сети. Заметка человека тоже важнее — она остаётся заголовком,
     * а имя сети уходит в подпись «Сбербанк — по данным оператора».
     */
    private fun networkNameOf(
        group: CallLogGroup,
        bookOrLogName: String?,
        knownNumbers: Set<String>,
        networkNames: Map<String, String>
    ): String? = if (bookOrLogName.isNullOrBlank() && group.e164 !in knownNumbers) {
        networkNames[group.e164]?.takeIf { it.isNotBlank() }
    } else null

    /*
     * ИМЯ СТРОКИ — ИЗ КНИГИ, ЕСЛИ НОМЕР В НЕЙ ЕСТЬ (1.7.0).
     *
     * Автор, 24.10: «после добавления в контакты номер изменился на имя
     * в журнале только после звонка». Имя в записи журнала (CACHED_NAME)
     * пишет система в момент звонка и потом не трогает: сохранили номер
     * в контакты или переименовали контакт — журнал до следующего звонка
     * показывал старое. Теперь имя берётся из книги, того же контакта,
     * что и фото (первого по алфавиту); номера в книге нет — из журнала,
     * как раньше (там бывает имя из справочника оператора). Затем — форма
     * из «Как показывать имя».
     */
    private fun nameOf(
        group: CallLogGroup,
        contactNames: Map<Pair<String, String>, String>,
        bookNames: Map<String, String>
    ): String? = ContactNames.inJournal(bookNames[group.e164] ?: group.displayName, group.e164, contactNames)

    /** Подготовка одной строки. Отпечаток уже посчитан — он кладётся в строку как есть. */
    private fun mapOne(
        group: CallLogGroup,
        inputs: Inputs,
        render: Render,
        signature: Int
    ): CallLogRowState = run {
        val formatted = PhoneFormatter.format(group.number)
        val labels = render.labels

        /*
         * Заголовок: имя, заметка или номер.
         *
         * Заметка вместо имени — главное здесь. Большинство входящих
         * от людей, которых не заносят в книгу: курьер, мастер, оператор.
         * Строка «доставка мебели» отвечает на вопрос «кто это» без
         * засорения телефонной книги, а безликий номер — нет.
         */
        val note = inputs.notes[group.e164]
        val bookOrLogName = nameOf(group, inputs.contactNames, inputs.bookNames)
        val networkName = networkNameOf(group, bookOrLogName, inputs.knownNumbers, inputs.networkNames)
        val hasNote = !note?.note.isNullOrBlank()
        val display = NoteDisplay.resolve(
            // «Фамилия Имя» (1.6.2) — только для имени того же контакта.
            // Имя от оператора — вместо имени, только если нет заметки
            displayName = bookOrLogName ?: networkName?.takeIf { !hasNote },
            note = note?.note,
            formattedNumber = formatted
        )

        val info = inputs.numberInfo[group.e164]
        /*
         * ══════════════════════════════════════════════════════════════════
         * «ЗНАКОМЫЙ» — ТОЛЬКО ЕСЛИ НОМЕР ЕСТЬ В КНИГЕ СЕЙЧАС
         * ══════════════════════════════════════════════════════════════════
         * Раньше знакомым считался и номер, у которого журнал вызовов
         * хранит ссылку на контакт (CACHED_LOOKUP_URI → contactId).
         * Но эту ссылку система ставит сама и не всегда к книге:
         *
         * • служебные номера вроде 8-800 система сопоставляет
         *   со справочником «Carrier service numbers» — это каталог
         *   Google, а не контакт человека;
         * • ссылка остаётся после удаления контакта — кэш не чистится;
         * • на части прошивок последний сегмент ссылки — ключ, а не номер
         *   (об этом уже есть разбор в DialerViewModel).
         *
         * Итог был виден на экране: номер без имени, а подпись оператора
         * не показана — будто он в контактах. Особенно у номеров на 8:
         * именно их система чаще сопоставляет с каталогом.
         *
         * Теперь решает только сама телефонная книга: номер знаком,
         * если он в ней есть. knownNumbers строится из неё при каждом
         * обновлении, и устаревших ссылок в нём не бывает.
         */
        val isKnown = group.e164 in inputs.knownNumbers

        /*
         * ГДЕ ПОКАЗЫВАТЬ ОПЕРАТОРА И РЕГИОН — РЕШАЕТ ЧЕЛОВЕК.
         *
         * Раньше подпись показывалась только у незнакомых, и это было
         * зашито в код. Теперь это настройка «Оператор и регион»:
         * кому-то важно видеть, на какой номер звонили с рабочего,
         * а кому-то строка под каждым контактом только мешает.
         *
         * По умолчанию — только у незнакомых: у контакта с именем
         * «Мама» оператор ничего не добавляет.
         */
        val carrierLabel = when (inputs.carrierInfo) {
            CarrierInfoMode.HIDDEN -> null
            CarrierInfoMode.ALL -> labels.infoSummary(info)
            CarrierInfoMode.UNKNOWN_ONLY -> if (isKnown) null else labels.infoSummary(info)
        }
        // Чьё имя в заголовке — видно по подписи (1.8.0): «по данным оператора»
        val infoLabel = when {
            networkName == null -> carrierLabel
            hasNote -> labels.networkNamed(networkName)
            else -> labels.networkSource(carrierLabel)
        }

        val dateLabel = render.dateLabel(group.lastDate)

        CallLogRowState(
            key = group.key,
            number = group.number,
            e164 = group.e164,
            contactId = group.contactId,
            isContact = group.contactId != null || isKnown,
            title = display.title,
            noteLine = display.subtitle?.takeIf { display.titleFromNote.not() },
            hasReminder = group.e164 in inputs.remindedNumbers,
            titleFromNote = display.titleFromNote,
            formattedNumber = formatted,
            dateLabel = dateLabel,
            infoLabel = infoLabel,
            // Пометка о блокировке: показывается вместо подписи
            // определителя — причина важнее сведений о регионе
            blockedLabel = blockedLabelOf(group, inputs.blockedLabels),
            // Оценки риска больше нет; предупреждающим остаётся
            // только то, что назвал сам человек
            infoIsWarning = false,
            /*
             * Фотография: сначала из журнала, потом из книги.
             *
             * CallLog кэширует фото контакта не всегда — поле заполняется
             * системой при звонке и часто остаётся пустым, особенно
             * для старых записей. Поэтому при наличии contactId берём
             * фотографию из телефонной книги, где она точно актуальна.
             * Раньше аватары просто не показывались.
             */
            /*
             * Фото из книги важнее кэша журнала.
             *
             * Кэш мог сохранить старую фотографию, которую человек
             * с тех пор поменял, а книга всегда актуальна. Кэш остаётся
             * запасным вариантом для номеров, которых в книге нет.
             */
            photoUri = inputs.contactPhotos[group.e164] ?: group.photoUri,
            lastType = group.lastType,
            count = group.count,
            incomingCount = group.incomingCount,
            outgoingCount = group.outgoingCount,
            missedCount = group.missedCount,
            /*
             * ТРЕВОЖНАЯ МЕТКА — ТОЛЬКО ЕСЛИ ПОСЛЕДНИЙ ВЫЗОВ ПРОПУЩЕН.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ НЕ missedCount > 0
             * ══════════════════════════════════════════════════════════════
             * Так было раньше, и метка загоралась при ЛЮБОМ пропущенном
             * за всю историю общения. У человека, с которым созваниваются
             * годами, пропущенные есть всегда — и карточка горела красным
             * постоянно.
             *
             * Смысл метки при этом терялся: она должна означать «тебе
             * звонили, ты не ответил, перезвони». А означала «когда-то
             * за три года был неотвеченный звонок» — сведение, которое
             * ни к чему не побуждает.
             *
             * ── Что считается тревожным ────────────────────────────────────
             * Последний вызов пропущен или отклонён. Если после него был
             * разговор — вопрос закрыт, каким бы ни было прошлое.
             */
            hasMissed = group.lastType == CallType.MISSED ||
                group.lastType == CallType.REJECTED,
            simLabel = group.subscriptionLabel,
            simSlotIndex = inputs.simSlots[group.subscriptionId],
            numbersCount = group.numbers.size,
            accessibilityLabel = labels.accessibility(
                display.title, group.lastType, dateLabel
            ),
            signature = signature,
            group = group
        )
    }

    /**
     * Подписи, которые нельзя получить без ресурсов и локали.
     *
     * Интерфейс передаёт их сюда готовыми функциями: так вычисления
     * остаются вне композиции, а тексты — локализованными.
     */
    interface Labels {
        fun infoSummary(info: NumberInfo?): String?
        fun accessibility(title: String, type: CallType, date: String): String

        /** «Сегодня, 18:02» — строка из ресурсов, время подставляет маппер. */
        fun today(time: String): String

        /** «Вчера, 21:47». */
        fun yesterday(time: String): String

        /**
         * Подпись строки, где заголовок — имя от оператора (1.8.0):
         * «По данным оператора · Билайн · Москва». Без ресурсов — как есть.
         */
        fun networkSource(carrier: String?): String? = carrier

        /** Имя от оператора, когда заголовок — заметка: «Сбербанк — по данным оператора». */
        fun networkNamed(name: String): String = name

        /**
         * Отпечаток подписей (1.8.0): те же тексты — то же число.
         *
         * Подписи пересоздаются при каждом заходе на экран журнала, а тексты
         * у них меняются только со сменой языка. По отпечатку пересборка
         * понимает, годятся ли строки прошлой сборки ([Context]). 0 — отпечатка
         * нет: тогда готовые строки не берутся вовсе.
         */
        val fingerprint: Int get() = 0
    }
}
