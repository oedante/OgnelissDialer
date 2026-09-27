package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.CarrierInfoMode
import com.ogneliss.dialer.ui.components.CallLogRowState
import com.ogneliss.dialer.util.PhoneFormatter
import java.text.SimpleDateFormat
import java.util.Date

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
 * создаётся один и переиспользуется для всего списка.
 */
object CallLogRowMapper {

    /**
     * @param labels готовые подписи, зависящие от локали и ресурсов
     */
    /**
     * @param contactPhotos фотографии из телефонной книги по contactId
     */
    /**
     * ИНКРЕМЕНТАЛЬНАЯ ПОДГОТОВКА.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Подгрузка страницы добавляет к списку сто пятьдесят записей,
     * а пересобирался он целиком: три тысячи строк заново разбирались
     * через libphonenumber ради ста пятидесяти новых.
     *
     * Здесь готовые строки переиспользуются: карточка пересобирается
     * только если изменились данные, от которых она зависит.
     *
     * ── Как определяется «изменилось» ──────────────────────────────────────
     * По ключу карточки и времени последнего вызова. Ключ уникален
     * и включает идентификатор вызова, поэтому при изменении группы
     * он меняется — и старая строка не подойдёт.
     *
     * Дополнительно сверяется заметка и подпись определителя: они
     * приходят позже основного списка, и строка должна обновиться,
     * когда они появятся.
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
         * Подписи о блокировке по номеру.
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
        remindedNumbers: Set<String> = emptySet()
    ): List<CallLogRowState> {
        if (previous.isEmpty()) {
            return map(
                groups, numberInfo, knownNumbers, simSlots,
                contactPhotos, blockedLabels, notes, dateFormat, labels, carrierInfo,
                remindedNumbers
            )
        }

        val byKey = previous.associateBy { it.key }

        return groups.map { group ->
            val cached = byKey[group.key]

            /*
             * Сравнение отпечатка, а не пересчёт полей.
             *
             * Проверять «изменилось ли что-нибудь» через те же функции,
             * что готовят строку, значило бы выполнить работу дважды —
             * и оптимизация превратилась бы в замедление. Целое число
             * сравнивается за наносекунды.
             */
            val signature =
                signatureOf(
                    group, numberInfo, notes, contactPhotos, simSlots,
                    blockedLabels, carrierInfo, remindedNumbers
                )

