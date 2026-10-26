package com.ogneliss.dialer.call

import com.ogneliss.dialer.util.EmergencyNumbers
import android.telecom.Call
import android.os.Build
import androidx.annotation.RequiresApi
import android.telecom.CallScreeningService
import com.ogneliss.dialer.background.BlockedCallNotice
import com.ogneliss.dialer.data.BlockedCallLog
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * СЕРВИС ФИЛЬТРАЦИИ ВХОДЯЩИХ ВЫЗОВОВ (Android 10+).
 *
 * Назначение: система передаёт сюда каждый входящий вызов ДО того, как
 * телефон зазвонит, и ждёт ответа — пропустить или отклонить.
 * Работает только когда приложение выбрано телефоном по умолчанию.
 *
 * Сам класс решений не принимает: вся логика в [SpamPolicy], чтобы её
 * можно было тестировать отдельно от Android.
 *
 * ── Ограничение по времени ─────────────────────────────────────────────────
 * Система даёт на ответ несколько секунд. Если не уложиться, вызов
 * пропускается по умолчанию. Поэтому здесь нет тяжёлых операций,
 * а ошибки трактуются в пользу пропуска вызова: лучше принять спам,
 * чем потерять нужный звонок.
 *
 * ── Соответствие правилам Google Play ──────────────────────────────────────
 * Блокировка выполняется исключительно по правилам, заданным самим
 * пользователем, и по его локальной истории. Никакие данные о вызовах
 * не передаются наружу.
 */
@AndroidEntryPoint
class OgnelissCallScreeningService : CallScreeningService() {

    @Inject lateinit var collector: SpamInputCollector

    /**
     * Журнал причин блокировки.
     *
     * Заблокированный вызов выглядит в системном журнале как обычный
     * пропущенный: почему телефон не зазвонил, узнать неоткуда.
     * Здесь причина запоминается, чтобы показать её в списке.
     */
    @Inject lateinit var blockedCallLog: BlockedCallLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object { const val TAG = "Screening" }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        Logger.d(TAG, "Проверка входящего ${Logger.mask(number)}")

        scope.launch {
            val response = try {
                /*
                 * Обратный звонок экстренной службы (1.3.2, 01.10.2026).
                 * Он приходит с незнакомого номера, и «блокировка всех
                 * незнакомых» могла бы его отклонить. Два часа после
                 * исходящего на экстренный номер пропускаем всё.
                 */
                if (EmergencyNumbers.recentlyCalled(applicationContext)) {
                    Logger.i(TAG, "Недавно был экстренный вызов — входящий пропущен без проверки")
                    return@launch respondSafely(callDetails, allowResponse())
                }

                val e164 = PhoneFormatter.toE164(number.orEmpty())
                val input = collector.collect(number, e164)
                val decision = SpamPolicy.evaluate(input)

                /*
                 * Беззвучный пропуск доступен только держателю роли
                 * определителя. Без роли он трактуется как обычный
                 * пропуск: лучше сомнительный звонок со звуком,
                 * чем блокировка по догадке.
                 */
                /*
                 * Причина запоминается ДО применения решения.
                 *
                 * Иначе при сбросе вызова процесс может быть остановлен
                 * системой раньше, чем запись дойдёт до хранилища:
                 * CallScreeningService живёт ровно столько, сколько нужно
                 * для ответа.
                 */
                if (decision.action != SpamPolicy.Action.ALLOW) {
                    blockedCallLog.record(
                        e164 = input.e164,
                        reason = decision.reason,
                        outcome = if (decision.action == SpamPolicy.Action.BLOCK) {
                            BlockedCallLog.Outcome.BLOCKED
                        } else {
                            BlockedCallLog.Outcome.SILENCED
                        }
                    )
                }

                /*
                 * УВЕДОМЛЕНИЕ ОБ ОШИБКЕ БЛОКИРОВКИ.
                 *
                 * Показывается только при полной блокировке: беззвучный
                 * пропуск и так виден в журнале обычной записью,
                 * а заблокированный вызов не оставляет следа нигде,
                 * кроме отдельного журнала заблокированных, куда
                 * человек не заходит.
                 *
                 * hasOwnRule решает, будет ли кнопка отмены: снять маску
                 * из шторки нельзя — вместе с ней разблокируется весь
                 * диапазон.
                 */
                /*
                 * Тихий вызов (1.8.0, тихие часы) — метка для службы вызовов.
                 * Заглушить звонок сами мы можем только с ролью определителя;
                 * без неё это сделает служба вызовов, и она же не станет
                 * будить экран. Разбор — QuietCallGate.
                 */
                if (decision.action == SpamPolicy.Action.SILENCE) {
                    QuietCallGate.mark(input.e164)
                }

                if (decision.action == SpamPolicy.Action.BLOCK) {
                    BlockedCallNotice.show(
                        context = applicationContext,
                        e164 = input.e164,
                        hasOwnRule = input.inBlockList
                    )
                }

                when (decision.action) {
                    SpamPolicy.Action.BLOCK -> blockResponse()
                    SpamPolicy.Action.SILENCE ->
                        /*
                         * Проверка версии стоит ЯВНО, хотя canSilenceCalls
                         * её уже делает.
                         *
                         * lint не идёт внутрь чужой функции и считал вызов
                         * setSilenceCall (API 29) незащищённым — сборка
                         * release падала на этой единственной ошибке.
                         * Аннотации @ChecksSdkIntAtLeast на canSilenceCalls
                         * не хватило: между проверкой и вызовом лежит ещё
                         * одна функция, silenceResponse().
                         *
                         * Строка ниже дублирует проверку, зато её понимают
                         * и человек, и lint. Стоимость — одно сравнение
                         * целых чисел на входящий вызов.
                         */
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            ScreeningRoleInfo.canSilenceCalls(this@OgnelissCallScreeningService)
                        ) {
                            silenceResponse()
                        } else {
                            allowResponse()
                        }
                    SpamPolicy.Action.ALLOW -> allowResponse()
                }
            } catch (t: Throwable) {
                // Любая ошибка → пропускаем вызов. Молчащий телефон хуже спама.
                Logger.e(TAG, "Ошибка при проверке вызова, пропускаем", t)
                allowResponse()
            }

            respondSafely(callDetails, response)
        }
    }

    private fun respondSafely(callDetails: Call.Details, response: CallResponse) {
        runCatching { respondToCall(callDetails, response) }
            .onFailure { Logger.e(TAG, "Не удалось ответить системе", it) }
    }

    private fun allowResponse(): CallResponse = CallResponse.Builder().build()

    /**
     * Отклонение вызова.
     * skipCallLog = false — запись в журнале ОСТАЁТСЯ намеренно: пользователь
     * должен видеть, что звонок был и был заблокирован, иначе возникает
     * ощущение, что приложение «съедает» звонки.
     */
    /**
     * Беззвучный пропуск.
     *
     * Вызов проходит и попадает в журнал, но телефон не звонит.
     * Уведомление показываем: иначе человек не узнает о звонке вовсе,
     * а смысл именно в том, чтобы он мог перезвонить.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun silenceResponse(): CallResponse = CallResponse.Builder()
        .setSilenceCall(true)
        .setSkipCallLog(false)
        .setSkipNotification(false)
        .build()

    private fun blockResponse(): CallResponse = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipCallLog(false)
        .setSkipNotification(true)
        .build()
}
