package com.ogneliss.dialer.call

import com.ogneliss.dialer.log.CallDiagnostics
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * ОБЯЗАТЕЛЬНЫЙ КОМПОНЕНТ ДИАЛЕРА ПО УМОЛЧАНИЮ.
 *
 * Система передаёт сюда все вызовы. Наша задача — показать экран вызова
 * и сообщить остальному приложению, что происходит.
 *
 * ── Что делает система, а не мы ────────────────────────────────────────────
 * Мелодию входящего вызова проигрывает telecom. Флаг
 * IN_CALL_SERVICE_RINGING в манифесте намеренно НЕ объявлен, поэтому
 * система звонит сама и учитывает персональную мелодию контакта,
 * «Не беспокоить» с исключениями, вибро и беззвучный режимы,
 * рабочий профиль и настройки доступности.
 *
 * Взять рингтон на себя означало бы воспроизвести всё это заново
 * и по-разному на каждой прошивке — с гарантированными расхождениями.
 */
@AndroidEntryPoint
class OgnelissInCallService : InCallService() {

    /** Метка для лога при неудачном показе уведомления. Не интерфейсная строка. */
    private companion object {
        const val TAG = "InCallService"
        const val WHAT_INCOMING = "показ уведомления о входящем"
    }

    /**
     * Передаёт факт завершения вызова в интерфейс.
     * Сервис не знает об экранах, а Activity может быть не запущена —
     * посредник разрывает эту связь.
     */
    @Inject lateinit var postCall: PostCallCoordinator

    /** Заметки: попадают в уведомление после отклонённого вызова. */
    @Inject lateinit var notesRepo: com.ogneliss.dialer.data.NotesRepository

    /*
     * @Suppress("DEPRECATION") — про call.state.
     *
     * Замена, Call.getDetails().getState(), появилась только в API 31,
     * а minSdk у нас 26: ветка со старым свойством нужна в любом случае.
     * Подавление стоит на функции, а не на файле, чтобы новые
     * устаревшие вызовы в других местах по-прежнему были видны.
     */
    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.service = this
        CallManager.add(call)

