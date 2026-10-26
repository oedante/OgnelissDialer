package com.ogneliss.dialer.data

import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.data.db.NetworkNameDao
import com.ogneliss.dialer.data.db.NetworkNameEntity
import com.ogneliss.dialer.log.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ИМЕНА ОТ ОПЕРАТОРА (1.8.0, «маркировка звонков»).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, в списке функций для 1.8.0: «Название от оператора». С 1.09.2025
 * звонки организаций в России приходят с названием: оператор передаёт его
 * вместе с номером, и телефон показывает «Сбербанк» вместо цифр. Android
 * отдаёт это имя в Call.Details (callerDisplayName) — но только пока идёт
 * вызов. В системный журнал оно не пишется, и через час в списке снова
 * безликий номер.
 *
 * Здесь имя запоминается и потом показывается в журнале и карточке
 * с пометкой «по данным оператора»: человек видит, что это не его контакт
 * и не его заметка, а сведения сети.
 *
 * ── Чему верить ────────────────────────────────────────────────────────────
 * Имя от сети — не подтверждение. Подменить его мошенник может так же,
 * как номер, — поэтому оно никогда не вытесняет имя из книги и заметку
 * человека и никогда не делает номер «знакомым» для правил блокировки.
 */
@Singleton
class NetworkNames @Inject constructor(
    private val dao: NetworkNameDao
) {

    /** Запомнить имя, пришедшее с вызовом. Пустое и «неизвестный» не пишутся. */
    suspend fun remember(e164: String, raw: String?, now: Long = System.currentTimeMillis()) {
        if (e164.isBlank()) return
        val name = clean(raw) ?: return
        suspendRunCatching { dao.upsert(NetworkNameEntity(e164 = e164, name = name, seenAt = now)) }
            .onFailure { Logger.w(TAG, "Не удалось запомнить имя от оператора", it) }
    }

    suspend fun get(e164: String): String? =
        suspendRunCatching { dao.get(e164)?.name }.getOrNull()

    /** Имена по номерам — для журнала. Ошибка чтения — пустая карта: журнал покажет номера. */
    suspend fun snapshot(limit: Int = DEFAULT_LIMIT): Map<String, String> =
        suspendRunCatching { dao.recent(limit).associate { it.e164 to it.name } }
            .getOrElse {
                Logger.w(TAG, "Не удалось прочитать имена от оператора", it)
                emptyMap()
            }

    /** Старше года — забыть: организация могла смениться, номер — уйти другому. */
    suspend fun purgeOlderThan(days: Int = RETENTION_DAYS, now: Long = System.currentTimeMillis()) {
        suspendRunCatching { dao.deleteOlderThan(now - days * DAY_MILLIS) }
    }

    companion object {
        private const val TAG = "NetworkNames"
        private const val DEFAULT_LIMIT = 2000
        private const val RETENTION_DAYS = 365
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** Длиннее — обрезаем: в строке журнала больше не видно, а база не резиновая. */
        const val MAX_LENGTH = 64

        /*
         * Что сети присылают вместо имени: заглушки «нет данных».
         * Нижним регистром; сравнение — со всей строкой целиком.
         */
        private val PLACEHOLDERS = setOf(
            "unknown", "private", "private number", "restricted", "anonymous",
            "unavailable", "withheld", "no caller id", "payphone",
            "неизвестно", "неизвестный", "неизвестный номер", "скрытый номер", "номер скрыт"
        )

        /**
         * Имя, годное для показа, или null.
         *
         * Не имя: пусто; заглушка «unknown»; сам номер — сеть иногда кладёт
         * в поле имени цифры. Пробелы схлопываются, слишком длинное
         * обрезается до [MAX_LENGTH].
         */
        fun clean(raw: String?): String? {
            val name = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
            if (name.isEmpty()) return null
            if (name.lowercase() in PLACEHOLDERS) return null
            // Почти одни цифры — это номер, а не название
            val significant = name.count { !it.isWhitespace() && it !in "+-()." }
            val digits = name.count { it.isDigit() }
            if (significant == 0 || digits * 10 >= significant * 7) return null
            return if (name.length > MAX_LENGTH) name.take(MAX_LENGTH).trimEnd() else name
        }
    }
}
