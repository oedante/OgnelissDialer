package com.ogneliss.dialer.call

import com.ogneliss.dialer.data.NumberCategory
import com.ogneliss.dialer.log.Logger

/**
 * ПОЛИТИКА ФИЛЬТРАЦИИ ВХОДЯЩИХ ВЫЗОВОВ.
 *
 * Назначение: единственное место, где принимается решение
 * «пропустить вызов или заблокировать».
 *
 * ── Почему это чистая функция ──────────────────────────────────────────────
 * Класс НЕ обращается ни к базе, ни к настройкам, ни к Android API.
 * Все нужные данные передаются на вход в [SpamInput], который собирает
 * вызывающий код. Благодаря этому логику можно полностью покрыть
 * unit-тестами на JVM, без эмулятора и без моков.
 *
 * Это не абстракция ради абстракции: ошибка здесь означает либо пропущенный
 * важный звонок, либо прошедший спам — и то и другое пользователь замечает
 * сразу. Такой код обязан быть покрыт тестами.
 *
 * ── Порядок проверок (важен) ───────────────────────────────────────────────
 * 1. Белый список — абсолютный приоритет, отменяет все остальные правила.
 * 2. Скрытый номер.
 * 3. Системный список блокировки Android.
 * 4. Чёрный список приложения (включая маски).
 * 5. Метки пользователя «спам» / «мошенники».
 * 6. Номер есть в контактах — пропускаем.
 * 7. Блокировка всех не из контактов.
 * 8. Тихие часы (1.8.0): незнакомый — без звука, повторный за 15 минут — звонит.
 *
 * ── Принцип осторожности ───────────────────────────────────────────────────
 * Пропущенный нужный звонок вредит сильнее, чем принятый спамный.
 * Поэтому при равных сомнениях решение всегда в пользу ПРОПУСКА.
 */
object SpamPolicy {

    private const val TAG = "SpamPolicy"

    /** Обозначение скрытого номера в логе. Не интерфейсная строка. */
    private const val HIDDEN_LABEL = "<скрытый>"

    /** Метки, означающие «этот номер нежелателен». Ключи, не переводы. */
    val BLOCKING_TAGS = setOf("spam", "fraud")

    /** Все данные, необходимые для решения. Собираются вызывающим кодом. */
    data class SpamInput(
        /** Сырой номер. null или пусто — скрытый номер. */
        val number: String?,
        val e164: String,
        val isInContacts: Boolean,
        /** Явно разрешён в белом списке. */
        val inAllowList: Boolean = false,
        /** Явно запрещён в чёрном списке приложения (в т.ч. по маске). */
        val inBlockList: Boolean = false,
        /** Есть в системном списке блокировки Android. */
        val inSystemBlockList: Boolean = false,
        /** Метки, поставленные пользователем этому номеру. */
        val tags: List<String> = emptyList(),
        /** Оценка риска 0..100 из офлайн-анализа. */
        val category: NumberCategory = NumberCategory.UNKNOWN,
        val settings: Settings = Settings(),
        /** Минута суток по часам телефона — для тихих часов; −1 — не задана. */
        val minuteOfDay: Int = -1,
        /**
         * Этот номер уже звонил без звука в тихие часы за последние
         * 15 минут — значит, перезванивает: пропускаем со звуком.
         */
        val repeatCall: Boolean = false,
        /** Слот SIM вызова (2.9.8, SpamSimScope); null — неизвестно или карта одна. */
        val simSlot: Int? = null
    )

    /** Снимок настроек фильтрации. */
    data class Settings(
        val blockHidden: Boolean = false,
        val blockNotInContacts: Boolean = false,
        val blockTaggedSpam: Boolean = true,
        /** Тихие часы для незнакомых (1.8.0): разбор в QuietHours. */
        val quietHours: QuietHours = QuietHours.OFF,
        /** Для какой SIM правила выше (2.9.8): разбор в SpamSimScope. */
        val simScope: SpamSimScope = SpamSimScope.ALL,
        /**
         * Свои правила SIM 2 (2.9.11, SpamSimScope.SEPARATE); null — не заданы
         * (для вызова на SIM 2 — правила выше). Списки и область у них не свои.
         */
        val sim2: Settings? = null
    ) {
        /** Без правил — только списки: вызов на карту, к которой правила не относятся. */
        fun listsOnly(): Settings = copy(
            blockHidden = false,
            blockNotInContacts = false,
            blockTaggedSpam = false,
            quietHours = QuietHours.OFF
        )
    }

    /**
     * ЧТО СДЕЛАТЬ С ВЫЗОВОМ.
     *
     * ── Почему три варианта, а не два ──────────────────────────────────────
     * Блокировка — решение необратимое: если приложение ошиблось, человек
     * не узнает, что ему звонили. Для явного спама это приемлемо,
     * для догадки — нет.
     *
     * Беззвучный пропуск ошибку прощает: телефон не звонит, но вызов
     * проходит и остаётся в журнале. Пользователь увидит его и решит сам.
     *
     * Заглушать может только держатель роли определителя. Если роли нет,
     * SILENCE трактуется как ALLOW: лучше пропустить сомнительный звонок
     * со звуком, чем заблокировать по догадке.
     */
    enum class Action {
        /** Пропустить обычным порядком. */
        ALLOW,

        /** Пропустить беззвучно: вызов в журнале, телефон молчит. */
        SILENCE,

        /** Отклонить: вызов не дойдёт. */
        BLOCK
    }

    /** Результат: что делать и почему. Причина уходит в лог. */
    data class Decision(
        val action: Action,
        val reason: Reason
    ) {
        /** Совместимость с прежним интерфейсом. */
        val block: Boolean get() = action == Action.BLOCK
    }

