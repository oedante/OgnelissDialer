package com.ogneliss.dialer.data

import androidx.compose.runtime.Immutable

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.ogneliss.dialer.util.PhoneFormatter
import com.ogneliss.dialer.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class NumberCategory {
    PERSONAL,        // обычный абонентский номер
    TOLL_FREE,       // 8-800
    PREMIUM,         // 8-809 и прочие платные
    SHORT_CODE,      // короткие сервисные номера
    ORGANIZATION,    // городской / стационарный
    INTERNATIONAL,   // зарубежный номер
    HIDDEN,          // скрытый / без номера
    UNKNOWN
}

/** Локальный результат определения. Никаких сетевых запросов. */
@Immutable
data class NumberInfo(
    val e164: String,
    val formatted: String,
    val country: String?,        // "Россия"
    val region: String?,         // "Краснодарский край"
    val operatorHint: String?,   // "МТС" — по коду DEF, приблизительно
    val category: NumberCategory
)

/**
 * Офлайн-определитель номера.
 *
 * ВАЖНО: используются только libphonenumber (данные вшиты в APK)
 * и локальная таблица кодов DEF. Номер НИКОГДА не покидает устройство.
 * Разрешение INTERNET в манифесте отсутствует — это гарантируется на уровне ОС.
 */
@Singleton
class NumberInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val directoryLookup: DirectoryLookupRepository,
    /**
     * Реестр Россвязи.
     *
     * Даёт оператора и регион по официальной выгрузке — точнее, чем
     * офлайн-справочник libphonenumber (он знает регион только у части
     * городских номеров) и чем прежняя таблица кодов DEF (она знала
     * оператора по трёхзначному коду, а коды давно поделены между
     * операторами по тысячам).
     */
    private val numberingPlan: NumberingPlan
) {

    /**
     * ОПЕРАТОР ЗА ПРЕДЕЛАМИ РОССИИ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ОН НЕ ЗАМЕНЯЕТ РЕЕСТР РОССВЯЗИ
     * ══════════════════════════════════════════════════════════════════════
     * Вопрос возникает сам собой: раз у libphonenumber есть оператор
     * по номеру, зачем мы держим свой файл на четыре мегабайта?
     *
     * Потому что по России эти данные заметно хуже. Сверка с выгрузкой
     * (файл resources/carrier/ru/7.txt в репозитории libphonenumber):
     *
     * • 988 префиксов на всю страну против 419 551 диапазона у нас;
     * • представлен 41 код DEF из сотни с лишним. Кодов 910, 916, 918
     *   в данных нет вовсе — это МТС, Мегафон и Билайн в Москве
     *   и на юге, то есть десятки миллионов номеров;
     * • код 967 отдан целиком Билайну одним префиксом «796», хотя внутри
     *   него живут диапазоны разных операторов. Это ровно то угадывание
     *   по коду, от которого мы ушли, когда завели реестр;
     * • городских номеров и линий 8-800 нет совсем;
     * • региона эти данные не дают вообще — только оператора.
     *
     * Поэтому для российских номеров источник остаётся прежним,
     * а этот определитель отвечает там, где реестр молчит.
     *
     * ── Что он даёт в других странах ───────────────────────────────────────
     * Германия — 41 префикс, Франция — 398, США и Канада — 697.
     * Немного, но основные мобильные сети покрыты, а это и есть случай,
     * когда человек смотрит на незнакомый номер.
     *
     * ⚠️ Перенос номера между операторами эти данные не учитывают
     * нигде — как и наш реестр. Подпись говорит, кому диапазон выдан,
     * а не кто обслуживает номер сегодня.
     */
    private val carrierMapper by lazy {
        runCatching {
            com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper.getInstance()
        }.getOrNull()
    }

    /**
     * СТРАНА ДЛЯ РАЗБОРА МЕСТНЫХ НОМЕРОВ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ SIM, А НЕ ЯЗЫК СИСТЕМЫ
     * ══════════════════════════════════════════════════════════════════════
     * Номер вида «030 12345678» — местный, и без страны его не разобрать.
     * Взять страну можно из трёх мест, и они расходятся чаще, чем кажется:
     *
     * • SIM-карта — где выдан номер владельца. Самый верный источник:
     *   местные номера набирают в той же стране, где куплена карта;
     * • сеть — где телефон находится сейчас. В поездке это чужая страна,
     *   а звонят из неё чаще домой;
     * • язык системы — вообще не про географию: у русскоязычного
     *   в Германии он русский.
     *
     * Поэтому порядок такой: SIM, затем сеть, затем страна из настроек
     * языка, и «RU» последним — как было до появления других языков.
     *
     * Значение читается один раз: смена SIM-карты на ходу — событие
     * редкое, а TelephonyManager при каждом разборе номера в журнале
     * обходится дороже, чем сам разбор.
     */
    private val deviceRegion: String by lazy {
        val telephony = runCatching {
            context.getSystemService(android.telephony.TelephonyManager::class.java)
        }.getOrNull()

        val fromSim = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
        val fromNetwork = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
        val fromLocale = Locale.getDefault().country.takeIf { it.isNotBlank() }

        (fromSim ?: fromNetwork ?: fromLocale ?: "RU").uppercase(Locale.ROOT)
    }

    /**
     * Пакетный разбор списка номеров.
     *
     * Разбор через libphonenumber — вычислительная работа: парсинг,
     * поиск по офлайн-справочнику регионов, форматирование. На 500 записях
     * журнала это десятки миллисекунд, которые нельзя занимать у главного
     * потока и не стоит занимать у пула IO — тот нужен для чтения курсоров.
     *
     * Поэтому весь пакет уходит на Default, где пул равен числу ядер.
     */
    suspend fun describeAll(
        items: List<Pair<String, Boolean>>
    ): Map<String, NumberInfo> = withContext(defaultDispatcher) {
        items.associate { (number, known) ->
            number to describe(number, known)
        }
    }

    private val util by lazy { PhoneNumberUtil.getInstance() }
    private val geocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }

    /**
     * Имя из внешнего определителя, если пользователь его включил.
     *
     * ── Почему отдельным методом ───────────────────────────────────────────
     * describe() работает офлайн и вызывается для всего журнала пачкой.
     * Обращение к стороннему приложению — совсем другая операция:
     * оно может занимать сотни миллисекунд и передаёт номер наружу.
     * Смешивать их в одном методе нельзя: тогда включение определителя
     * молча замедлило бы загрузку журнала и отправило туда все номера
     * разом, а не только тот, который сейчас звонит.
     *
     * @return имя или null, если провайдер ничего не знает
     */
    suspend fun lookupExternalName(
        number: String,
        enabledProviderIds: Set<String>
    ): String? {
        if (enabledProviderIds.isEmpty()) return null

        return directoryLookup.availableProviders()
            .filter { it.id.toString() in enabledProviderIds }
            .firstNotNullOfOrNull { provider ->
                directoryLookup.lookup(number, provider.id)?.displayName
            }
    }

    fun describe(rawNumber: String?, isInContacts: Boolean): NumberInfo {
        if (rawNumber.isNullOrBlank()) {
            return NumberInfo("", "", null, null, null, NumberCategory.HIDDEN)
        }

        val e164 = PhoneFormatter.toE164(rawNumber)
        val digits = rawNumber.filter { it.isDigit() }

        /*
         * ══════════════════════════════════════════════════════════════════
         * СТРАНА РАЗБОРА И ЯЗЫК ПОДПИСЕЙ — НЕ «RU» И НЕ РУССКИЙ
         * ══════════════════════════════════════════════════════════════════
         * Раньше здесь стояло `parse(rawNumber, "RU")` и `Locale("ru")`.
         * Пока приложение было только на русском, это было верно. Теперь
         * языков четыре, и обе жёстко вписанные строки становятся ошибкой:
         *
         * • разбор с «RU» ломает местные номера где угодно, кроме России.
         *   Немецкий номер 030 12345678, набранный без кода страны,
         *   с российскими правилами разбирается в бессмыслицу или
         *   не разбирается вовсе;
         *
         * • подписи на русском получает немец и француз: «Бавария»
         *   вместо «Bayern».
         *
         * Теперь страна берётся у SIM-карты, а язык подписей — у системы.
         */
        val parsed = runCatching { util.parse(rawNumber, deviceRegion) }.getOrNull()

        val locale = Locale.getDefault()

        val country = parsed?.let {
            val code = util.getRegionCodeForNumber(it) ?: ""
            /*
             * Locale.Builder, а не конструктор: Locale(язык, страна)
             * объявлен устаревшим. Сборщик заодно проверяет коды
             * и бросает исключение на мусоре, поэтому обёрнут —
             * getRegionCodeForNumber на странном номере может вернуть
             * что угодно.
             */
            runCatching {
                Locale.Builder()
                    .setLanguage(locale.language)
                    .setRegion(code)
                    .build()
                    .displayCountry
            }.getOrNull()?.takeIf { c -> c.isNotBlank() }
        }

        val region = parsed?.let {
            geocoder.getDescriptionForNumber(it, locale).takeIf { r -> r.isNotBlank() }
        }

        val category = classify(digits, parsed)

        /*
         * РЕЕСТР ГЛАВНЕЕ ПРЕЖНИХ ИСТОЧНИКОВ.
         *
         * ── Регион ─────────────────────────────────────────────────────────
         * Офлайн-справочник libphonenumber знает регион у городских
         * номеров и почти никогда — у мобильных: там он отдаёт «Россия»
         * или пустоту. Выгрузка Россвязи знает диапазон каждого номера.
         *
         * ── Оператор ───────────────────────────────────────────────────────
         * Таблица DefCodes сопоставляла оператора трёхзначному коду
         * целиком. Так было верно лет пятнадцать назад: сегодня внутри
         * кода 967 живут диапазоны разных операторов, и ответ по коду —
         * угадывание. Реестр даёт диапазон точно.
         *
         * Обе прежние ветки остаются запасными: реестр не знает
         * зарубежных и коротких номеров, а также диапазонов, выданных
         * после выгрузки.
         */
        val planEntry = numberingPlan.lookup(e164)

        /*
         * Порядок источников оператора:
         *
         *   1. реестр Россвязи — точный диапазон, только Россия;
         *   2. carrier libphonenumber — все страны, грубее и с пробелами;
         *   3. таблица кодов DEF — наш давний запасной путь по России.
         *
         * Второй шаг спрашивается только у разобранного номера: без
         * разбора определителю нечего сопоставлять.
         */
        val fromCarrier = parsed?.let { number ->
            runCatching {
                carrierMapper?.getNameForNumber(number, locale)?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        /*
         * Таблица кодов DEF — российская, и применяется только
         * к российским номерам.
         *
         * Без этой проверки у немецкого номера +49 800 … из цифр
         * вычленялось бы «498», а у французского +33 800 — «338»,
         * и таблица отвечала бы что-нибудь про российского оператора.
         * Своей страны у номера она не спрашивает — просто смотрит
         * три цифры.
         */
        val isRussian = e164.startsWith("+7")

        val operator = planEntry?.let { NumberingPlan.shortOperatorName(it.operator) }
            ?: fromCarrier
            ?: defCode(digits).takeIf { isRussian }?.let { DefCodes.operatorFor(it) }

        return NumberInfo(
            e164 = e164,
            formatted = PhoneFormatter.format(rawNumber),
            country = country,
            region = planEntry?.region?.takeIf { it.isNotBlank() } ?: region,
            operatorHint = operator,
            category = category
        )
    }

    /**
     * ВИД НОМЕРА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ТЕПЕРЬ СПРАШИВАЕМ У libphonenumber, А НЕ СМОТРИМ НА ЦИФРЫ
     * ══════════════════════════════════════════════════════════════════════
     * Здесь были правила «начинается на 800 — бесплатный, на 809 —
     * платный». Это правила российского плана нумерации, и за его
     * пределами они не просто бесполезны, а вредны: немецкий номер
     * 0800-… действительно бесплатный, а вот французский 08 09 …
     * платным не является.
     *
     * libphonenumber знает типы номеров для всех стран, которые
     * поддерживает. Сначала спрашиваем у него, и только если он
     * не знает (номер не разобрался — короткий, служебный, набранный
     * с ошибкой), возвращаемся к российским правилам по цифрам.
     *
     * ⚠️ «Международный» теперь считается относительно страны SIM-карты,
     * а не России. Для владельца немецкой карты российский номер —
     * международный, и это верно: именно ему придёт счёт за роуминг.
     */
    private fun classify(digits: String, parsed: com.google.i18n.phonenumbers.Phonenumber.PhoneNumber?): NumberCategory {
        if (digits.length in 3..6) return NumberCategory.SHORT_CODE

        if (parsed != null) {
            val type = util.getNumberType(parsed)
            val foreign = util.getRegionCodeForNumber(parsed) != deviceRegion

            when (type) {
                PhoneNumberUtil.PhoneNumberType.TOLL_FREE -> return NumberCategory.TOLL_FREE
                PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE -> return NumberCategory.PREMIUM
                else -> Unit
            }

            if (foreign) return NumberCategory.INTERNATIONAL

            if (type == PhoneNumberUtil.PhoneNumberType.FIXED_LINE) {
                return NumberCategory.ORGANIZATION
            }
            if (type == PhoneNumberUtil.PhoneNumberType.MOBILE) {
                return NumberCategory.PERSONAL
            }
        }

        /*
         * Запасной путь — правила российского плана по цифрам.
         *
         * Сюда попадают только номера, которые libphonenumber
         * не разобрал: набранные с опечаткой, служебные, обрезанные.
         * Страны у них нет, поэтому применяем правила своей —
         * но только если своя страна и есть Россия. У немца эти
         * правила дали бы «бесплатная линия» любому номеру,
         * начинающемуся на 800 после произвольного префикса.
         */
        if (deviceRegion != "RU") return NumberCategory.UNKNOWN

        val nat = digits.removePrefix("7").removePrefix("8")
        return when {
            nat.startsWith("800") -> NumberCategory.TOLL_FREE
            nat.startsWith("809") || nat.startsWith("803") -> NumberCategory.PREMIUM
            nat.length == 10 -> NumberCategory.PERSONAL
            else -> NumberCategory.UNKNOWN
        }
    }

    private fun defCode(digits: String): String? {
        val nat = digits.removePrefix("7").removePrefix("8")
        return nat.take(3).takeIf { it.length == 3 }
    }
}
