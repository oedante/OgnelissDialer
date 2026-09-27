package com.ogneliss.dialer.data

import com.ogneliss.dialer.data.db.SimPreferenceDao
import com.ogneliss.dialer.data.db.SimPreferenceEntity
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ПРЕДПОЧТЕНИЯ SIM ПО АБОНЕНТАМ.
 *
 * Назначение: помнить, с какой SIM пользователь обычно звонит на конкретный
 * номер, и подставлять её при следующем вызове.
 *
 * ── Логика выбора SIM при звонке (по убыванию приоритета) ──────────────────
 * 1. Пользователь явно нажал кнопку конкретной SIM — используем её
 *    и запоминаем для этого номера.
 * 2. Есть запомненное предпочтение для номера — используем его.
 * 3. Задана глобальная SIM по умолчанию в настройках — используем её.
 * 4. Ничего не задано — отдаём выбор системе (она покажет свой диалог
 *    или использует системную SIM по умолчанию).
 *
 * Мы намеренно НЕ угадываем SIM по истории вызовов автоматически:
 * молчаливая смена карты приводит к звонкам не с того номера,
 * а это уже вопрос денег и того, какой номер увидит собеседник.
 * Запоминаем только явный выбор пользователя.
 */
@Singleton
class SimPreferenceRepository @Inject constructor(
    private val dao: SimPreferenceDao
) {

    private companion object { const val TAG = "SimPref" }

    val all: Flow<Map<String, Int>> = dao.observeAll().map { list ->
        list.associate { it.e164 to it.slotIndex }
    }

    suspend fun slotFor(e164: String): Int? =
        runCatching { dao.get(e164)?.slotIndex }.getOrElse {
            Logger.w(TAG, "Не удалось прочитать предпочтение SIM", it)
            null
        }

    /** Запоминает явный выбор пользователя. */
    suspend fun remember(e164: String, slotIndex: Int) {
        runCatching { dao.upsert(SimPreferenceEntity(e164, slotIndex)) }
            .onSuccess {
                Logger.d(TAG, "SIM ${slotIndex + 1} запомнена для ${Logger.mask(e164)}")
            }
            .onFailure { Logger.e(TAG, "Не удалось сохранить предпочтение SIM", it) }
    }
}