        // Исходящий звонок — человек у телефона: пропущенные увидены,
        // счётчик на значке сбрасываем (MissedCallsNotifier)
        @Suppress("DEPRECATION")
        val outgoing = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            call.details.callDirection == Call.Details.DIRECTION_OUTGOING
        } else {
            call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING
        }
        if (outgoing) MissedCallsNotifier.clear(this)

        /*
         * ОТСЛЕЖИВАНИЕ СОСТОЯНИЯ ВЫЗОВА.
         *
         * ── Зачем ──────────────────────────────────────────────────────────
         * Входящий мы больше не открываем сами — это делает уведомление.
         * Но если человек ответил ПРЯМО ИЗ уведомления, не разворачивая
         * его, экран вызова не появится вовсе: разговор идёт, а управлять
         * им нечем — ни отключить микрофон, ни включить громкую связь.
         *
         * Здесь мы ловим переход из «звонит» в «разговор» и открываем
         * экран. Для человека это выглядит естественно: ответил —
         * появился экран разговора.
         *
         * ── Почему обработчик снимается ────────────────────────────────────
         * Call живёт дольше нашей подписки только формально: система
         * держит объект до конца вызова. Не сняв обработчик, мы оставили
         * бы ссылку на службу после её остановки.
         */
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_ACTIVE) {
                    /*
                     * Уведомление входящего снимается при ответе.
                     *
                     * Раньше оно убиралось только в конце вызова —
                     * то есть висело весь разговор с кнопками
                     * «Ответить» и «Отклонить». Нажатие на «Ответить»
                     * во время разговора ничего бы не сделало,
                     * а «Отклонить» сбросило бы идущий звонок:
                     * подпись обещает не то, что произойдёт.
                     */
                    /*
                     * Уведомление сервиса переключается на обычное.
                     *
                     * Снимать его нельзя: оно держит передний сервис,
                     * а тот — процесс на время разговора. Поэтому
                     * не отменяем, а заменяем — тем же идентификатором.
                     */
                    // Имя берём из самого вызова: переменная выше
                    // объявляется позже этого обработчика
                    val label = CallDetailsCompat.displayLabel(
                        c.details,
                        getString(com.ogneliss.dialer.R.string.call_unknown_number)
                    )
                    CallForegroundService.start(
                        this@OgnelissInCallService, label, incoming = false
                    )

                    runCatching {
                        startActivity(
                            Intent(this@OgnelissInCallService, InCallActivity::class.java)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                )
                        )
                    }.onFailure {
                        Logger.w(TAG, "Не удалось открыть экран разговора", it)
                    }
                    c.unregisterCallback(this)
                }
            }
        })

        // contactDisplayName появился только в API 30, прямое обращение
        // роняет приложение на старых устройствах при входящем вызове
        val name = CallDetailsCompat.displayLabel(
            call.details,
            getString(com.ogneliss.dialer.R.string.call_unknown_number)
        )
        // Foreground-сервис держит процесс живым на всё время вызова
        /*
         * Сервис запускается обычным уведомлением.
         *
         * Вид уточняется ниже, когда станет известно, открылся ли экран
         * вызова: если да — кнопки в уведомлении лишние.
         */
        CallForegroundService.start(this, name, incoming = false)


        /*
         * Отдельного уведомления входящего больше нет.
         *
         * Его показывал этот блок, и оно висело ВТОРЫМ рядом
         * с уведомлением переднего сервиса: человек видел
         * «Входящий вызов» и «Текущий вызов» об одном звонке.
         *
         * Хуже того, система урезала его до строчки без кнопок —
         * CallStyle разворачивается только у уведомления переднего
         * сервиса с типом PHONE_CALL.
         *
         * Теперь сервис сам показывает нужное уведомление: с кнопками
         * при входящем, обычное при разговоре. Одно на весь вызов.
         */
        CallNotifications.ensureChannels(this)
        /*
         * ВХОДЯЩИЙ ОТКРЫВАЕТ ЭКРАН ЧЕРЕЗ УВЕДОМЛЕНИЕ, А НЕ НАПРЯМУЮ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО БЫЛО НЕ ТАК
         * ══════════════════════════════════════════════════════════════════
         * startActivity вызывался при любом состоянии, включая входящий.
         * А уведомление входящего уже содержит full-screen intent,
         * который открывает тот же экран.
         *
         * В итоге показывались оба: система рисовала уведомление сверху
         * с кнопками «Ответить» и «Отклонить», а под ним открывался
         * экран вызова со своими такими же кнопками. Две пары кнопок
         * на одном экране — человеку неясно, какая настоящая.
         *
         * ── Почему решение принимает система ───────────────────────────────
         * Она знает то, чего не знаем мы: заблокирован ли экран, идёт ли
         * полноэкранная игра, включён ли режим «не беспокоить».
         *
         * При заблокированном экране full-screen intent открывает экран
         * вызова во весь экран — иначе ответить было бы нечем.
         * При разблокированном показывает уведомление сверху, не отнимая
         * экран у того, чем человек занят.
         *
         * Это правильное поведение: звонок не должен выбрасывать
         * из карты посреди поворота или из видеозвонка.
         *
         * ── Исходящие и активные открываем сами ────────────────────────────
         * Там уведомления с full-screen intent нет, и без startActivity
         * экран не появится вовсе.
         */
        /*
         * ЭКРАН ОТКРЫВАЕТСЯ НАПРЯМУЮ — ВКЛЮЧАЯ ВХОДЯЩИЙ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ Я ВЕРНУЛ ПРЯМОЙ ЗАПУСК
         * ══════════════════════════════════════════════════════════════════
         * Раньше здесь стояло условие «кроме входящего»: считалось,
         * что экран откроет полноэкранное намерение уведомления.
         *
         * Оно и открывало — пока уведомление было отдельным. После
         * объединения с уведомлением переднего сервиса система перестала
         * разворачивать намерение: у служебных уведомлений оно
         * срабатывает не всегда, и это не наша прихоть, а поведение
         * платформы.
         *
         * Итог: на заблокированном экране не открывалось ничего.
         * Разговор шёл, ответить было нечем.
         *
         * ── Почему прямой запуск здесь допустим ────────────────────────────
         * Приложение — звонилка по умолчанию, и система разрешает ему
         * открывать экран вызова из фона. Именно так делают системные
         * звонилки.
         *
         * Активность помечена setShowWhenLocked и setTurnScreenOn:
         * она разбудит экран и покажется поверх блокировки.
         *
         * ── Уведомление при этом остаётся ──────────────────────────────────
         * Оно нужно, когда человек ушёл с экрана вызова — свернул
         * или переключился. И оно же держит передний сервис.
         */
        val screenOpened = runCatching {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            true
        }.getOrElse {
            Logger.w(TAG, "Не удалось открыть экран вызова", it)
            false
        }

        /*
         * УВЕДОМЛЕНИЕ С КНОПКАМИ — ТОЛЬКО ЕСЛИ ЭКРАН НЕ ОТКРЫЛСЯ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ОНО ВЫСКАКИВАЛО ПОВЕРХ
         * ══════════════════════════════════════════════════════════════════
         * Уведомление входящего показывалось всегда — и система выводила
         * его шторкой поверх экрана вызова, который к тому моменту уже
         * открылся сам.
         *
         * Получалось две пары кнопок «Ответить» и «Отклонить»: одна
         * в шторке, другая на экране под ней. Человеку неясно, какая
         * настоящая, и половина экрана закрыта.
         *
         * ── Когда уведомление всё же нужно ─────────────────────────────────
         * Если запуск экрана не удался — прошивка запретила открывать
         * активность из фона, такое бывает. Тогда уведомление
         * с кнопками остаётся единственным способом ответить,
         * и показать его обязательно.
         *
         * ── Почему сервису всё равно нужно уведомление ─────────────────────
         * Передний сервис без уведомления существовать не может: система
         * его остановит. Поэтому показываем обычное — с именем
         * и длительностью, без кнопок и без всплытия.
         */
        CallForegroundService.start(
            this,
            name,
            // call.state объявлено устаревшим; разбор — в аннотации
            // над onCallAdded
            incoming = call.state == Call.STATE_RINGING && !screenOpened
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        // Кто и почему завершил, куда шёл звук — разбор в CallDiagnostics
        CallDiagnostics.logEnded(call, CallManager.audioState.value)
        val details = call.details

        // Сброшенный входящий: предлагаем создать напоминание.
        // Именно «сбросил на бегу и забыл» — сценарий, ради которого
        // функция и нужна
        // Направление вызова доступно в Details только с API 29;
        // ниже определяется по состоянию вызова
        val wasRejected = details != null &&
            !CallDetailsCompat.isOutgoing(details, call) &&
            details.connectTimeMillis <= 0

        // Итоги разговора — для экрана заметки
        if (details != null) {
            val connectTime = details.connectTimeMillis
            val duration = if (connectTime > 0)
                (System.currentTimeMillis() - connectTime) / 1000 else 0L

            postCall.onCallCompleted(
                number = details.handle?.schemeSpecificPart,
                displayName = CallDetailsCompat.contactDisplayName(details),
                durationSec = duration,
                type = if (CallDetailsCompat.isOutgoing(details, call))
                    com.ogneliss.dialer.data.CallType.OUTGOING
                else com.ogneliss.dialer.data.CallType.INCOMING
            )
        }

        if (wasRejected) {
            val rejectedNumber = CallDetailsCompat.number(details)
            val rejectedName = CallDetailsCompat.contactDisplayName(details)

            /*
             * Заметка читается из базы, поэтому в сопрограмме.
             *
             * Область не привязана к службе: она живёт ровно до конца
             * вызова, а мы как раз в этот момент. Уведомление показывается
             * приложением, а не службой, и переживёт её остановку.
             *
             * Ошибку чтения глотаем сознательно: предложение перезвонить
             * важнее заметки, и без неё оно должно выйти всё равно.
             */
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val note = runCatching {
                    notesRepo.get(PhoneFormatter.toE164(rejectedNumber.orEmpty()))?.note
                }.getOrNull()

                com.ogneliss.dialer.background.CallbackSuggestion.show(
                    applicationContext, rejectedNumber, rejectedName, note
                )
            }
        }

        // Постобработка уходит в WorkManager: сервис живёт ровно до конца
        // вызова, и система вправе убить процесс сразу после
        com.ogneliss.dialer.background.CallEndWorker.enqueue(
            this, call.details?.handle?.schemeSpecificPart
        )
        CallManager.remove(call)

        /*
         * Отдельное уведомление больше не снимаем: его нет.
         *
         * Единственное уведомление вызова принадлежит переднему сервису
         * и уходит вместе с ним — ниже, когда закончился последний
         * вызов. Снять его отдельно значило бы остановить сервис
         * раньше времени.
         */
        if (CallManager.calls.value.isEmpty()) {
            CallManager.service = null
            CallForegroundService.stop(this)
        }
    }

    /*
     * УСТАРЕВШИЙ МЕТОД ОСТАЁТСЯ НАМЕРЕННО.
     *
     * ── Почему не переходим на новый ───────────────────────────────────────
     * Замена — onCallEndpointChanged — появилась в Android 14. Наш minSdk
     * 26: на телефонах старше система вызовет только этот метод,
     * и без него звук не переключится вовсе.
     *
     * Правильный переход означает две реализации сразу и выбор
     * по версии — работа с проверкой на устройствах обеих эпох.
     * Пока метод работает везде, менять его перед релизом незачем.
     *
     * @Deprecated здесь — не признак заброшенности, а требование
     * компилятора: переопределяя устаревшее, надо это признать.
     */
    /*
     * Метод объявлен устаревшим начиная с Android 14, но остаётся
     * единственным способом узнать маршрут звука на API 26–33.
     * Подавление точечное: убрать его можно будет вместе с подъёмом
     * minSdk до 34.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Replaced by onCallEndpointChanged in Android 14, still needed for API 26-33")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.updateAudio(audioState)
    }
}