    /**
     * Причины решения перечислением, а не строкой: так их можно
     * проверять в тестах и при необходимости показать пользователю
     * на локализованном языке.
     */
    enum class Reason {
        ALLOW_LIST,
        HIDDEN_BLOCKED,
        HIDDEN_ALLOWED,
        SYSTEM_BLOCK_LIST,
        APP_BLOCK_LIST,
        USER_TAG,
        IN_CONTACTS,
        NOT_IN_CONTACTS,

        /** Тихие часы: незнакомый номер — без звука (1.8.0). */
        QUIET_HOURS,

        /** Тихие часы, но номер перезвонил за 15 минут — звонит. */
        QUIET_REPEAT,
        NO_RULE_MATCHED
    }

    fun evaluate(input: SpamInput): Decision {
        val masked = Logger.mask(input.e164)
        /*
         * 2.9.8: правила — для выбранной SIM (SpamSimScope). Вызов на другую
         * карту проверяется только по спискам: белый, системный, чёрный.
         */
        val s = rulesFor(input)

        // 1. Белый список побеждает всё. Без этого правило «блокировать всех
        //    не из контактов» отрезало бы школу, врача и курьера.
        if (input.inAllowList) return allow(masked, Reason.ALLOW_LIST)

        // 2. Скрытый номер. В тихие часы — без звука: повтор у скрытого
        //    не распознать, номера нет
        if (input.number.isNullOrBlank()) {
            return when {
                s.blockHidden -> deny(HIDDEN_LABEL, Reason.HIDDEN_BLOCKED)
                isQuietNow(input, s) -> silence(HIDDEN_LABEL, Reason.QUIET_HOURS)
                else -> allow(HIDDEN_LABEL, Reason.HIDDEN_ALLOWED)
            }
        }

        // 3. Системный список Android
        if (input.inSystemBlockList) return deny(masked, Reason.SYSTEM_BLOCK_LIST)

        // 4. Чёрный список приложения
        if (input.inBlockList) return deny(masked, Reason.APP_BLOCK_LIST)

        // 5. Метки пользователя
        if (s.blockTaggedSpam && input.tags.any { it in BLOCKING_TAGS }) {
            return deny(masked, Reason.USER_TAG)
        }

        // 6. Знакомый номер пропускаем всегда
        if (input.isInContacts) return allow(masked, Reason.IN_CONTACTS)

        // 7. Блокировка всех незнакомых
        if (s.blockNotInContacts) return deny(masked, Reason.NOT_IN_CONTACTS)

        // 8. Тихие часы (1.8.0): не блокировка, а беззвучный пропуск —
        //    вызов останется в журнале. Перезвонил за 15 минут — срочно
        if (isQuietNow(input, s)) {
            return if (input.repeatCall) allow(masked, Reason.QUIET_REPEAT)
            else silence(masked, Reason.QUIET_HOURS)
        }

        /*
         * ОЦЕНКИ РИСКА БОЛЬШЕ НЕТ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ УБРАНА
         * ══════════════════════════════════════════════════════════════════
         * Оценка складывалась из признаков, которые не отличают спам
         * от обычного звонка: «звонил несколько раз, вы не ответили» —
         * это и коллектор, и жена с чужого телефона; «разговоры длились
         * считаные секунды» — и робот, и курьер «я у подъезда».
         *
         * Проверить такую оценку нечем: разметки нет, и сколько нужных
         * звонков она срезала, узнать невозможно. А цена ошибок
         * несимметрична — пропущенный спам это раздражение,
         * заблокированный звонок из школы это беда.
         *
         * Вместо догадки приложение теперь показывает факт: оператора
         * и регион из официальной выгрузки Россвязи. Решение принимает
         * человек, и по этим сведениям он решает точнее любой эвристики.
         *
         * Осталось то, что человек задал сам: чёрный и белый списки,
         * метки, скрытые номера и блокировка незнакомых.
         */

        return allow(masked, Reason.NO_RULE_MATCHED)
    }

    /**
     * Правила для карты вызова. 2.9.8: правила — для выбранной SIM (SpamSimScope);
     * вызов на другую карту проверяется только по спискам. 2.9.11: «Свои для каждой
     * SIM» — вызов на SIM 2 по её правилам (Settings.sim2), остальные — по общим.
     */
    internal fun rulesFor(input: SpamInput): Settings {
        val settings = input.settings
        if (settings.simScope == SpamSimScope.SEPARATE && input.simSlot == 1) {
            settings.sim2?.let { own ->
                Logger.i(TAG, "Вызов на SIM 2: свои правила этой карты")
                return own
            }
        }
        if (settings.simScope.covers(input.simSlot)) return settings
        Logger.i(TAG, "Вызов на SIM ${input.simSlot?.plus(1)}: правила — для другой карты, только списки")
        return settings.listsOnly()
    }

    private fun isQuietNow(input: SpamInput, s: Settings): Boolean =
        input.minuteOfDay >= 0 && s.quietHours.contains(input.minuteOfDay)

    private fun allow(masked: String, reason: Reason): Decision {
        Logger.i(TAG, "ПРОПУЩЕН $masked: $reason")
        return Decision(Action.ALLOW, reason)
    }

    private fun silence(masked: String, reason: Reason): Decision {
        Logger.i(TAG, "БЕЗЗВУЧНО $masked: $reason")
        return Decision(Action.SILENCE, reason)
    }

    private fun deny(masked: String, reason: Reason): Decision {
        Logger.i(TAG, "ЗАБЛОКИРОВАН $masked: $reason")
        return Decision(Action.BLOCK, reason)
    }
}