            if (cached != null && cached.signature == signature) cached else mapOne(
                group, numberInfo, knownNumbers, simSlots,
                contactPhotos, blockedLabels, notes, dateFormat, labels, carrierInfo,
                remindedNumbers
            )
        }
    }

    fun map(
        groups: List<CallLogGroup>,
        numberInfo: Map<String, NumberInfo>,
        knownNumbers: Set<String>,
        simSlots: Map<String, Int>,
        /** Фотографии из книги по номеру в E.164. */
        contactPhotos: Map<String, String>,
        /**
         * Подписи о блокировке по номеру.
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
        remindedNumbers: Set<String> = emptySet()
    ): List<CallLogRowState> = groups.map { group ->
        mapOne(
            group, numberInfo, knownNumbers, simSlots, contactPhotos,
            blockedLabels, notes, dateFormat, labels, carrierInfo, remindedNumbers
        )
    }

    /**
     * Отпечаток данных, влияющих на вид строки.
     *
     * Заметка сравнивается по времени изменения, а не по тексту:
     * число дешевле строки, а меняются они вместе.
     */
    private fun signatureOf(
        group: CallLogGroup,
        numberInfo: Map<String, NumberInfo>,
        notes: Map<String, NumberNote>,
        /** Фотографии из книги по номеру в E.164. */
        contactPhotos: Map<String, String>,
        simSlots: Map<String, Int>,
        /**
         * Подпись о блокировке.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ЕЁ ЗДЕСЬ НЕ ХВАТАЛО
         * ══════════════════════════════════════════════════════════════════
         * Блокировка номера не меняет ни число вызовов, ни заметку,
         * ни фотографию, ни SIM — то есть ничего из прежнего отпечатка.
         * Отпечаток совпадал, строка бралась из прошлого списка вместе
         * со старым (пустым) полем blockedLabel, и пометка в журнале
         * не появлялась.
         *
         * Заметно это было так: блокируешь из карточки — в журнале
         * ничего. После перезапуска приложения пометка на месте:
         * при пустом previous список собирается заново, и путь
         * с переиспользованием не работает.
         *
         * Разблокировка ломалась так же, только наоборот: пометка
         * оставалась висеть.
         */
        blockedLabels: Map<String, String>,
        carrierInfo: CarrierInfoMode,
        remindedNumbers: Set<String>
    ): Int {
        val note = notes[group.e164]
        val info = numberInfo[group.e164]
        var result = group.key.hashCode()
        result = 31 * result + group.count
        result = 31 * result + (note?.updatedAt?.hashCode() ?: 0)
        result = 31 * result + (info?.category?.ordinal ?: -1)
        result = 31 * result + (contactPhotos[group.e164]?.hashCode() ?: 0)
        result = 31 * result + (simSlots[group.subscriptionId] ?: -1)
        result = 31 * result + (blockedLabels[group.e164]?.hashCode() ?: 0)
        /*
         * Режим показа оператора — часть отпечатка.
         *
         * Иначе переключение настройки не меняло бы уже построенные
         * строки: отпечаток совпадал, строка бралась из прошлого списка
         * вместе со старой подписью. Ровно та же ошибка, из-за которой
         * не появлялась пометка о блокировке.
         */
        result = 31 * result + carrierInfo.ordinal
        /*
         * Напоминание — часть отпечатка по той же причине, что
         * и блокировка: поставили напоминание — в строке появился
         * значок, а сами данные вызова не изменились. Без этого
         * строка бралась бы из прошлого списка без значка.
         */
        result = 31 * result + (if (group.e164 in remindedNumbers) 1 else 0)
        return result
    }

    /** Подготовка одной строки. Вынесено, чтобы переиспользовать в обоих путях. */
    private fun mapOne(
        group: CallLogGroup,
        numberInfo: Map<String, NumberInfo>,
        knownNumbers: Set<String>,
        simSlots: Map<String, Int>,
        /** Фотографии из книги по номеру в E.164. */
        contactPhotos: Map<String, String>,
        /**
         * Подписи о блокировке по номеру.
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
        remindedNumbers: Set<String> = emptySet()
    ): CallLogRowState = run {
        val formatted = PhoneFormatter.format(group.number)

        /*
         * Заголовок: имя, заметка или номер.
         *
         * Заметка вместо имени — главное здесь. Большинство входящих
         * от людей, которых не заносят в книгу: курьер, мастер, оператор.
         * Строка «доставка мебели» отвечает на вопрос «кто это» без
         * засорения телефонной книги, а безликий номер — нет.
         */
        val note = notes[group.e164]
        val display = NoteDisplay.resolve(
            displayName = group.displayName,
            note = note?.note,
            formattedNumber = formatted
        )

        val info = numberInfo[group.e164]
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
        val isKnown = group.e164 in knownNumbers

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
        val infoLabel = when (carrierInfo) {
            CarrierInfoMode.HIDDEN -> null
            CarrierInfoMode.ALL -> labels.infoSummary(info)
            CarrierInfoMode.UNKNOWN_ONLY -> if (isKnown) null else labels.infoSummary(info)
        }

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
        val dateLabel = when (daysAgo(group.lastDate)) {
            0 -> labels.today(timeFormat.format(Date(group.lastDate)))
            1 -> labels.yesterday(timeFormat.format(Date(group.lastDate)))
            else -> dateFormat.format(Date(group.lastDate))
        }

        CallLogRowState(
            key = group.key,
            number = group.number,
            e164 = group.e164,
            contactId = group.contactId,
            title = display.title,
            noteLine = display.subtitle?.takeIf { display.titleFromNote.not() },
            hasReminder = group.e164 in remindedNumbers,
            titleFromNote = display.titleFromNote,
            formattedNumber = formatted,
            dateLabel = dateLabel,
            infoLabel = infoLabel,
            // Пометка о блокировке: показывается вместо подписи
            // определителя — причина важнее сведений о регионе
            blockedLabel = blockedLabels[group.e164],
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
            photoUri = contactPhotos[group.e164] ?: group.photoUri,
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
            simSlotIndex = simSlots[group.subscriptionId],
            numbersCount = group.numbers.size,
            accessibilityLabel = labels.accessibility(
                display.title, group.lastType, dateLabel
            ),
            signature =
                signatureOf(
                    group, numberInfo, notes, contactPhotos, simSlots,
                    blockedLabels, carrierInfo, remindedNumbers
                ),
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
    }

    /**
     * Сколько КАЛЕНДАРНЫХ дней назад.
     *
     * По датам, а не по разнице в миллисекундах: звонок в 23:50
     * и звонок в 00:10 разделяют двадцать минут, но это «вчера»
     * и «сегодня», и человек различает их именно так.
     */
    private fun daysAgo(date: Long): Int {
        fun startOfDay(millis: Long) = java.util.Calendar.getInstance().apply {
            timeInMillis = millis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val diff = startOfDay(System.currentTimeMillis()) - startOfDay(date)
        return (diff / (24L * 60 * 60 * 1000)).toInt()
    }

    /** Время без даты: подставляется в «Сегодня, …». */
    private val timeFormat = SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

}
