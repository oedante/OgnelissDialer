package com.ogneliss.dialer.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * ПЕРЕНОС «SIM ДЛЯ ДЕЙСТВИЙ СВАЙПА» В САМИ СВАЙПЫ (2.9.11).
 *
 * Было две настройки об одном: действие свайпа «Позвонить» и отдельно «SIM для
 * действий свайпа» (спрашивать / первая / вторая, расширенная, раздел «SIM-карты»).
 * С 2.9.8 карта может быть в самом действии — «Позвонить с SIM 1/2», — и «Позвонить»
 * + «Первая SIM» значило то же, что «Позвонить с SIM 1». Автор согласился
 * упростить (обзор интерфейса, 25.11.2026): отдельная настройка убрана.
 *
 * Чтобы у выбравших карту ничего не поменялось, их выбор переносится один раз:
 * везде, где у свайпа стоит «Позвонить» (журнал и контакты, раздел SMS, основное
 * действие и кнопки панели), — «Позвонить с SIM 1» или «с SIM 2». «Спрашивать»
 * (по умолчанию) переносить не во что: «Позвонить» при двух картах и так спрашивает.
 * Старый ключ удаляется в той же записи — перенос не повторится.
 *
 * Механизм — DataMigration у DataStore: выполняется до первого чтения настроек,
 * в одной записи с ними. Ни один экран не увидит половину переноса.
 */
internal object SwipeSimMigration : DataMigration<Preferences> {

    /** Ключ убранной настройки; значения были ASK, FIRST, SECOND (enum SwipeSimMode). */
    val OLD_KEY = stringPreferencesKey("swipe_sim_mode")

    /** Основные действия свайпов и их значения по умолчанию (как в Prefs). */
    // Pair(…), а не «to»: у Preferences.Key свой инфиксный to (пара для mutablePreferencesOf)
    private val PRIMARY = listOf(
        Pair(stringPreferencesKey("swipe_right"), SwipeAction.CALL.key),
        Pair(stringPreferencesKey("swipe_left"), SwipeAction.SMS.key),
        Pair(stringPreferencesKey("sms_swipe_right"), SwipeAction.SMS.key),
        Pair(stringPreferencesKey("sms_swipe_left"), SwipeAction.DELETE_FROM_LOG.key),
    )

    /** Дополнительные действия (кнопки панели) — ключи через запятую; по умолчанию пусто. */
    private val EXTRA = listOf(
        stringPreferencesKey("swipe_right_extra"),
        stringPreferencesKey("swipe_left_extra"),
        stringPreferencesKey("sms_swipe_right_extra"),
        stringPreferencesKey("sms_swipe_left_extra"),
    )

    /** Во что превращается «Позвонить» при сохранённом выборе; null — оставить как есть. */
    fun callActionFor(oldMode: String?): SwipeAction? = when (oldMode) {
        "FIRST" -> SwipeAction.CALL_SIM1
        "SECOND" -> SwipeAction.CALL_SIM2
        else -> null
    }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[OLD_KEY] != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val out = currentData.toMutablePreferences()
        val target = callActionFor(currentData[OLD_KEY])
        if (target != null) {
            for ((key, default) in PRIMARY) {
                if ((currentData[key] ?: default) == SwipeAction.CALL.key) out[key] = target.key
            }
            for (key in EXTRA) {
                val raw = currentData[key] ?: continue
                out[key] = replaceInList(raw, target)
            }
        }
        out.remove(OLD_KEY)
        return out.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    /**
     * «call» в списке кнопок — на действие с картой. Если оно там уже есть,
     * второй раз не добавляется (порядок прочих — как был).
     */
    internal fun replaceInList(raw: String, target: SwipeAction): String =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it == SwipeAction.CALL.key) target.key else it }
            .distinct()
            .joinToString(",")
}
